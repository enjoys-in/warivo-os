// Warivo Node — ESP32-C6 (Waveshare ESP32-C6-WROOM-1-N8) telemetry firmware
// -------------------------------------------------------------------------
// Board: https://robu.in/product/waveshare-esp32-c6-microcontroller-wifi-6-development-board-with-esp32-c6-wroom-1-n8/
//
// Streams scooter telemetry to the Warivo OS phone over Bluetooth LE (GATT
// notify) and accepts the phone's GPS coordinates back over a write
// characteristic. BLE lets the phone keep its own WiFi/cellular internet (maps,
// search) and its Bluetooth speaker at the same time.
//
// Requires: arduino-esp32 core v3.x + the "NimBLE-Arduino" library (v1.4.x).
//
// The ESP32-C6 has a real multi-channel ADC, so battery voltage and (optional)
// current are read directly. If a SMART BMS is present, prefer reading V/SoC/
// current/temp from it over UART — see readBmsBatteryTemp() and the guide.
//
// BLE service (UUID fff0):
//   fff1  telemetry  READ + NOTIFY  -> scooter state JSON, pushed 5x/sec
//   fff2  gps        WRITE          <- phone pushes {"lat":..,"lon":..,"spd":..,"ts":..}

#include <NimBLEDevice.h>
#include <OneWire.h>
#include <DallasTemperature.h>

// ----------------------- USER CONFIG -----------------------
// Battery model — CONFIRMED: lead-acid 60V pack (5 x 12V, 30Ah), no BMS.
const float BATT_FULL_V   = 65.0;   // rested-full (~13V/batt)
const float BATT_EMPTY_V  = 52.5;   // cutoff (~10.5V/batt)

// Voltage divider on PIN_VOLTAGE: ratio = (R1+R2)/R2. R1=270k, R2=10k -> 28.
const float DIVIDER_RATIO = 28.0;   // calibrate against a multimeter

// Wheel + energy model.
const float WHEEL_CIRC_M     = 1.47;   // 90/90-12 tyre: 12" rim + 2*81mm sidewall -> 0.467m dia, pi*d
const int   MAGNETS_PER_REV  = 1;
const float PACK_CAPACITY_WH = 1800.0; // 60V * 30Ah lead-acid (nominal; usable is less)
const float WH_PER_KM        = 25.0;   // consumption estimate for range

// ESP32-C6 pins (GPIO numbers).
const uint8_t PIN_VOLTAGE = 1;   // ADC1 — battery divider tap
const uint8_t PIN_CURRENT = 2;   // ADC1 — ACS758 (optional)
const uint8_t PIN_SPEED   = 10;  // hall/reed interrupt
const uint8_t PIN_TEMP    = 11;  // DS18B20 1-Wire (4.7k pull-up to 3.3V)
// DS18B20 index 0 = outside/ambient, index 1 = battery pack (optional).
// If a SMART BMS is present, battery temp/SoC/current come from it instead —
// see readBmsBatteryTemp() and the guide's BMS section.

// Optional current sensor (ACS758). Set USE_CURRENT true once wired.
const bool  USE_CURRENT  = false;
const float CUR_ZERO_MV  = 1650.0;  // sensor output at 0 A (after any divider)
const float CUR_MV_PER_A = 20.0;    // sensor sensitivity (mV/A)

// Obstacle distance sensor (analog IR, e.g. Sharp GP2Y0A21) + optional buzzer.
const uint8_t PIN_DIST   = 3;    // ADC1 — IR distance sensor analog out
const uint8_t PIN_BUZZER = 20;   // active buzzer (optional; node-side beep)
const bool  HAS_DIST_SENSOR = false;  // set true once the IR sensor is wired

#define SERVICE_UUID    "0000fff0-0000-1000-8000-00805f9b34fb"
#define CHAR_TELEMETRY  "0000fff1-0000-1000-8000-00805f9b34fb"
#define CHAR_GPS        "0000fff2-0000-1000-8000-00805f9b34fb"
#define CHAR_CONFIG     "0000fff3-0000-1000-8000-00805f9b34fb"
// -----------------------------------------------------------

OneWire oneWire(PIN_TEMP);
DallasTemperature tempSensors(&oneWire);

NimBLECharacteristic* telemetryChar;
bool deviceConnected = false;

volatile unsigned long pulseCount = 0;
volatile unsigned long lastPulseUs = 0;
double odometer_km = 0.0;

// Latest telemetry (recomputed every 200ms in loop()).
float g_v = 0, g_soc = 0, g_spd = 0, g_a = 0, g_w = 0, g_rng = 0;
float g_tout = -127, g_tbat = -127;   // outside + battery temp (°C); -127 = no sensor

// Last GPS fix from the phone.
float g_lat = 0, g_lon = 0, g_gspd = 0;
unsigned long g_gts = 0;

// Obstacle distance + proximity-beep config (beep is opt-in from the phone).
float g_dist = -1;            // cm; -1 = no sensor / out of range
bool  g_beepEnabled = false;  // toggled by the phone's settings
float g_beepCm = 40.0;        // beep when closer than this

void IRAM_ATTR onWheelPulse() {
  unsigned long now = micros();
  if (now - lastPulseUs > 3000) {   // 3ms debounce
    pulseCount++;
    lastPulseUs = now;
  }
}

float readPackVoltage() {
  uint32_t mv = 0;
  for (int i = 0; i < 16; i++) mv += analogReadMilliVolts(PIN_VOLTAGE);
  return (mv / 16.0 / 1000.0) * DIVIDER_RATIO;
}

float readAmps() {
  if (!USE_CURRENT) return 0.0;
  uint32_t mv = 0;
  for (int i = 0; i < 16; i++) mv += analogReadMilliVolts(PIN_CURRENT);
  return ((mv / 16.0) - CUR_ZERO_MV) / CUR_MV_PER_A;
}

// Battery temp from a SMART BMS (UART/CAN). Return NAN when no BMS link, so the
// DS18B20 index-1 reading is used instead. Wire up per the guide's BMS section.
float readBmsBatteryTemp() { return NAN; }

// Analog IR distance (Sharp GP2Y0A21, ~10-80cm). Calibrate per sensor model.
float readDistanceCm() {
  if (!HAS_DIST_SENSOR) return -1;
  uint32_t mv = 0;
  for (int i = 0; i < 8; i++) mv += analogReadMilliVolts(PIN_DIST);
  float volts = (mv / 8.0) / 1000.0;
  if (volts < 0.4) return -1;                 // too far / no reading
  float cm = 27.86 * pow(volts, -1.15);       // GP2Y0A21 empirical curve
  if (cm < 8 || cm > 80) return -1;           // outside reliable range
  return cm;
}

// Non-blocking proximity beep on the node buzzer, only when the phone enabled it.
void updateBeep() {
  static unsigned long lastToggle = 0;
  static bool on = false;
  bool active = g_beepEnabled && g_dist > 0 && g_dist <= g_beepCm;
  if (!active) {
    if (on) { digitalWrite(PIN_BUZZER, LOW); on = false; }
    return;
  }
  unsigned long interval = 100 + (unsigned long)((g_dist / g_beepCm) * 500);  // closer = faster
  unsigned long now = millis();
  if (now - lastToggle >= interval) {
    on = !on;
    digitalWrite(PIN_BUZZER, on ? HIGH : LOW);
    lastToggle = now;
  }
}

// Non-blocking DS18B20 read: request, then collect ~800ms later.
void updateTemps() {
  static unsigned long lastReq = 0;
  static bool pending = false;
  unsigned long now = millis();
  if (!pending && now - lastReq > 2000) {
    tempSensors.requestTemperatures();
    lastReq = now;
    pending = true;
  } else if (pending && now - lastReq > 800) {
    float t0 = tempSensors.getTempCByIndex(0);
    if (t0 > -100) g_tout = t0;
    float t1 = tempSensors.getTempCByIndex(1);
    if (t1 > -100) g_tbat = t1;
    float bms = readBmsBatteryTemp();
    if (!isnan(bms)) g_tbat = bms;
    pending = false;
  }
}

String telemetryJson() {
  String s = "{";
  s += "\"v\":"    + String(g_v, 1)   + ",";
  s += "\"soc\":"  + String(g_soc, 0) + ",";
  s += "\"spd\":"  + String(g_spd, 1) + ",";
  s += "\"odo\":"  + String(odometer_km, 2) + ",";
  s += "\"a\":"    + String(g_a, 1)   + ",";
  s += "\"w\":"    + String(g_w, 0)   + ",";
  s += "\"rng\":"  + String(g_rng, 1) + ",";
  s += "\"tout\":" + String(g_tout, 1) + ",";
  s += "\"tbat\":" + String(g_tbat, 1) + ",";
  s += "\"dist\":" + String(g_dist, 0) + ",";
  s += "\"up\":"   + String(millis());
  s += "}";
  return s;
}

// Minimal JSON number extractor (avoids pulling in ArduinoJson).
float jsonNum(const String& body, const String& key) {
  int k = body.indexOf("\"" + key + "\"");
  if (k < 0) return 0;
  int c = body.indexOf(':', k);
  if (c < 0) return 0;
  int i = c + 1;
  while (i < (int)body.length() && (body[i] == ' ')) i++;
  int j = i;
  while (j < (int)body.length() &&
         (isDigit(body[j]) || body[j] == '-' || body[j] == '.' || body[j] == '+' ||
          body[j] == 'e' || body[j] == 'E')) j++;
  return body.substring(i, j).toFloat();
}

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* s)    { deviceConnected = true; }
  void onDisconnect(NimBLEServer* s) { deviceConnected = false; NimBLEDevice::startAdvertising(); }
};

// Phone writes its GPS fix here: {"lat":..,"lon":..,"spd":..,"ts":..}
class GpsCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) {
    String body = String(c->getValue().c_str());
    g_lat  = jsonNum(body, "lat");
    g_lon  = jsonNum(body, "lon");
    g_gspd = jsonNum(body, "spd");
    g_gts  = (unsigned long)jsonNum(body, "ts");
  }
};

// Phone pushes proximity-beep settings here: {"beep":1,"beep_cm":40}
class ConfigCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) {
    String body = String(c->getValue().c_str());
    g_beepEnabled = jsonNum(body, "beep") > 0.5;
    float cm = jsonNum(body, "beep_cm");
    if (cm > 0) g_beepCm = cm;
  }
};

void setup() {
  Serial.begin(115200);
  pinMode(PIN_SPEED, INPUT_PULLUP);
  attachInterrupt(digitalPinToInterrupt(PIN_SPEED), onWheelPulse, FALLING);
  analogSetPinAttenuation(PIN_VOLTAGE, ADC_11db);
  analogSetPinAttenuation(PIN_CURRENT, ADC_11db);
  analogSetPinAttenuation(PIN_DIST, ADC_11db);
  pinMode(PIN_BUZZER, OUTPUT);
  digitalWrite(PIN_BUZZER, LOW);

  tempSensors.begin();
  tempSensors.setWaitForConversion(false);   // non-blocking conversions

  NimBLEDevice::init("Warivo-Node");
  NimBLEServer* server = NimBLEDevice::createServer();
  server->setCallbacks(new ServerCallbacks());

  NimBLEService* service = server->createService(SERVICE_UUID);
  telemetryChar = service->createCharacteristic(
      CHAR_TELEMETRY, NIMBLE_PROPERTY::READ | NIMBLE_PROPERTY::NOTIFY);
  NimBLECharacteristic* gpsChar = service->createCharacteristic(
      CHAR_GPS, NIMBLE_PROPERTY::WRITE);
  gpsChar->setCallbacks(new GpsCallbacks());
  NimBLECharacteristic* configChar = service->createCharacteristic(
      CHAR_CONFIG, NIMBLE_PROPERTY::WRITE);
  configChar->setCallbacks(new ConfigCallbacks());
  service->start();

  NimBLEAdvertising* adv = NimBLEDevice::getAdvertising();
  adv->addServiceUUID(SERVICE_UUID);
  adv->start();
  Serial.println("BLE advertising as Warivo-Node");
}

void loop() {
  updateTemps();
  updateBeep();

  static unsigned long lastCalc = 0;
  unsigned long now = millis();
  if (now - lastCalc < 200) return;
  float dt = (now - lastCalc) / 1000.0;
  lastCalc = now;

  noInterrupts();
  unsigned long pulses = pulseCount;
  pulseCount = 0;
  interrupts();

  float revs   = (float)pulses / MAGNETS_PER_REV;
  float dist_m = revs * WHEEL_CIRC_M;
  g_spd        = (dist_m / dt) * 3.6;      // km/h
  odometer_km += dist_m / 1000.0;

  g_v   = readPackVoltage();
  g_soc = (g_v - BATT_EMPTY_V) / (BATT_FULL_V - BATT_EMPTY_V) * 100.0;
  if (g_soc < 0) g_soc = 0;
  if (g_soc > 100) g_soc = 100;

  g_a   = readAmps();
  g_w   = g_v * g_a;
  float remWh = PACK_CAPACITY_WH * g_soc / 100.0;
  g_rng = remWh / WH_PER_KM;

  g_dist = readDistanceCm();

  if (deviceConnected) {
    String json = telemetryJson();
    telemetryChar->setValue((uint8_t*)json.c_str(), json.length());
    telemetryChar->notify();
  }
}

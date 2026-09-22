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
// Pack temperature is per-battery. The 60V pack is five 12V batteries in series and one
// of them always ages first, so "pack temp" is really five numbers. All five DS18B20s
// share the SINGLE 1-Wire bus on PIN_TEMP — three wires and one 4.7k pull-up for the lot
// — and are told apart by their unique 64-bit ROM addresses, pinned in TEMP_ADDR_BATT[]
// so that battery 3 stays battery 3.
//
// BLE service (UUID fff0):
//   fff1  telemetry  READ + NOTIFY  -> scooter state JSON, pushed 5x/sec
//   fff2  gps        WRITE          <- phone pushes {"lat":..,"lon":..,"spd":..,"ts":..}
//   fff3  config     WRITE          <- proximity-beep settings
//   fff4  lock       WRITE          <- immobiliser: {"lock":1} / {"lock":0}
//
// The immobiliser opens the controller's key-switch (KSI) line, in series with the
// physical key, and ONLY while the wheel is stopped — see docs/IMMOBILIZER.md. It never
// touches motor current, brakes or steering, and it refuses to engage above walking pace
// no matter what the phone asks for.

#include <NimBLEDevice.h>
#include <OneWire.h>
#include <DallasTemperature.h>
#include <Preferences.h>

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

// 3-speed gear selector (read-only display; the controller enforces the caps).
const bool    HAS_GEAR_SWITCH = false; // set true once the gear switch is tapped
const float   GEAR1_KMH       = 27.0;  // gear 1 (eco) cap
const float   GEAR2_KMH       = 36.0;  // gear 2 (normal) cap
// gear 3 = full speed, no cap.
const uint8_t PIN_GEAR_A      = 18;    // gear switch signal A (INPUT_PULLUP)
const uint8_t PIN_GEAR_B      = 19;    // gear switch signal B (INPUT_PULLUP)

// Immobiliser relay, in series with the controller's key-switch (KSI) line. Read
// docs/IMMOBILIZER.md before wiring: the failure mode you pick matters more than the
// feature. Default off, so an unwired node behaves exactly as before.
const bool    HAS_LOCK_RELAY   = false;  // set true once the relay is fitted
const uint8_t PIN_LOCK         = 7;      // relay drive (needs a 10k pull-down)
const bool    LOCK_ACTIVE_HIGH = true;   // match your transistor's polarity
// Engage only below this speed. The interlock lives HERE, in the node, not in the phone:
// the phone is the part most likely to be broken, out of range, or in a thief's pocket.
const float   LOCK_MAX_KMH     = 1.5;
// The wheel must have been stopped this long before engaging — one stationary sample is
// not a stopped scooter, it is a gap between magnet passes.
const unsigned long LOCK_STILL_MS = 2000;

// Trip + battery-life accounting.
const float   PACK_CAPACITY_AH = 30.0;   // 30Ah pack (for coulomb-counted cycles)
const float   SPD_MOVING_KMH   = 2.0;    // below this the scooter counts as stopped
const float   CHG_FULL_PCT     = 97.0;   // SoC that counts as "fully charged"
const float   CHG_LOW_PCT      = 90.0;   // must dip below this for a charge to count
const unsigned long REST_MS    = 20000;  // rest before a resting SoC is trustworthy
const unsigned long SAVE_MS    = 60000;  // how often odometer/cycles persist to flash

// ESP32-C6 pins (GPIO numbers).
const uint8_t PIN_VOLTAGE = 1;   // ADC1 — battery divider tap
const uint8_t PIN_CURRENT = 2;   // ADC1 — ACS758 (optional)
const uint8_t PIN_SPEED   = 10;  // hall/reed interrupt
const uint8_t PIN_TEMP    = 11;  // DS18B20 1-Wire (4.7k pull-up to 3.3V)

// ---- Per-battery temperature: one DS18B20 per 12V battery, all on one bus ----
// Six probes (five batteries + ambient) share PIN_TEMP: VCC, DATA and GND daisy-chained
// to every sensor, with a single 4.7k resistor from DATA up to 3.3V. 1-Wire is designed
// for exactly this, so the sensor count costs no extra GPIOs.
//
// The probes are THERMAL contacts, not electrical ones. Strap each one to its battery's
// case and leave the pack wiring alone: every DS18B20's GND is the ESP32's GND, which is
// battery-negative for the whole string, so a probe taken off an individual battery's
// terminals would short part of the series string through the signal wiring. Temperature
// does not need the terminals — that is the whole reason this is the easy measurement to
// take per battery, where per-battery VOLTAGE would need five isolated front ends.
const uint8_t BATT_COUNT = 5;

// Which probe is which. Pin each sensor's 64-bit ROM address here, in battery order.
// Left as zeros the node falls back to bus index order (ambient = 0, batteries = 1..5),
// which works — but that order is the bus search's address order, so one dead probe
// silently renumbers every probe after it and battery 4 starts being reported as 3.
//
// To fill them in: flash with SHOW_ONEWIRE_ADDRESSES true, open the serial monitor at
// 115200, warm one probe at a time with a finger to see which line moves, and paste that
// line's address in on the matching row.
const bool SHOW_ONEWIRE_ADDRESSES = true;              // print the bus scan at boot
uint8_t TEMP_ADDR_AMBIENT[8] = { 0,0,0,0,0,0,0,0 };    // outside/ambient probe
uint8_t TEMP_ADDR_BATT[BATT_COUNT][8] = {
  { 0,0,0,0,0,0,0,0 },   // battery 1   e.g. { 0x28,0xFF,0x1A,0x63,0x91,0x16,0x03,0x7C }
  { 0,0,0,0,0,0,0,0 },   // battery 2
  { 0,0,0,0,0,0,0,0 },   // battery 3
  { 0,0,0,0,0,0,0,0 },   // battery 4
  { 0,0,0,0,0,0,0,0 },   // battery 5
};

// Conversion timing. At 12-bit (0.0625C) a conversion takes 750ms, and one broadcast
// converts every probe in parallel — six sensors cost the same 750ms as one.
const unsigned long TEMP_PERIOD_MS  = 2000;  // how often a conversion is started
const unsigned long TEMP_CONVERT_MS = 800;   // wait before reading (750ms + margin)
const uint8_t TEMP_MISS_LIMIT = 3;           // failed reads before a probe reports "gone"
// There is deliberately no warning threshold here. The node has nothing to do about a hot
// battery — no fan, no contactor — so it reports all five temperatures and lets the app
// decide what is alarming (PACK_TEMP_HOT_C / PACK_TEMP_SPREAD_C in DashboardPanel.kt).
// One threshold, in the place that draws the warning.

// If a SMART BMS is present, pack temp/SoC/current come from it instead —
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
#define CHAR_LOCK       "0000fff4-0000-1000-8000-00805f9b34fb"

// One BLE notification carries MTU-3 bytes and there is NO reassembly, so the whole
// telemetry frame has to fit in one. An overlong frame does not lose its last field — it
// arrives truncated mid-JSON and fails to parse entirely, which looks exactly like dead
// firmware.
//
// We ask for the BLE maximum. At the old 247 the frame had ~9 bytes spare once the five
// battery temperatures were added, and "spare" was doing a lot of work: a noise spike on
// the wheel pin prints a four-digit speed, and after 49 days `up` reaches ten digits.
// Budgeting single bytes against sensor noise is a losing game, so take the headroom.
// Negotiation settles on the smaller of the two sides' preferences, so a phone that
// cannot manage this simply lands lower and the check below reports it.
const uint16_t PREFERRED_MTU = 517;
const unsigned MTU_FLOOR     = 247;   // what we assume until a peer negotiates otherwise
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
float g_tout = -127, g_tbat = -127;   // outside + pack temp (°C); -127 = no sensor
float g_tb[BATT_COUNT];               // per-battery temps (°C); -127 = no probe
uint8_t g_thot = 0;                   // hottest battery, 1..BATT_COUNT (0 = no probes)
uint8_t tempMiss[1 + BATT_COUNT];     // consecutive failed reads; slot 0 = ambient

bool     g_locked  = false; // actual relay state: true = controller disabled
bool     g_lockReq = false; // what was asked for; differs while waiting to stop
uint8_t  g_gear    = 0;    // 1/2/3 selected gear (0 = no switch / unknown)
float    g_avg     = 0;    // trip average speed (moving) km/h
float    g_whkm    = 0;    // measured/estimated consumption Wh/km
float    g_mileage = 0;    // projected km on a full charge (this ride's efficiency)
uint32_t g_cycles  = 0;    // battery charge cycles (equivalent full charges)

// Trip + charge accounting (trip_* reset each power-up; odometer/cycles persist).
double   trip_km     = 0;  // distance this trip
double   moving_s    = 0;  // seconds actually moving (for the average)
double   energy_wh   = 0;  // Wh drawn this trip (only when a current sensor is fitted)
double   odoAtCharge = 0;  // odometer at the last full charge (mileage reference)
float    socAtCharge = 0;  // SoC at that reference
float    cycleAccum  = 0;  // fractional progress toward the next whole charge cycle

Preferences prefs;

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

// The 3-speed selector, read-only. Returns 1/2/3, or 0 when no switch is wired.
// Decode table is provisional — confirm each position with a multimeter (see audit.md).
uint8_t readGear() {
  if (!HAS_GEAR_SWITCH) return 0;
  bool a = digitalRead(PIN_GEAR_A);   // HIGH = open (pull-up), LOW = grounded
  bool b = digitalRead(PIN_GEAR_B);
  if (!a && b) return 1;              // gear 1 (eco, GEAR1_KMH cap)
  if (a && b)  return 2;              // gear 2 (normal, GEAR2_KMH cap)
  if (a && !b) return 3;              // gear 3 (full)
  return 0;
}

// Speed cap for a gear: GEAR1/GEAR2 km/h, 0 = full (no cap), -1 = unknown.
float gearLimitKmh(uint8_t g) {
  if (g == 1) return GEAR1_KMH;
  if (g == 2) return GEAR2_KMH;
  if (g == 3) return 0;
  return -1;
}

// Persist the values that must survive a power cycle.
void saveState() {
  prefs.putDouble("odo", odometer_km);
  prefs.putUInt("cyc", g_cycles);
  prefs.putFloat("cycAcc", cycleAccum);
}

// Count battery charge cycles as equivalent full charges. With a current sensor
// this is a coulomb count of charge flowing into the pack; without one it detects
// a rested recovery to full from a lower floor and counts it (partial charges add
// up fractionally). Also resets the mileage reference at each full charge.
void updateChargeCycles(float dt) {
  static float socFloor = 100;
  static unsigned long restStart = 0;
  static bool refInit = false;
  unsigned long now = millis();

  if (!refInit) { odoAtCharge = odometer_km; socAtCharge = g_soc; socFloor = g_soc; refInit = true; }

  if (USE_CURRENT) {
    if (g_a < -0.2)                                   // negative current = charging
      cycleAccum += (-g_a) * dt / 3600.0 / PACK_CAPACITY_AH;
    while (cycleAccum >= 1.0) { cycleAccum -= 1.0; g_cycles++; saveState(); }
    if (g_soc >= CHG_FULL_PCT) { odoAtCharge = odometer_km; socAtCharge = g_soc; }
    return;
  }

  bool atRest = g_spd < SPD_MOVING_KMH;
  if (atRest) { if (restStart == 0) restStart = now; }
  else restStart = 0;
  bool rested = atRest && restStart && (now - restStart > REST_MS);

  if (g_soc < socFloor) socFloor = g_soc;             // remember how far it discharged

  if (rested && g_soc >= CHG_FULL_PCT && socFloor <= CHG_LOW_PCT) {
    cycleAccum += (100.0 - socFloor) / 100.0;          // partial charges count fractionally
    while (cycleAccum >= 1.0) { cycleAccum -= 1.0; g_cycles++; }
    socFloor = g_soc;
    odoAtCharge = odometer_km; socAtCharge = g_soc;    // new mileage reference
    saveState();
  }
}

// Drive the relay. Kept as the only place that touches the pin, so there is exactly one
// line in this firmware that can immobilise the scooter.
void applyLock(bool locked) {
  if (!HAS_LOCK_RELAY) return;
  digitalWrite(PIN_LOCK, (locked == LOCK_ACTIVE_HIGH) ? HIGH : LOW);
  g_locked = locked;
}

// The safety interlock.
//
// Releasing is allowed at any speed and happens immediately — it is never unsafe to give a
// rider their scooter back. Engaging waits until the wheel has been stopped for
// LOCK_STILL_MS. Cutting the controller at speed means no throttle and, on many
// controllers, no regen braking; that is how someone comes off, so the node simply will
// not do it, whatever the phone or the server asks for.
void updateLock() {
  if (!HAS_LOCK_RELAY) return;
  static unsigned long stillSince = 0;
  unsigned long now = millis();

  if (!g_lockReq) {                       // release: immediate, unconditional
    if (g_locked) applyLock(false);
    stillSince = 0;
    return;
  }
  if (g_locked) return;                   // already engaged, nothing to do

  // A one-magnet wheel sensor cannot tell "stopped" from "creeping": at 1.5 km/h a
  // revolution takes ~3.5 s, so g_spd reads 0 between magnet passes. LOCK_STILL_MS only
  // narrows that window, it does not close it — which is why LOCK_MAX_KMH is set at
  // walking pace rather than trying to detect a true standstill. Engaging at 1 km/h is
  // safe; engaging at 20 km/h is what this prevents.
  if (g_spd > LOCK_MAX_KMH) {             // still rolling: keep waiting
    stillSince = 0;
    return;
  }
  if (stillSince == 0) stillSince = now;
  if (now - stillSince >= LOCK_STILL_MS) {
    applyLock(true);
    prefs.putBool("lock", true);          // survives a power cycle, or it is worthless
  }
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

// True once a ROM address has actually been filled in (all-zero is not a valid address).
bool addrIsSet(const uint8_t* a) {
  for (uint8_t i = 0; i < 8; i++) if (a[i]) return true;
  return false;
}

// Read one probe. Prefers its pinned ROM address; falls back to bus index while the
// address table is still zeros, so an unconfigured node behaves as it did before.
float readTempAt(const uint8_t* addr, uint8_t fallbackIndex) {
  if (addrIsSet(addr)) return tempSensors.getTempC(addr);
  return tempSensors.getTempCByIndex(fallbackIndex);
}

// Store a reading, or retire the probe after TEMP_MISS_LIMIT failures.
//
// A single failed read is a bus collision, not a missing sensor, so one miss must not
// blank the display. But holding the last good value forever is worse than showing
// nothing: a probe whose wire has chafed through would sit on screen at a comfortable
// 32C for the rest of the scooter's life, on the one number a rider would check to find
// out whether a battery is cooking.
void applyTemp(float* dest, float reading, uint8_t slot) {
  if (reading > -100) { *dest = reading; tempMiss[slot] = 0; return; }
  if (tempMiss[slot] < TEMP_MISS_LIMIT) tempMiss[slot]++;
  if (tempMiss[slot] >= TEMP_MISS_LIMIT) *dest = -127;
}

// Print every DS18B20 on the bus, paste-ready for TEMP_ADDR_BATT[]. Boot-time only, so
// the blocking 750ms conversion here costs nothing at runtime.
void scanOneWire() {
  uint8_t n = tempSensors.getDeviceCount();
  Serial.printf("\n1-Wire scan on GPIO%u: %u DS18B20(s)\n", PIN_TEMP, n);
  if (n == 0) {
    Serial.println("  none found - check the 4.7k pull-up to 3.3V and the shared GND");
    return;
  }
  tempSensors.setWaitForConversion(true);
  tempSensors.requestTemperatures();
  tempSensors.setWaitForConversion(false);
  for (uint8_t i = 0; i < n; i++) {
    DeviceAddress a;
    if (!tempSensors.getAddress(a, i)) continue;
    Serial.printf("  [%u] ", i);
    Serial.print(tempSensors.getTempC(a), 2);
    Serial.print(" C  { ");
    for (uint8_t b = 0; b < 8; b++) { if (b) Serial.print(','); Serial.printf("0x%02X", a[b]); }
    Serial.println(" }");
  }
  Serial.println("  Warm one probe with a finger, see which line moves, and paste that");
  Serial.println("  address into TEMP_ADDR_BATT[] on the matching battery's row.");
}

// Non-blocking DS18B20 read across the whole bus: broadcast one conversion, wait, then
// collect the probes ONE PER PASS.
//
// Reading six sensors back to back is ~55ms of blocking 1-Wire transactions, which lands
// as a visible stutter in a 5x/sec notify stream and in the speedo it drives. One probe
// per pass costs ~9ms and the whole bus is still collected inside a single loop tick.
enum TempPhase { TEMP_IDLE, TEMP_CONVERTING, TEMP_READING };

void updateTemps() {
  static unsigned long lastReq = 0;
  static TempPhase phase = TEMP_IDLE;
  static uint8_t slot = 0;
  unsigned long now = millis();

  if (phase == TEMP_IDLE) {
    if (now - lastReq < TEMP_PERIOD_MS) return;
    tempSensors.requestTemperatures();   // broadcast: every probe converts in parallel
    lastReq = now;
    phase = TEMP_CONVERTING;
    return;
  }

  if (phase == TEMP_CONVERTING) {
    if (now - lastReq < TEMP_CONVERT_MS) return;
    slot = 0;
    phase = TEMP_READING;
    return;
  }

  if (slot == 0) {
    applyTemp(&g_tout, readTempAt(TEMP_ADDR_AMBIENT, 0), 0);
  } else {
    applyTemp(&g_tb[slot - 1], readTempAt(TEMP_ADDR_BATT[slot - 1], slot), slot);
  }
  if (++slot <= BATT_COUNT) return;     // more probes to collect on later passes
  phase = TEMP_IDLE;

  // Pack temp is the HOTTEST battery, not the mean. The mean of five is exactly the
  // number that hides the one battery going thermal, which is the only reason to measure
  // per battery in the first place.
  g_tbat = -127;
  g_thot = 0;
  for (uint8_t i = 0; i < BATT_COUNT; i++) {
    if (g_tb[i] > -100 && (g_thot == 0 || g_tb[i] > g_tbat)) { g_tbat = g_tb[i]; g_thot = i + 1; }
  }
  // A smart BMS measures inside the pack and wins for the pack figure. It says nothing
  // about which of our five probes is hottest, so g_thot keeps indexing the probes.
  float bms = readBmsBatteryTemp();
  if (!isnan(bms)) g_tbat = bms;
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
  // Temperatures go out as whole degrees. A DS18B20 is +/-0.5C, and every consumer
  // renders these with toInt() anyway, so the decimal was two bytes of invented
  // precision per field on a frame with none to spare.
  s += "\"tout\":" + String(g_tout, 0) + ",";
  s += "\"tbat\":" + String(g_tbat, 0) + ",";
  // Per-battery temps, in battery order as fixed by TEMP_ADDR_BATT[]. Sent only when at
  // least one probe answered — see TELEMETRY_MAX_BYTES. Five probes spelled out as
  // "battery1_temp" .. "battery5_temp" would cost 115 bytes and push the frame past the
  // MTU; as an array of whole degrees they cost 26. The long, readable names live in the
  // fleet uplink (docs/FLEET.md), which is HTTP and has the room.
  if (g_thot > 0) {
    s += "\"tb\":[";
    for (uint8_t i = 0; i < BATT_COUNT; i++) {
      if (i) s += ",";
      s += String(g_tb[i], 0);
    }
    s += "],";
  }
  s += "\"dist\":" + String(g_dist, 0) + ",";
  s += "\"gear\":" + String(g_gear) + ",";
  s += "\"glim\":" + String(gearLimitKmh(g_gear), 0) + ",";
  s += "\"avg\":"  + String(g_avg, 1) + ",";
  s += "\"whkm\":" + String(g_whkm, 0) + ",";
  s += "\"mil\":"  + String(g_mileage, 1) + ",";
  s += "\"cyc\":"  + String(g_cycles) + ",";
  // 'lock' is the real relay state; 'lockq' is an engage still waiting for the scooter to
  // stop. They differ while it is rolling, and a UI that says "locked" then is lying.
  s += "\"lock\":"  + String(g_locked ? 1 : 0) + ",";
  s += "\"lockq\":" + String((g_lockReq && !g_locked) ? 1 : 0) + ",";
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

// What the peer actually negotiated. Tracked rather than assumed: the frame size check
// below is only worth anything if it tests the real payload limit.
volatile uint16_t g_mtu = MTU_FLOOR;

class ServerCallbacks : public NimBLEServerCallbacks {
  void onConnect(NimBLEServer* s)    { deviceConnected = true; }
  void onDisconnect(NimBLEServer* s) {
    deviceConnected = false;
    g_mtu = MTU_FLOOR;                // the next peer may not negotiate as high
    NimBLEDevice::startAdvertising();
  }
  void onMTUChange(uint16_t MTU, ble_gap_conn_desc* desc) {
    g_mtu = MTU;
    Serial.printf("MTU negotiated: %u (%u bytes per notification)\n", MTU, MTU - 3);
  }
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

// Phone writes the immobiliser request here: {"lock":1} or {"lock":0}.
// Separate from fff3 on purpose: writing an actuator is a different kind of act from
// changing a beep threshold, and the two should not share a code path.
class LockCallbacks : public NimBLECharacteristicCallbacks {
  void onWrite(NimBLECharacteristic* c) {
    if (!HAS_LOCK_RELAY) {
      // No relay fitted: refuse rather than queue. Accepting the request would leave
      // 'lockq' asserted forever and the app claiming a lock is pending on hardware that
      // does not exist.
      Serial.println("lock request ignored: HAS_LOCK_RELAY is false");
      return;
    }
    String body = String(c->getValue().c_str());
    g_lockReq = jsonNum(body, "lock") > 0.5;
    if (!g_lockReq) {
      applyLock(false);                   // release takes effect now, not next loop
      prefs.putBool("lock", false);
    }
    Serial.printf("lock request: %d\n", g_lockReq ? 1 : 0);
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

  if (HAS_GEAR_SWITCH) {
    pinMode(PIN_GEAR_A, INPUT_PULLUP);
    pinMode(PIN_GEAR_B, INPUT_PULLUP);
  }

  if (HAS_LOCK_RELAY) {
    // Drive the safe state before switching to OUTPUT: the pin floats during boot, and on
    // a normally-closed relay a few hundred milliseconds of stray coil current shows up as
    // a scooter that intermittently refuses to start.
    digitalWrite(PIN_LOCK, LOCK_ACTIVE_HIGH ? LOW : HIGH);
    pinMode(PIN_LOCK, OUTPUT);
  }

  prefs.begin("warivo", false);
  odometer_km = prefs.getDouble("odo", 0.0);
  g_cycles    = prefs.getUInt("cyc", 0);
  cycleAccum  = prefs.getFloat("cycAcc", 0.0);
  // Re-apply the lock across a power cycle, or pulling the fuse would release it.
  g_lockReq   = HAS_LOCK_RELAY && prefs.getBool("lock", false);
  applyLock(g_lockReq);

  for (uint8_t i = 0; i < BATT_COUNT; i++) g_tb[i] = -127;
  tempSensors.begin();
  tempSensors.setWaitForConversion(false);   // non-blocking conversions
  tempSensors.setResolution(12);             // 0.0625C / 750ms, what TEMP_CONVERT_MS assumes
  if (SHOW_ONEWIRE_ADDRESSES) scanOneWire();

  NimBLEDevice::init("Warivo-Node");
  NimBLEDevice::setMTU(PREFERRED_MTU);   // a preference; the peer still negotiates down
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
  NimBLECharacteristic* lockChar = service->createCharacteristic(
      CHAR_LOCK, NIMBLE_PROPERTY::WRITE);
  lockChar->setCallbacks(new LockCallbacks());
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
  trip_km     += dist_m / 1000.0;
  if (g_spd > SPD_MOVING_KMH) moving_s += dt;
  g_avg = (moving_s > 0) ? (float)(trip_km / (moving_s / 3600.0)) : 0;

  g_gear = readGear();

  g_v   = readPackVoltage();
  g_soc = (g_v - BATT_EMPTY_V) / (BATT_FULL_V - BATT_EMPTY_V) * 100.0;
  if (g_soc < 0) g_soc = 0;
  if (g_soc > 100) g_soc = 100;

  g_a   = readAmps();
  g_w   = g_v * g_a;
  if (USE_CURRENT) energy_wh += g_w * dt / 3600.0;

  updateChargeCycles(dt);

  // Mileage: consumption (Wh/km) → km on a full charge. Prefer a measured current
  // integral; otherwise infer from the SoC used since the last full charge.
  if (USE_CURRENT && trip_km > 0.2) {
    g_whkm = (float)(energy_wh / trip_km);
  } else {
    double distSince = odometer_km - odoAtCharge;
    float  socUsed   = socAtCharge - g_soc;
    if (distSince > 0.2 && socUsed > 1.0)
      g_whkm = PACK_CAPACITY_WH * (socUsed / distSince) / 100.0;
  }
  g_mileage = (g_whkm > 0) ? (PACK_CAPACITY_WH / g_whkm) : 0;

  float remWh = PACK_CAPACITY_WH * g_soc / 100.0;
  g_rng = remWh / (g_whkm > 0 ? g_whkm : WH_PER_KM);

  g_dist = readDistanceCm();

  updateLock();

  static unsigned long lastSave = 0;
  static double odoSaved = 0;
  if (now - lastSave > SAVE_MS) {
    if (fabs(odometer_km - odoSaved) > 0.05) { saveState(); odoSaved = odometer_km; }
    lastSave = now;
  }

  if (deviceConnected) {
    String json = telemetryJson();
    // Say so once, rather than leaving the next person to debug a phone that has silently
    // stopped parsing frames after someone added a field.
    static bool warnedSize = false;
    unsigned budget = (g_mtu > 3) ? (unsigned)(g_mtu - 3) : 20;
    if (!warnedSize && json.length() > budget) {
      warnedSize = true;
      Serial.printf("telemetry frame is %u bytes but only %u fit one notification: it will "
                    "arrive truncated and unparseable\n", json.length(), budget);
    }
    telemetryChar->setValue((uint8_t*)json.c_str(), json.length());
    telemetryChar->notify();
  }
}

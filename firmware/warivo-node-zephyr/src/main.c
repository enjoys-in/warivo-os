/*
 * Warivo Node — ESP32-C6 telemetry firmware, Zephyr edition.
 * ---------------------------------------------------------------------------
 * A drop-in alternative to ../warivo-node/warivo-node.ino, built on the Zephyr
 * RTOS and its own Bluetooth host instead of the Arduino core + NimBLE. The BLE
 * contract is byte-for-byte the same, so the Warivo OS phone app pairs with
 * either firmware without a change:
 *
 *   BLE service fff0
 *     fff1  telemetry  READ + NOTIFY  -> scooter-state JSON, pushed ~5x/sec
 *     fff2  gps        WRITE          <- phone pushes {"lat":..,"lon":..,"spd":..,"ts":..}
 *     fff3  config     WRITE          <- {"beep":1,"beep_cm":40}
 *     fff4  lock       WRITE          <- immobiliser {"lock":1} / {"lock":0}
 *
 * Why a second firmware: Zephyr gives a real preemptive scheduler, a maintained
 * BLE stack, devicetree/Kconfig, a watchdog and OTA — the "proper OS" the audit
 * called for. This file wires the two sensors the reference build always has
 * (battery voltage via ADC, wheel speed via a GPIO interrupt) and the immobiliser
 * relay with the same node-side interlock; per-battery temperature, current and
 * the distance sensor are left as hooks, exactly as the Arduino node gates them
 * behind HAS_* flags, so the emitted frame matches an unwired reference node.
 *
 * Build & flash (host with the Zephyr SDK + west):
 *   west build -b esp32c6_devkitc firmware/warivo-node-zephyr
 *   west flash
 * The app binary is well under 1 MB; the 8 MB (N8) flash leaves room for OTA.
 */

#include <zephyr/kernel.h>
#include <zephyr/bluetooth/bluetooth.h>
#include <zephyr/bluetooth/conn.h>
#include <zephyr/bluetooth/gatt.h>
#include <zephyr/bluetooth/uuid.h>
#include <zephyr/drivers/adc.h>
#include <zephyr/drivers/gpio.h>
#include <zephyr/sys/printk.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>

/* ----------------------- USER CONFIG (mirrors the .ino) ----------------------- */
#define BATT_FULL_V      65.0f   /* rested-full 60V lead-acid pack (5 x 12V) */
#define BATT_EMPTY_V     52.5f   /* cutoff */
#define DIVIDER_RATIO    28.0f   /* (R1+R2)/R2 = (270k+10k)/10k; calibrate vs a meter */
#define WHEEL_CIRC_M     1.47f   /* 90/90-12 tyre circumference */
#define MAGNETS_PER_REV  1
#define PACK_CAPACITY_WH 1800.0f /* 60V * 30Ah nominal */
#define WH_PER_KM        25.0f   /* consumption estimate for range */

/* Immobiliser: engage only at/below walking pace, and only after the wheel has been
 * stopped this long. The interlock lives HERE, in the node — the phone is the part most
 * likely to be broken, out of range, or in a thief's pocket. See docs/IMMOBILIZER.md. */
#define LOCK_ACTIVE_HIGH 1
#define LOCK_MAX_KMH     1.5f
#define LOCK_STILL_MS    2000
#define WHEEL_DEBOUNCE_US 3000

#define TELEMETRY_PERIOD_MS 200  /* ~5 Hz */

/* --------------------------- BLE UUIDs (16-bit) --------------------------- */
#define WARIVO_SVC   BT_UUID_DECLARE_16(0xfff0)
#define WARIVO_TELE  BT_UUID_DECLARE_16(0xfff1)
#define WARIVO_GPS   BT_UUID_DECLARE_16(0xfff2)
#define WARIVO_CFG   BT_UUID_DECLARE_16(0xfff3)
#define WARIVO_LOCK  BT_UUID_DECLARE_16(0xfff4)

/* --------------------------- device resources --------------------------- */
#define USER_NODE DT_PATH(zephyr_user)

#if DT_NODE_HAS_PROP(USER_NODE, io_channels)
static const struct adc_dt_spec vbat_adc = ADC_DT_SPEC_GET(USER_NODE);
#define HAS_VBAT 1
#endif

#if DT_NODE_HAS_PROP(USER_NODE, wheel_gpios)
static const struct gpio_dt_spec wheel_gpio = GPIO_DT_SPEC_GET(USER_NODE, wheel_gpios);
static struct gpio_callback wheel_cb;
#define HAS_WHEEL 1
#endif

#if DT_NODE_HAS_PROP(USER_NODE, lock_gpios)
static const struct gpio_dt_spec lock_gpio = GPIO_DT_SPEC_GET(USER_NODE, lock_gpios);
#define HAS_LOCK 1
#endif

/* ------------------------------ node state ------------------------------ */
static volatile uint32_t pulse_count;
static volatile int64_t last_pulse_us;

static double odometer_km;
static float g_v, g_soc, g_spd, g_a, g_w, g_rng;
static float g_tout = -127, g_tbat = -127;   /* -127 = no probe (temps not wired here) */
static float g_dist = -1;                     /* -1 = no distance sensor */
static uint8_t g_gear;                        /* 0 = no gear switch */
static float g_avg, g_whkm, g_mileage;
static uint32_t g_cycles;

static bool g_locked;      /* real relay state */
static bool g_lockReq;     /* requested; differs from g_locked while still rolling */

/* GPS the phone wrote back, and the beep config it pushed. */
static float g_lat, g_lon, g_gspd;
static uint32_t g_gts;
static bool g_beepEnabled;
static float g_beepCm = 40.0f;

static bool notify_enabled;

/* The telemetry frame, rebuilt each tick; also served on a fff1 READ. */
static char frame[251];
static uint16_t frame_len;

/* Thread waits here until main() has brought up the sensors and BLE. */
static K_SEM_DEFINE(ready_sem, 0, 1);

/* ------------------------------ helpers ------------------------------ */

/* Minimal JSON number extractor, matching jsonNum() in the .ino (no JSON library). */
static float json_num(const char *body, const char *key)
{
	char pat[24];
	(void)snprintf(pat, sizeof(pat), "\"%s\"", key);
	const char *k = strstr(body, pat);
	if (!k) {
		return 0.0f;
	}
	const char *colon = strchr(k, ':');
	if (!colon) {
		return 0.0f;
	}
	return strtof(colon + 1, NULL);
}

/* Speed cap for a gear: GEAR km/h, 0 = full (no cap), -1 = unknown. */
static float gear_limit_kmh(uint8_t g)
{
	if (g == 1) {
		return 27.0f;
	}
	if (g == 2) {
		return 36.0f;
	}
	if (g == 3) {
		return 0.0f;
	}
	return -1.0f;
}

/* -------------------------- wheel speed (GPIO IRQ) -------------------------- */
#ifdef HAS_WHEEL
static void wheel_isr(const struct device *port, struct gpio_callback *cb, uint32_t pins)
{
	ARG_UNUSED(port);
	ARG_UNUSED(cb);
	ARG_UNUSED(pins);
	int64_t now = k_ticks_to_us_floor64(k_uptime_ticks());
	if (now - last_pulse_us > WHEEL_DEBOUNCE_US) {   /* debounce a bouncing reed */
		pulse_count++;
		last_pulse_us = now;
	}
}
#endif

/* --------------------------- battery voltage (ADC) --------------------------- */
static float read_pack_voltage(void)
{
#ifdef HAS_VBAT
	int32_t acc = 0;
	int16_t sample = 0;
	struct adc_sequence seq = {
		.buffer = &sample,
		.buffer_size = sizeof(sample),
	};
	(void)adc_sequence_init_dt(&vbat_adc, &seq);

	int used = 0;
	for (int i = 0; i < 16; i++) {
		if (adc_read_dt(&vbat_adc, &seq) != 0) {
			continue;
		}
		int32_t mv = sample;
		if (adc_raw_to_millivolts_dt(&vbat_adc, &mv) != 0) {
			continue;
		}
		acc += mv;
		used++;
	}
	if (used == 0) {
		return 0.0f;
	}
	return ((float)acc / used / 1000.0f) * DIVIDER_RATIO;
#else
	/* No ADC on this board build: model a healthy rested pack so the app still runs. */
	return BATT_FULL_V;
#endif
}

/* --------------------------- immobiliser relay --------------------------- */
static void apply_lock(bool locked)
{
#ifdef HAS_LOCK
	gpio_pin_set_dt(&lock_gpio, (locked == (bool)LOCK_ACTIVE_HIGH) ? 1 : 0);
#endif
	g_locked = locked;
}

/* Release is immediate and unconditional; engage waits for a real standstill. Cutting the
 * controller at speed is how someone comes off, so the node refuses it whatever is asked. */
static void update_lock(void)
{
#ifdef HAS_LOCK
	static int64_t still_since;
	int64_t now = k_uptime_get();

	if (!g_lockReq) {
		if (g_locked) {
			apply_lock(false);
		}
		still_since = 0;
		return;
	}
	if (g_locked) {
		return;
	}
	if (g_spd > LOCK_MAX_KMH) {   /* still rolling: keep waiting */
		still_since = 0;
		return;
	}
	if (still_since == 0) {
		still_since = now;
	}
	if (now - still_since >= LOCK_STILL_MS) {
		apply_lock(true);
	}
#endif
}

/* ------------------------------ sensor tick ------------------------------ */
static void read_sensors(float dt_s)
{
	/* Speed + odometer from the wheel pulse count since the last tick. */
	uint32_t pulses = pulse_count;
	static uint32_t last_pulses;
	uint32_t d = pulses - last_pulses;
	last_pulses = pulses;

	float dist_m = (float)d * WHEEL_CIRC_M / (float)MAGNETS_PER_REV;
	odometer_km += dist_m / 1000.0;
	g_spd = (dt_s > 0.0f) ? (dist_m / dt_s) * 3.6f : 0.0f;

	/* Battery voltage -> state of charge -> range. */
	g_v = read_pack_voltage();
	float soc = (g_v - BATT_EMPTY_V) / (BATT_FULL_V - BATT_EMPTY_V) * 100.0f;
	g_soc = soc < 0.0f ? 0.0f : (soc > 100.0f ? 100.0f : soc);
	g_rng = g_soc / 100.0f * PACK_CAPACITY_WH / WH_PER_KM;
}

/* Build the telemetry JSON — identical keys/precision to telemetryJson() in the .ino.
 * Per-battery temps (the "tb":[..] array) are inserted before "dist" once probes are
 * wired; unwired, the reference node omits them too. */
static void build_frame(void)
{
	int n = snprintf(frame, sizeof(frame),
		"{\"v\":%.1f,\"soc\":%.0f,\"spd\":%.1f,\"odo\":%.2f,"
		"\"a\":%.1f,\"w\":%.0f,\"rng\":%.1f,"
		"\"tout\":%.0f,\"tbat\":%.0f,"
		"\"dist\":%.0f,\"gear\":%d,\"glim\":%.0f,"
		"\"avg\":%.1f,\"whkm\":%.0f,\"mil\":%.1f,\"cyc\":%u,"
		"\"lock\":%d,\"lockq\":%d,\"up\":%lld}",
		(double)g_v, (double)g_soc, (double)g_spd, odometer_km,
		(double)g_a, (double)g_w, (double)g_rng,
		(double)g_tout, (double)g_tbat,
		(double)g_dist, g_gear, (double)gear_limit_kmh(g_gear),
		(double)g_avg, (double)g_whkm, (double)g_mileage, g_cycles,
		g_locked ? 1 : 0, (g_lockReq && !g_locked) ? 1 : 0,
		(long long)k_uptime_get());

	if (n < 0) {
		n = 0;
	} else if (n >= (int)sizeof(frame)) {
		n = sizeof(frame) - 1;
	}
	frame_len = (uint16_t)n;
}

/* ------------------------------ GATT server ------------------------------ */

static ssize_t read_tele(struct bt_conn *conn, const struct bt_gatt_attr *attr,
			 void *buf, uint16_t len, uint16_t offset)
{
	return bt_gatt_attr_read(conn, attr, buf, len, offset, frame, frame_len);
}

static void tele_ccc_changed(const struct bt_gatt_attr *attr, uint16_t value)
{
	notify_enabled = (value == BT_GATT_CCC_NOTIFY);
}

static ssize_t write_gps(struct bt_conn *conn, const struct bt_gatt_attr *attr,
			 const void *buf, uint16_t len, uint16_t offset, uint8_t flags)
{
	char tmp[128];
	uint16_t n = len < sizeof(tmp) - 1 ? len : sizeof(tmp) - 1;
	memcpy(tmp, buf, n);
	tmp[n] = '\0';
	g_lat = json_num(tmp, "lat");
	g_lon = json_num(tmp, "lon");
	g_gspd = json_num(tmp, "spd");
	g_gts = (uint32_t)json_num(tmp, "ts");
	return len;
}

static ssize_t write_cfg(struct bt_conn *conn, const struct bt_gatt_attr *attr,
			 const void *buf, uint16_t len, uint16_t offset, uint8_t flags)
{
	char tmp[64];
	uint16_t n = len < sizeof(tmp) - 1 ? len : sizeof(tmp) - 1;
	memcpy(tmp, buf, n);
	tmp[n] = '\0';
	g_beepEnabled = json_num(tmp, "beep") > 0.5f;
	float cm = json_num(tmp, "beep_cm");
	if (cm > 5.0f) {
		g_beepCm = cm;
	}
	return len;
}

static ssize_t write_lock(struct bt_conn *conn, const struct bt_gatt_attr *attr,
			  const void *buf, uint16_t len, uint16_t offset, uint8_t flags)
{
	char tmp[32];
	uint16_t n = len < sizeof(tmp) - 1 ? len : sizeof(tmp) - 1;
	memcpy(tmp, buf, n);
	tmp[n] = '\0';
	g_lockReq = json_num(tmp, "lock") > 0.5f;   /* update_lock() enforces the interlock */
	return len;
}

BT_GATT_SERVICE_DEFINE(warivo_svc,
	BT_GATT_PRIMARY_SERVICE(WARIVO_SVC),
	BT_GATT_CHARACTERISTIC(WARIVO_TELE, BT_GATT_CHRC_READ | BT_GATT_CHRC_NOTIFY,
			       BT_GATT_PERM_READ, read_tele, NULL, NULL),
	BT_GATT_CCC(tele_ccc_changed, BT_GATT_PERM_READ | BT_GATT_PERM_WRITE),
	BT_GATT_CHARACTERISTIC(WARIVO_GPS, BT_GATT_CHRC_WRITE | BT_GATT_CHRC_WRITE_WITHOUT_RESP,
			       BT_GATT_PERM_WRITE, NULL, write_gps, NULL),
	BT_GATT_CHARACTERISTIC(WARIVO_CFG, BT_GATT_CHRC_WRITE,
			       BT_GATT_PERM_WRITE, NULL, write_cfg, NULL),
	BT_GATT_CHARACTERISTIC(WARIVO_LOCK, BT_GATT_CHRC_WRITE,
			       BT_GATT_PERM_WRITE, NULL, write_lock, NULL),
);

/* attrs[1] is the fff1 (telemetry) characteristic — the target for notifications. */
#define TELE_ATTR (&warivo_svc.attrs[1])

/* ------------------------------ advertising ------------------------------ */
static const struct bt_data ad[] = {
	BT_DATA_BYTES(BT_DATA_FLAGS, (BT_LE_AD_GENERAL | BT_LE_AD_NO_BREDR)),
	BT_DATA_BYTES(BT_DATA_UUID16_ALL, BT_UUID_16_ENCODE(0xfff0)),
	BT_DATA(BT_DATA_NAME_COMPLETE, "Warivo-Node", sizeof("Warivo-Node") - 1),
};

static void start_adv(void)
{
	int err = bt_le_adv_start(BT_LE_ADV_CONN, ad, ARRAY_SIZE(ad), NULL, 0);
	if (err) {
		printk("advertising failed (err %d)\n", err);
	}
}

static void connected(struct bt_conn *conn, uint8_t err)
{
	printk(err ? "connect failed (0x%02x)\n" : "phone connected\n", err);
}

static void disconnected(struct bt_conn *conn, uint8_t reason)
{
	printk("phone disconnected (0x%02x)\n", reason);
	notify_enabled = false;
	start_adv();   /* let the phone find us again */
}

BT_CONN_CB_DEFINE(conn_callbacks) = {
	.connected = connected,
	.disconnected = disconnected,
};

/* --------------------------- telemetry thread --------------------------- */
static void telemetry_thread(void *a, void *b, void *c)
{
	ARG_UNUSED(a);
	ARG_UNUSED(b);
	ARG_UNUSED(c);

	k_sem_take(&ready_sem, K_FOREVER);   /* wait for sensors + BLE to come up */

	int64_t last = k_uptime_get();
	while (1) {
		int64_t now = k_uptime_get();
		float dt = (now - last) / 1000.0f;
		last = now;

		read_sensors(dt);
		update_lock();
		build_frame();

		if (notify_enabled) {
			(void)bt_gatt_notify(NULL, TELE_ATTR, frame, frame_len);
		}
		k_msleep(TELEMETRY_PERIOD_MS);
	}
}

K_THREAD_DEFINE(telemetry_tid, 2048, telemetry_thread, NULL, NULL, NULL, 7, 0, 0);

/* ------------------------------ bring-up ------------------------------ */
static void sensors_init(void)
{
#ifdef HAS_VBAT
	if (adc_channel_setup_dt(&vbat_adc) != 0) {
		printk("ADC channel setup failed\n");
	}
#endif
#ifdef HAS_WHEEL
	if (gpio_is_ready_dt(&wheel_gpio)) {
		gpio_pin_configure_dt(&wheel_gpio, GPIO_INPUT);
		gpio_pin_interrupt_configure_dt(&wheel_gpio, GPIO_INT_EDGE_TO_ACTIVE);
		gpio_init_callback(&wheel_cb, wheel_isr, BIT(wheel_gpio.pin));
		gpio_add_callback(wheel_gpio.port, &wheel_cb);
	}
#endif
#ifdef HAS_LOCK
	if (gpio_is_ready_dt(&lock_gpio)) {
		/* Boot released — an unwired or freshly powered node never immobilises itself. */
		gpio_pin_configure_dt(&lock_gpio, GPIO_OUTPUT_INACTIVE);
	}
#endif
}

int main(void)
{
	printk("Warivo Node (Zephyr) starting\n");

	sensors_init();

	int err = bt_enable(NULL);
	if (err) {
		printk("bt_enable failed (err %d)\n", err);
		return 0;
	}
	start_adv();
	printk("advertising as Warivo-Node (service fff0)\n");

	k_sem_give(&ready_sem);   /* release the telemetry thread */
	return 0;
}

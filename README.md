# Warivo OS

A dedicated "car head-unit" system for a **Warivo 60V electric scooter**, built from two parts:

1. **Warivo Node** — a **Waveshare ESP32-C6** board
   ([product page](https://robu.in/product/waveshare-esp32-c6-microcontroller-wifi-6-development-board-with-esp32-c6-wroom-1-n8/))
   bolted to the scooter that measures battery, speed, temperature and range, and streams
   telemetry over **Bluetooth LE**.
2. **Warivo OS** — a **spare Android phone** running the **Warivo Launcher**: a custom
   home/launcher that locks the phone to a single car-like dashboard (GPS map, telemetry
   gauges, music, Google search) — nothing else. The *same* launcher ships two ways, and we
   keep **both**: a kiosk on stock Android now (fast), and a full custom **AOSP/LineageOS
   ROM** later (the "real" build).

> The scooter is a dumb machine (controller + 7-segment display only). It is **not** an
> Android device. The Android system runs on the **phone**, which acts as the head unit.

## Reality check (read first)

| Assumption | Reality | Consequence |
| --- | --- | --- |
| "Build AOSP for the scooter" | The scooter has no SoC/screen to run Android | AOSP/kiosk runs on the **phone** |
| "How does the phone reach the scooter?" | ESP32-C6 has **BLE** | Link is **Bluetooth LE**; phone keeps its own internet + BT speaker |
| "One board reads voltage + current" | ESP32-C6 has a **multi-channel ADC** | Reads both directly (ADS1115 optional for precision) |
| "We must sense the battery ourselves" | A **smart BMS** may expose V/SoC/temp/current | Read the BMS over UART; skip extra sensors |

## Layout

```
warivo-os/
├── README.md                     ← you are here
├── docs/
│   └── PROJECT_GUIDE.md          ← full architecture, hardware, ROM build, roadmap
└── firmware/
    └── warivo-node/
        └── warivo-node.ino       ← ESP32-C6 BLE telemetry firmware
```

Start with [docs/PROJECT_GUIDE.md](docs/PROJECT_GUIDE.md).

package com.test.ble;

import java.util.HashMap;
import java.util.UUID;

public class BleUuids {
    // Сервис CGM (для сканирования)
    public static final UUID CGM_SERVICE =
            UUID.fromString("0000181F-0000-1000-8000-00805f9b34fb");

    // ⭐ ГЛАВНАЯ ХАРАКТЕРИСТИКА ГЛЮКОЗЫ
    public static final UUID CGM_MEASUREMENT =
            UUID.fromString("0000f002-0000-1000-8000-00805f9b34fb");
    public static final UUID RECORD_ACCESS_CONTROL_POINT =
            UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb");
    // Сервисы
    public static final UUID GLUCOSE_SERVICE =
            UUID.fromString("0000181F-0000-1000-8000-00805f9b34fb");
    public static final UUID DEVICE_INFORMATION_SERVICE =
            UUID.fromString("0000180A-0000-1000-8000-00805f9b34fb");

    // Характеристики глюкозы
    public static final UUID GLUCOSE_MEASUREMENT =
            UUID.fromString("00002AA7-0000-1000-8000-00805f9b34fb");
    //public static final UUID RECORD_ACCESS_CONTROL_POINT =
      //      UUID.fromString("00002A52-0000-1000-8000-00805f9b34fb");
    public static final UUID CGM_CONTROL_POINT =
            UUID.fromString("00002AAC-0000-1000-8000-00805f9b34fb");
    public static final UUID CGM_FEATURE =
            UUID.fromString("00002AA8-0000-1000-8000-00805f9b34fb");
    public static final UUID CGM_STATUS =
            UUID.fromString("00002AA9-0000-1000-8000-00805f9b34fb");
    public static final UUID SESSION_START_TIME =
            UUID.fromString("00002AAA-0000-1000-8000-00805f9b34fb");
    public static final UUID SESSION_RUN_TIME =
            UUID.fromString("00002AAB-0000-1000-8000-00805f9b34fb");

    // Информационные характеристики
    public static final UUID FIRMWARE_REVISION =
            UUID.fromString("00002A26-0000-1000-8000-00805f9b34fb");
    public static final UUID MANUFACTURER_NAME =
            UUID.fromString("00002A29-0000-1000-8000-00805f9b34fb");
    public static final UUID MODEL_NUMBER =
            UUID.fromString("00002A24-0000-1000-8000-00805f9b34fb");
    public static final UUID SERIAL_NUMBER =
            UUID.fromString("00002A25-0000-1000-8000-00805f9b34fb");
}
package com.test.ble;

import com.test.ble.mock.BleMockImpl;

public class BleFactory {
    private static final boolean USE_REAL_BLE = false;  // false = заглушка, true = реальный BLE

    public static void setCallback(BleDataCallback callback) {
        if (USE_REAL_BLE) {
            // Реальный BLE
            GlucoseMonitorService.setCallback(callback);
        } else {
            // Заглушка
            BleMockImpl.setCallback(callback);
        }
    }

    public static void stop() {
        if (USE_REAL_BLE) {
            // Остановка реального сервиса
            // GlucoseMonitorService.stop();
        } else {
            BleMockImpl.stopSimulation();
        }
    }
}
package com.test.ble;

public interface BleDataCallback {
    // Когда пришли новые данные глюкозы
    void onGlucoseDataReceived(float glucoseValue, long timestamp);

    // Когда изменился статус подключения
    void onDeviceStatusChanged(String status);

    // Когда произошла ошибка
    void onError(String error);

    // Когда найдено устройство (опционально)
    void onDeviceFound(String deviceName, String deviceAddress, int rssi);
}
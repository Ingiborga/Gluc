package com.test.ble.mock;  // отдельный пакет для моков

import android.os.Handler;
import android.os.Looper;

import com.test.ble.BleDataCallback;

import java.util.Random;

public class BleMockImpl {

    private static BleDataCallback callback;
    private static Handler handler = new Handler(Looper.getMainLooper());
    private static Random random = new Random();
    private static boolean isRunning = false;

    // Подписка на события
    public static void setCallback(BleDataCallback cb) {
        callback = cb;
        if (cb != null && !isRunning) {
            startSimulation();
        }
    }

    // Запуск симуляции данных
    private static void startSimulation() {
        isRunning = true;

        // Симулируем поиск устройства
        if (callback != null) {
            callback.onDeviceFound("Mock Glucose Sensor", "00:11:22:33:44:55", -45);
        }

        // Симулируем подключение через 1 секунду
        handler.postDelayed(() -> {
            if (callback != null) {
                callback.onDeviceStatusChanged("CONNECTED");
            }

            // Запускаем генерацию данных глюкозы
            generateGlucoseData();
        }, 1000);
    }

    // Генерация случайных данных глюкозы
    private static void generateGlucoseData() {
        if (!isRunning || callback == null) return;

        // Нормальный уровень глюкозы: 4.0 - 7.0 mmol/L
        float glucose = 4.0f + random.nextFloat() * 3.0f;
        long timestamp = System.currentTimeMillis();

        callback.onGlucoseDataReceived(glucose, timestamp);

        // Новые данные каждые 5 секунд (вместо 5 минут)
        handler.postDelayed(() -> generateGlucoseData(), 5000);
    }

    // Остановка симуляции
    public static void stopSimulation() {
        isRunning = false;
        if (callback != null) {
            callback.onDeviceStatusChanged("DISCONNECTED");
        }
    }
}
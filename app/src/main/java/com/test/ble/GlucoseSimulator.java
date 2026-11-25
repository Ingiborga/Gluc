package com.test.ble;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import java.util.Random;

public class GlucoseSimulator {

    private static final String TAG = "GlucoseSimulator";

    // ✅ 5 минут
    public static final long INTERVAL_MS = 5 * 60 * 1000L;

    private static float currentGlucose = 6.0f;
    private static final Random random = new Random();
    private static BleDataCallback callback;

    /** Установить callback для UI (из MainActivity) */
    public static void setCallback(BleDataCallback cb) {
        callback = cb;
    }

    /** Запуск периодической генерации через AlarmManager */
    public static void start(Context context, BleDataCallback cb) {
        callback = cb;

        // Первое значение — сразу
        float value = generateNextValue();
        long ts = System.currentTimeMillis();
        if (callback != null) {
            callback.onGlucoseDataReceived(value, ts);
            callback.onDeviceStatusChanged("Simulator: " + value + " mmol/L");
        }

        // Планируем повтор каждые 5 минут
        scheduleNext(context);

        Log.d(TAG, "GlucoseSimulator started, interval = " + INTERVAL_MS + " ms");
    }

    /** Планирование следующего срабатывания */
    public static void scheduleNext(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, GlucoseAlarmReceiver.class);
        intent.setAction("com.test.glucn.GLUCOSE_TICK");

        PendingIntent pi = PendingIntent.getBroadcast(
                context,
                1001,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        long triggerAt = System.currentTimeMillis() + INTERVAL_MS;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            // ✅ Неточный, но энергоэффективный — идеально для 5 минут
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        } else {
            am.set(AlarmManager.RTC_WAKEUP, triggerAt, pi);
        }

        Log.d(TAG, "Next glucose tick scheduled at " + triggerAt);
    }

    /** Остановка */
    public static void stop(Context context) {
        AlarmManager am = (AlarmManager) context.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;

        Intent intent = new Intent(context, GlucoseAlarmReceiver.class);
        intent.setAction("com.test.glucn.GLUCOSE_TICK");

        PendingIntent pi = PendingIntent.getBroadcast(
                context, 1001, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );
        am.cancel(pi);

        Log.d(TAG, "GlucoseSimulator stopped");
    }

    /** Генерация нового значения */
    public static float generateNextValue() {
        float delta = (random.nextFloat() - 0.5f) * 0.8f;
        currentGlucose += delta;

        if (currentGlucose < 3.9f) currentGlucose = 3.9f + random.nextFloat() * 0.3f;
        if (currentGlucose > 10.0f) currentGlucose = 10.0f - random.nextFloat() * 0.3f;

        return Math.round(currentGlucose * 10) / 10.0f;
    }

    /** Уведомить UI */
    public static void notifyCallback(float value, long timestamp) {
        if (callback != null) {
            callback.onGlucoseDataReceived(value, timestamp);
            callback.onDeviceStatusChanged("Simulator: " + value + " mmol/L");
        }
    }
}
package com.test.ble;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.ParcelUuid;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class BluetoothScanner {
    private static final String TAG = "BluetoothScanner";

    private BluetoothLeScanner bluetoothLeScanner;
    private ScanCallback scanCallback;
    private boolean isScanning = false;

    // UUID сервиса глюкозы (пример для Freestyle Libre)
    private static final UUID GLUCOSE_SERVICE_UUID =
            UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");

    // Интерфейс для обратного вызова
    public interface ScanCallbackListener {
        void onDeviceFound(BluetoothDevice device, int rssi, byte[] scanRecord);
        void onScanFailed(int errorCode);
    }

    private ScanCallbackListener listener;

    public BluetoothScanner(ScanCallbackListener listener) {
        this.listener = listener;
        BluetoothAdapter bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        if (bluetoothAdapter != null) {
            this.bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    /**
     * Проверка наличия разрешений для сканирования
     */
    public static boolean hasScanPermissions(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+ (API 31+)
            return context.checkSelfPermission(Manifest.permission.BLUETOOTH_SCAN) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            // Android 11 и ниже
            return context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        }
    }

    public void startScan(Context context) {
        // ПРОВЕРКА РАЗРЕШЕНИЙ ПЕРЕД СКАНИРОВАНИЕМ
        if (!hasScanPermissions(context)) {
            Log.e(TAG, "Missing required permissions for BLE scan");
            if (listener != null) {
                listener.onScanFailed(2); // SCAN_FAILED_APPLICATION_REGISTRATION_FAILED
            }
            return;
        }

        if (bluetoothLeScanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            if (listener != null) {
                listener.onScanFailed(2);
            }
            return;
        }

        // Создаем фильтр для поиска только устройств с сервисом глюкозы
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(GLUCOSE_SERVICE_UUID))
                .build();
        filters.add(filter);

        // Настройки сканирования
        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                .setCallbackType(ScanSettings.CALLBACK_TYPE_ALL_MATCHES)
                .setMatchMode(ScanSettings.MATCH_MODE_AGGRESSIVE)
                .setNumOfMatches(ScanSettings.MATCH_NUM_ONE_ADVERTISEMENT)
                .setReportDelay(0L)
                .build();

        scanCallback = new ScanCallback() {
            @Override
            public void onScanResult(int callbackType, ScanResult result) {
                super.onScanResult(callbackType, result);
                BluetoothDevice device = result.getDevice();
                int rssi = result.getRssi();
                byte[] scanRecord = result.getScanRecord() != null ?
                        result.getScanRecord().getBytes() : null;

                try {
                    Log.d(TAG, "Device found: " + device.getName() + " [" + device.getAddress() + "] RSSI: " + rssi);
                } catch (SecurityException e) {
                    Log.d(TAG, "Device found (name/address unavailable) RSSI: " + rssi);
                }
                if (listener != null) {
                    listener.onDeviceFound(device, rssi, scanRecord);
                }
            }

            @Override
            public void onBatchScanResults(List<ScanResult> results) {
                super.onBatchScanResults(results);
                // Игнорируем для простоты
            }

            @Override
            public void onScanFailed(int errorCode) {
                super.onScanFailed(errorCode);
                Log.e(TAG, "Scan failed with error code: " + errorCode);
                if (listener != null) {
                    listener.onScanFailed(errorCode);
                }
            }
        };

        // ЗАПУСК СКАНИРОВАНИЯ С ОБРАБОТКОЙ SecurityException
        try {
            bluetoothLeScanner.startScan(filters, settings, scanCallback);
            isScanning = true;
            Log.d(TAG, "Scan started successfully");
        } catch (SecurityException e) {
            Log.e(TAG, "Security exception while starting scan", e);
            if (listener != null) {
                listener.onScanFailed(2);
            }
        }
    }

    public void stopScan() {
        if (bluetoothLeScanner != null && scanCallback != null && isScanning) {
            try {
                bluetoothLeScanner.stopScan(scanCallback);
                isScanning = false;
                Log.d(TAG, "Scan stopped");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while stopping scan", e);
            }
        }
    }

    public boolean isScanning() {
        return isScanning;
    }
}
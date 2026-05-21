package com.test.ble;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.NotificationCompat;

import com.test.ui.ConnectionPage;

import java.util.UUID;

public class GlucoseMonitorService extends Service implements BleManager.BleManagerListener {
    private static final String TAG = "GlucoseMonitorService";
    private static final String CHANNEL_ID = "GlucoseMonitorChannel";
    private static final int NOTIFICATION_ID = 1;

    // UUID для Freestyle Libre (пример)
    private static final UUID GLUCOSE_SERVICE_UUID =
            UUID.fromString("00001808-0000-1000-8000-00805f9b34fb");
    private static final UUID GLUCOSE_MEASUREMENT_CHAR_UUID =
            UUID.fromString("00002A18-0000-1000-8000-00805f9b34fb");

    private BleManager bleManager;
    private BluetoothAdapter bluetoothAdapter;
    private Handler readHandler;
    private Runnable readRunnable;
    private boolean isReading = false;
    private String deviceAddress = null;
    private BluetoothScanner scanner;

    private static BleDataCallback callback = null;
    public static void setCallback(BleDataCallback cb) {
        callback = cb;
    }
    // Bonding receiver
    private final BroadcastReceiver bondStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            final String action = intent.getAction();
            if (action == null) return;

            if (action.equals(BluetoothDevice.ACTION_BOND_STATE_CHANGED)) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (device == null) return;

                final int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Bonding completed successfully");
                    if (bleManager != null && bleManager.isConnected()) {
                        startGlucoseReading();
                    }
                }
            }
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();

        bleManager = new BleManager(this, this);
        bluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        readHandler = new Handler(Looper.getMainLooper());

        // Регистрируем receiver для bonding
        registerReceiver(bondStateReceiver,
                new IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED));
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());

        // Получаем сохраненный MAC-адрес устройства
        SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
        deviceAddress = prefs.getString("device_address", null);

        if (deviceAddress != null && bluetoothAdapter != null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device);
        } else {
            startDeviceScanning();
        }

        return START_STICKY;
    }

    private void startDeviceScanning() {
        scanner = new BluetoothScanner(new BluetoothScanner.ScanCallbackListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi, byte[] scanRecord) {
                if (callback != null) {
                    String name;
                    try {
                        name = device.getName() != null ? device.getName() : "Unknown";
                    } catch (SecurityException e) {name="Unknown";}
                    callback.onDeviceFound(name, device.getAddress(), rssi);
                }
                Log.d(TAG, "Found device: " + device.getAddress());
                SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
                prefs.edit().putString("device_address", device.getAddress()).apply();
                deviceAddress = device.getAddress();

                connectToDevice(device);

                if (scanner != null) {  // ← теперь можно
                    scanner.stopScan();
                }
            }

            @Override
            public void onScanFailed(int errorCode) {
                Log.e(TAG, "Scan failed: " + errorCode);
                if (callback != null) {
                    callback.onError("Scan failed: " + errorCode);
                }            }
        });

        scanner.startScan(this);

        readHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (scanner != null) {
                    scanner.stopScan();
                }
            }
        }, 30000);
    }

    private void connectToDevice(BluetoothDevice device) {
        Log.d(TAG, "Connecting to device: " + device.getAddress());
        bleManager.connect(device);
    }

    private void startGlucoseReading() {
        if (isReading) return;

        BluetoothGattService glucoseService = bleManager.getService(GLUCOSE_SERVICE_UUID);
        if (glucoseService == null) {
            Log.e(TAG, "Glucose service not found");
            return;
        }

        BluetoothGattCharacteristic glucoseChar = bleManager.getCharacteristic(
                glucoseService, GLUCOSE_MEASUREMENT_CHAR_UUID);
        if (glucoseChar == null) {
            Log.e(TAG, "Glucose measurement characteristic not found");
            return;
        }

        // Включаем уведомления
        bleManager.enableNotifications(glucoseChar, true);

        isReading = true;

        // Периодическое чтение каждые 5 минут
        readRunnable = new Runnable() {
            @Override
            public void run() {
                if (bleManager.isConnected()) {
                    bleManager.readCharacteristic(glucoseChar);
                }
                readHandler.postDelayed(this, 300000);
            }
        };
        readHandler.post(readRunnable);
    }

    // Реализация BleManagerListener
    @Override
    public void onConnected() {
        Log.d(TAG, "Device connected");
        if (callback != null) {
            callback.onDeviceStatusChanged("CONNECTED");
        }
        startGlucoseReading();
    }

    @Override
    public void onDisconnected() {
        Log.d(TAG, "Device disconnected");
        if (callback != null) {
            callback.onDeviceStatusChanged("DISCONNECTED");
        }
        isReading = false;
        if (readRunnable != null) {
            readHandler.removeCallbacks(readRunnable);
        }

        // Переподключение через 10 секунд
        readHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                if (deviceAddress != null && bluetoothAdapter != null) {
                    BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
                    connectToDevice(device);
                }
            }
        }, 10000);
    }

    @Override
    public void onConnectionFailed(String error) {
        Log.e(TAG, "Connection failed: " + error);
        if (callback != null) {
            callback.onError(error);
        }
        isReading = false;
    }

    @Override
    public void onDataReceived(byte[] data, BluetoothGattCharacteristic characteristic) {
        float glucoseValue = parseGlucoseData(data);
        long timestamp = System.currentTimeMillis();

        // Отправляем через LiveData
        if (callback != null) {
            callback.onGlucoseDataReceived(glucoseValue, timestamp);
        }
    }

    @Override
    public void onBondingRequired() {
        Log.d(TAG, "Bonding required");
        if (callback != null) {
            callback.onDeviceStatusChanged("BONDING_REQUIRED");
        }    }

    @Override
    public void onBondingComplete() {
        Log.d(TAG, "Bonding complete");
        if (callback != null) {
            callback.onDeviceStatusChanged("BONDING_COMPLETE");
        }
    }

    private float parseGlucoseData(byte[] data) {
        // Парсинг данных в зависимости от вашего датчика
        if (data.length >= 2) {
            int rawGlucose = ((data[1] & 0xFF) << 8) | (data[0] & 0xFF);
            return rawGlucose / 10.0f;
        }
        return 0.0f;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                    CHANNEL_ID,
                    "Glucose Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            channel.setDescription("Glucose monitoring service");

            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(channel);
            }
        }
    }

    private Notification createNotification() {
        Intent intent = new Intent(this, ConnectionPage.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Glucose Monitor")
                .setContentText("Monitoring glucose levels...")
                .setSmallIcon(android.R.drawable.ic_menu_edit)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .build();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bleManager != null) {
            bleManager.disconnect();
        }
        if (readRunnable != null) {
            readHandler.removeCallbacks(readRunnable);
        }
        try {
            unregisterReceiver(bondStateReceiver);
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Receiver not registered", e);
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
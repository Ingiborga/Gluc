package com.test.ble;

import static android.bluetooth.BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT;

import android.Manifest;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.le.ScanRecord;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;

import com.test.MainActivity;

import java.util.UUID;

public class GlucoseMonitorService extends Service implements BleManager.BleManagerListener {
    private static final String TAG = "GlucoseMonitorService";
    private static final String CHANNEL_ID = "GlucoseMonitorChannel";
    private static final int NOTIFICATION_ID = 1;

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
            if (BluetoothDevice.ACTION_BOND_STATE_CHANGED.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                int bondState = intent.getIntExtra(BluetoothDevice.EXTRA_BOND_STATE, -1);

                Log.d(TAG, "Bond state changed: " + bondState);

                if (bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "Device successfully bonded!");
                    /*try {
                        startGlucoseReading();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }*/
                    if (callback != null) {
                        callback.onDeviceStatusChanged("Bonded successfully");
                    }

                    // ✅ Теперь можно снова попробовать читать характеристику

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
    public void onServicesDiscovered() throws InterruptedException {
        Log.d(TAG, "Services discovered, starting glucose reading");
        if (deviceSerialNumber == null) {
            SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
            deviceSerialNumber = prefs.getString("device_serial_number", null);
            Log.d(TAG, "Loaded SN from prefs: " + deviceSerialNumber);
        }
        if (deviceSerialNumber != null && !deviceSerialNumber.isEmpty()) {
            sendSerialNumberToDevice(deviceSerialNumber);
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                try {
                    startGlucoseReading();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }
            }, 1000);
        } else {
            Log.e(TAG, "No serial number available, cannot activate");
        }
    }
    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startForeground(NOTIFICATION_ID, createNotification());

        // Получаем сохраненный MAC-адрес устройства
        SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
        //prefs.edit().remove("device_address").apply();
        deviceAddress = prefs.getString("device_address", null);
        if (deviceAddress != null && bluetoothAdapter != null) {
            BluetoothDevice device = bluetoothAdapter.getRemoteDevice(deviceAddress);
            connectToDevice(device);
        } else {
            startDeviceScanning();
        }
        return START_STICKY;
    }
    private String deviceSerialNumber = null;
    private void startDeviceScanning() {
        scanner = new BluetoothScanner(new BluetoothScanner.ScanCallbackListener() {
            @Override
            public void onDeviceFound(BluetoothDevice device, int rssi, byte[] scanRecord) {
                String name = "Unknown";
                try {
                    name = device.getName() != null ? device.getName() : "Unknown";
                } catch (SecurityException e) {
                    name = "Unknown";
                }
                if (scanRecord != null) {
                    String serialNumber = extractSerialNumberFromBytes(scanRecord);
                    Log.d(TAG, "Extracted serial number: " + serialNumber);
                    if (serialNumber != null && !serialNumber.isEmpty()) {
                        deviceSerialNumber = serialNumber;
                        SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
                        prefs.edit().putString("device_serial_number", serialNumber).apply();
                        Log.d(TAG, "Extracted SN: " + deviceSerialNumber);
                    }
                }
                if (callback != null) {
                    callback.onDeviceFound(name, device.getAddress(), rssi);
                }
                Log.d(TAG, "Found device: " + device.getAddress());
                SharedPreferences prefs = getSharedPreferences("glucose_prefs", MODE_PRIVATE);
                prefs.edit().putString("device_address", device.getAddress()).apply();
                deviceAddress = device.getAddress();
                connectToDevice(device);
                if (scanner != null) {
                    scanner.stopScan();
                }
            }
            private String extractSerialNumberFromBytes(byte[] scanRecord) {
                if (scanRecord == null) return null;

                int i = 0;
                while (i < scanRecord.length) {
                    int length = scanRecord[i] & 0xFF;
                    if (length == 0) break;
                    int type = scanRecord[i + 1] & 0xFF;

                    if (type == 0x09) { // Complete Local Name
                        byte[] nameBytes = new byte[length - 1];
                        System.arraycopy(scanRecord, i + 2, nameBytes, 0, length - 1);
                        String deviceName = new String(nameBytes);

                        if (deviceName.contains("AiDEX")) {
                            String[] parts = deviceName.split(" ");
                            if (parts.length > 1) {
                                return parts[1]; // "X-222226MPAJ"
                            }
                        }
                    }
                    i += length + 1;
                }
                return null;
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
        bleManager.resetConnectionState();
        bleManager.connect(device);
    }

    private void startGlucoseReading() throws InterruptedException {
        Log.d(TAG, "startGlucoseReading called");
        if (isReading) return;

        // 1. Получаем сервис CGM
        BluetoothGattService service = bleManager.getService(BleUuids.CGM_SERVICE);
        if (service == null) {
            Log.e(TAG, "CGM service (0000181f) not found");
            return;
        }
        Log.d(TAG, "CGM service found");

        // 2. Включаем уведомления на характеристике данных F002
        BluetoothGattCharacteristic glucoseChar = service.getCharacteristic(BleUuids.CGM_MEASUREMENT);
        if (glucoseChar == null) {
            Log.e(TAG, "Characteristic F002 (0000f002) not found");
            return;
        }
        Log.d(TAG, "Characteristic F002 found, Properties: " + glucoseChar.getProperties());

        // Включаем уведомления
        bleManager.enableNotifications(glucoseChar, true);
        Log.d(TAG, "Notifications enabled for F002");

        // 3. ОТПРАВЛЯЕМ АКТИВАЦИОННУЮ КОМАНДУ НА CONTROL POINT
        BluetoothGattCharacteristic controlPoint = service.getCharacteristic(
                UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"));

        if (controlPoint != null) {
            // Команда активации из логов (01 00)
            byte[] command = new byte[]{0x01, 0x00};
            bleManager.writeCharacteristic(controlPoint, command,
                    WRITE_TYPE_DEFAULT);
            Log.d(TAG, "Activation command (01 00) sent to Control Point (00002a52)");
            // После первой команды
            Thread.sleep(100);
            byte[] command2 = new byte[]{0x02, 0x00};
            bleManager.writeCharacteristic(controlPoint, command2, WRITE_TYPE_DEFAULT);
        } else {
            Log.e(TAG, "Control Point (00002a52) not found");
        }

        isReading = true;
        Log.d(TAG, "Glucose reading started, waiting for notifications...");
    }

    // Реализация BleManagerListener
    @Override
    public void onConnected() {
        Log.d(TAG, "Device connected");
        String deviceName = bleManager.getDeviceName();
        String deviceAddress = bleManager.getDeviceAddress();

        Log.d(TAG, "Device connected: " + deviceName + " [" + deviceAddress + "]");
        if (callback != null) {
            callback.onDeviceStatusChanged("CONNECTED");
        }
        BluetoothDevice device = bleManager.getBluetoothDevice();  // нужен метод
        if (device != null) {
            Log.d(TAG, "Starting bonding with: " + device.getAddress());
            if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                // TODO: Consider calling
                //    ActivityCompat#requestPermissions
                // here to request the missing permissions, and then overriding
                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                //                                          int[] grantResults)
                // to handle the case where the user grants the permission. See the documentation
                // for ActivityCompat#requestPermissions for more details.
                return;
            }
            device.createBond();
        }

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

        if (glucoseValue < 0) {
            Log.w(TAG, "Invalid glucose data, skipping");
            return;
        }

        // Получаем timestamp из данных (если есть) или текущее время
        long timestamp = extractTimestampFromData(data);

        // Отправляем через Callback в UI
        if (callback != null) {
            callback.onGlucoseDataReceived(glucoseValue, timestamp);
        }
    }



    @Override
    public void onBondingRequired() {
        Log.d(TAG, "Bonding required, creating bond...");

        // Создаем bonding
        bleManager.createBond();

        // Уведомляем UI
        if (callback != null) {
            callback.onDeviceStatusChanged("Bonding in progress...");
        }
    }

    @Override
    public void onBondingComplete() {
        Log.d(TAG, "Bonding complete");
        if (callback != null) {
            callback.onDeviceStatusChanged("BONDING_COMPLETE");
        }
    }
    public static class GlucoseResult {
        public float value;        // Значение в ммоль/л
        public long timestamp;     // Unix timestamp в миллисекундах
        public int status;         // Статус сенсора (0=норма, 1=низкий, 2=высокий)
        public boolean isValid;    // Валидны ли данные
        public int sequenceNumber; // Порядковый номер
    }
    private float parseGlucoseData(byte[] data) {
        if (data.length < 4) return -1f;

        int flags = data[0] & 0xFF;
        boolean isMgDl = (flags & 0x01) == 0;  // 0=мг/дл, 1=ммоль/л

        int rawGlucose = ((data[3] & 0xFF) << 8) | (data[2] & 0xFF);

        if (isMgDl) {
            return rawGlucose / 18.0f;  // мг/дл → ммоль/л
        } else {
            return rawGlucose / 10.0f;  // уже ммоль/л
        }
    }
    private long extractTimestampFromData(byte[] data) {
        if (data != null && data.length >= 8) {
            // Unix timestamp в секундах (байты 4-7, Little-Endian)
            long seconds = ((long)(data[7] & 0xFF) << 24) |
                    ((long)(data[6] & 0xFF) << 16) |
                    ((long)(data[5] & 0xFF) << 8)  |
                    ((long)(data[4] & 0xFF));

            if (seconds > 0) {
                return seconds * 1000;  // переводим в миллисекунды
            }
        }
        // Если timestamp не найден, используем текущее время
        return System.currentTimeMillis();
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
        Intent intent = new Intent(this, MainActivity.class);
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
    // Добавьте этот метод в конец класса GlucoseMonitorService
    private void sendSerialNumberToDevice(String serialNumber) {
        Log.d(TAG, "sendSerialNumberToDevice called with: " + serialNumber);
        BluetoothGattService service = bleManager.getService(BleUuids.CGM_SERVICE);
        if (service == null) {
            Log.e(TAG, "CGM service not found");
            return;
        }
        BluetoothGattCharacteristic controlPoint = service.getCharacteristic(
                UUID.fromString("00002a52-0000-1000-8000-00805f9b34fb"));
        if (controlPoint != null) {
            byte[] snBytes = serialNumber.getBytes();
            byte[] command = new byte[snBytes.length + 1];
            command[0] = 0x01;  // команда "регистрация"
            System.arraycopy(snBytes, 0, command, 1, snBytes.length);

            bleManager.writeCharacteristic(controlPoint, command,
                    BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
            Log.d(TAG, "SN sent: " + serialNumber);
        } else {
            Log.e(TAG, "Control point not found");
        }
    }
    private String extractSerialNumberFromScanRecord(ScanRecord scanRecord) {
        if (scanRecord == null) return null;

        byte[] data = scanRecord.getBytes();
        int i = 0;
        while (i < data.length) {
            int length = data[i] & 0xFF;
            if (length == 0) break;
            int type = data[i + 1] & 0xFF;

            if (type == 0x09) { // Complete Local Name
                byte[] nameBytes = new byte[length - 1];
                System.arraycopy(data, i + 2, nameBytes, 0, length - 1);
                String deviceName = new String(nameBytes);

                if (deviceName.contains("AiDEX")) {
                    String[] parts = deviceName.split(" ");
                    if (parts.length > 1) {
                        return parts[1]; // "X-222226MPAJ"
                    }
                }
            }
            i += length + 1;
        }
        return null;
    }
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}
package com.test.ble;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothProfile;
import android.content.Context;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.lang.reflect.Method;
import java.util.LinkedList;
import java.util.Queue;
import java.util.UUID;

public class BleManager {
    private static final String TAG = "BleManager";
    private static final int MAX_RETRIES = 3;
    private static final int CONNECTION_TIMEOUT_MS = 60000;

    // UUID для Client Characteristic Configuration Descriptor
    private static final UUID CCC_DESCRIPTOR_UUID =
            UUID.fromString("00002902-0000-1000-8000-00805f9b34fb");

    private Context context;
    private BluetoothGatt bluetoothGatt;
    private Queue<Runnable> commandQueue;//Очередь команд
    private boolean isCommandRunning;// Флаг выполнения команды
    private int retryCount;
    private Handler bleHandler;// Обработчик для команд
    private Handler timeoutHandler;// Обработчик таймаутов

    private String deviceAddress;
    private BleManagerListener listener;
    private boolean isConnecting = false;
    private boolean isDisconnecting = false;

    // Интерфейс для обратных вызовов
    public interface BleManagerListener {
        void onConnected();
        void onDisconnected();
        void onServicesDiscovered() throws InterruptedException;
        void onConnectionFailed(String error);
        void onDataReceived(byte[] data, BluetoothGattCharacteristic characteristic);
        void onBondingRequired();
        void onBondingComplete();
    }
    public BluetoothDevice getBluetoothDevice() {
        if (bluetoothGatt != null) {
            return bluetoothGatt.getDevice();
        }
        return null;
    }
    public String getDeviceName() {
        if (bluetoothGatt != null) {
            try {
                return bluetoothGatt.getDevice().getName();
            } catch (SecurityException e) {
                Log.e(TAG, "Cannot get device name - permission missing", e);
                return "Unknown";
            }
        }
        return "Not connected";
    }
    public BleManager(Context context, BleManagerListener listener) {
        this.context = context;
        this.listener = listener;

        this.commandQueue = new LinkedList<>();
        this.isCommandRunning = false;
        this.bleHandler = new Handler(Looper.getMainLooper());
        this.timeoutHandler = new Handler(Looper.getMainLooper());
    }

    // GATT колбек (статья #2)
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            timeoutHandler.removeCallbacks(connectionTimeoutRunnable);
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                    Log.d(TAG, "Connected to device: " + deviceAddress);
                    isConnecting = false;
                    // Проверяем состояние bonding (статья #4)
                    int bondState = BluetoothDevice.BOND_NONE;
                    try {
                        bondState = gatt.getDevice().getBondState();
                    } catch (SecurityException e) {
                        Log.e(TAG, "Cannot get bond state - permission missing", e);
                    }
                    if (bondState == BluetoothDevice.BOND_BONDING) {
                        Log.d(TAG, "Bonding in progress, waiting...");
                        if (listener != null) {
                            listener.onBondingRequired();
                        }
                        return;
                    }
                    // Небольшая задержка для Android 7 и ниже (статья #2)
                    int delay = 2000;
                    bleHandler.postDelayed(() -> {
                        if (bluetoothGatt != null) {
                            Log.d(TAG, "Discovering services...");
                            try {
                                bluetoothGatt.discoverServices();
                            }catch(SecurityException e) {
                                Log.e(TAG, "Cannot get bond state - permission missing", e);
                            }
                        }
                    }, delay);
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    Log.d(TAG, "Disconnected from device");
                    isDisconnecting = false;
                    isConnecting = false;
                    clearCommandQueue();
                    if (bluetoothGatt != null) {
                        try {
                            bluetoothGatt.close();
                            Log.d(TAG, "Gatt closed successfully");
                        } catch (SecurityException e) {
                            Log.e(TAG, "Security exception while closing Gatt", e);
                        }
                        bluetoothGatt = null;
                    }
                    if (listener != null) {
                        listener.onDisconnected();
                    }
                }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Services discovered. Total services: " + gatt.getServices().size());
                // Выводим все сервисы для отладки
                for (BluetoothGattService service : gatt.getServices()) {
                    Log.d(TAG, "Service: " + service.getUuid());
                    for (BluetoothGattCharacteristic characteristic : service.getCharacteristics()) {
                        Log.d(TAG, "  Characteristic: " + characteristic.getUuid() +
                                ", Properties: " + characteristic.getProperties());
                    }
                }
                if (listener != null) {
                    listener.onConnected();
                    try {
                        listener.onServicesDiscovered();
                    } catch (InterruptedException e) {
                        throw new RuntimeException(e);
                    }
                }
                // Запускаем следующую команду из очереди
                nextCommand();
            } else {
                Log.e(TAG, "Service discovery failed, status: " + status);
                disconnect();
            }
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic characteristic,
                                         int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                byte[] data = characteristic.getValue();
                Log.d(TAG, "Characteristic read: " + characteristic.getUuid() +
                        ", data length: " + (data != null ? data.length : 0));

                if (status == BluetoothGatt.GATT_SUCCESS) {
                    // ✅ УСПЕШНОЕ ЧТЕНИЕ — отдаем данные
                    if (listener != null && data != null) {
                        listener.onDataReceived(data, characteristic);  // ← ДЛЯ ДАННЫХ
                    }
                } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                    // ❌ НЕДОСТАТОЧНО ПРАВ — требуется bonding
                    if (listener != null) {
                        listener.onBondingRequired();  // ← ДЛЯ BONDING (ЭТО ПРАВИЛЬНО!)
                    }
                }
            } else if (status == BluetoothGatt.GATT_INSUFFICIENT_AUTHENTICATION) {
                // Требуется bonding (статья #4)
                Log.w(TAG, "Insufficient authentication, bonding required");
                if (listener != null) {
                    listener.onBondingRequired();
                }
            } else {
                Log.e(TAG, "Characteristic read failed, status: " + status);
            }

            completeCommand();
        }

        @Override
        public void onCharacteristicWrite(BluetoothGatt gatt,
                                          BluetoothGattCharacteristic characteristic,
                                          int status) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Characteristic write successful: " + characteristic.getUuid());
            } else {
                Log.e(TAG, "Characteristic write failed, status: " + status);
            }

            completeCommand();
        }

        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            BluetoothGattCharacteristic characteristic) {
            Log.e(TAG, "🔥🔥🔥 NOTIFICATION RECEIVED! 🔥🔥🔥");
            Log.d(TAG, "Characteristic changed got (notification): " + characteristic.getUuid() +
                    ", data length: ");
            // Важно: создаем копию данных, так как объект переиспользуется (статья #3)
            byte[] originalData = characteristic.getValue();
            if (originalData != null) {
                byte[] dataCopy = new byte[originalData.length];
                System.arraycopy(originalData, 0, dataCopy, 0, originalData.length);

                Log.d(TAG, "Characteristic changed (notification): " + characteristic.getUuid() +
                        ", data length: " + dataCopy.length);

                if (listener != null) {
                    listener.onDataReceived(dataCopy, characteristic);
                }
            }
        }

        @Override
        public void onDescriptorWrite(BluetoothGatt gatt,
                                      BluetoothGattDescriptor descriptor,
                                      int status) {
            Log.d(TAG, "Descriptor write, status: " + status);
            completeCommand();
        }
    };

    // Таймаут подключения
    private Runnable connectionTimeoutRunnable = () -> {
        if (isConnecting) {
            Log.e(TAG, "Connection timeout");
            isConnecting = false;
            if (bluetoothGatt != null) {
                try {
                    bluetoothGatt.close();
                    Log.d(TAG, "Gatt closed successfully");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception while closing Gatt", e);
                }
                bluetoothGatt = null;
            }
            if (listener != null) {
                listener.onConnectionFailed("Connection timeout");
            }
        }
    };

    // Подключение к устройству (статья #2)
    public void connect(BluetoothDevice device) {
        if (device == null) {
            Log.e(TAG, "Device is null");
            return;
        }
        if (isConnecting || isDisconnecting) {
            Log.w(TAG, "Already connecting/disconnecting");
            return;
        }
        deviceAddress = device.getAddress();
        isConnecting = true;
        // Важно: используем TRANSPORT_LE (статья #2)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback,
                        BluetoothDevice.TRANSPORT_LE);
                Log.d(TAG, "Gatt connect successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while connection Gatt", e);
            }
        } else {
            try {
                bluetoothGatt = device.connectGatt(context, false, gattCallback);
                Log.d(TAG, "Gatt connect successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while connection Gatt", e);
            }
        }
        // Устанавливаем таймаут подключения
        timeoutHandler.postDelayed(connectionTimeoutRunnable, CONNECTION_TIMEOUT_MS);
    }

    // Отключение (статья #2)
    public void disconnect() {
        if (isDisconnecting) {
            return;
        }

        isDisconnecting = true;
        clearCommandQueue();

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.disconnect();
                Log.d(TAG, "Gatt closed successfully");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while closing Gatt", e);
            }
            bluetoothGatt = null;
        } else {
            isDisconnecting = false;
        }
    }

    // Чтение характеристики (статья #3)
    public boolean readCharacteristic(BluetoothGattCharacteristic characteristic) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Gatt is null");
            return false;
        }

        if (characteristic == null) {
            Log.e(TAG, "Characteristic is null");
            return false;
        }

        // Проверяем, поддерживается ли чтение
        if ((characteristic.getProperties() & BluetoothGattCharacteristic.PROPERTY_READ) == 0) {
            Log.e(TAG, "Characteristic does not support READ");
            return false;
        }

        enqueueCommand(() -> {
            if (bluetoothGatt != null) {
                boolean result;
                try {
                    result = bluetoothGatt.readCharacteristic(characteristic);
                    Log.d(TAG, "Gatt read characteristic successfully");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception while read characteristic Gatt", e);
                    result = false;
                }
                if (!result) {
                    Log.e(TAG, "readCharacteristic returned false");
                    completeCommand();
                } else {
                    retryCount = 0;
                }
            } else {
                completeCommand();
            }
        });

        return true;
    }

    // Запись характеристики (статья #3)
    public boolean writeCharacteristic(BluetoothGattCharacteristic characteristic,
                                       byte[] value,
                                       int writeType) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Gatt is null");
            return false;
        }

        if (characteristic == null) {
            Log.e(TAG, "Characteristic is null");
            return false;
        }

        // Проверяем поддержку типа записи
        int requiredProperty;
        switch (writeType) {
            case BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT:
                requiredProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
                break;
            case BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE:
                requiredProperty = BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE;
                break;
            default:
                requiredProperty = BluetoothGattCharacteristic.PROPERTY_WRITE;
                break;
        }

        if ((characteristic.getProperties() & requiredProperty) == 0) {
            Log.e(TAG, "Characteristic does not support write type: " + writeType);
            return false;
        }

        enqueueCommand(() -> {
            if (bluetoothGatt != null) {
                characteristic.setValue(value);
                characteristic.setWriteType(writeType);
                boolean result;
                try {
                    result = bluetoothGatt.writeCharacteristic(characteristic);
                    Log.d(TAG, "Gatt read characteristic successfully");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception while read characteristic Gatt", e);
                    result = false;
                }

                if (!result) {
                    Log.e(TAG, "writeCharacteristic returned false");
                    completeCommand();
                } else {
                    retryCount = 0;
                }
            } else {
                completeCommand();
            }
        });

        return true;
    }

    // Включение уведомлений (статья #3)
    public boolean enableNotifications(BluetoothGattCharacteristic characteristic, boolean enable) {
        if (bluetoothGatt == null) {
            Log.e(TAG, "Gatt is null");
            return false;
        }

        if (characteristic == null) {
            Log.e(TAG, "Characteristic is null");
            return false;
        }

        // Получаем CCC дескриптор
        BluetoothGattDescriptor descriptor = characteristic.getDescriptor(CCC_DESCRIPTOR_UUID);
        if (descriptor == null) {
            Log.e(TAG, "CCC Descriptor not found");
            return false;
        }

        // Определяем значение для записи
        byte[] value;
        int properties = characteristic.getProperties();
        if ((properties & BluetoothGattCharacteristic.PROPERTY_NOTIFY) != 0) {
            // ✅ ИСПРАВЛЕНО: для некоторых датчиков нужно 0x01, 0x00
            if (enable) {
                value = new byte[]{0x01, 0x00};  // включить уведомления
            } else {
                value = new byte[]{0x00, 0x00};  // выключить
            }
        } else if ((properties & BluetoothGattCharacteristic.PROPERTY_INDICATE) != 0) {
            if (enable) {
                value = new byte[]{0x02, 0x00};  // для индикаций
            } else {
                value = new byte[]{0x00, 0x00};
            }
        } else {
            Log.e(TAG, "Characteristic does not support NOTIFY or INDICATE");
            return false;
        }
        final byte[] finalValue = value;

        enqueueCommand(() -> {
            if (bluetoothGatt != null) {
                // Сначала устанавливаем уведомление
                boolean result;
                try {
                    result = bluetoothGatt.setCharacteristicNotification(characteristic, enable);
                    Log.d(TAG, "Gatt closed successfully");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception while closing Gatt", e);
                    result = false;
                }
                if (!result) {
                    Log.e(TAG, "setCharacteristicNotification failed");
                    completeCommand();
                    return;
                }

                // Затем записываем в дескриптор
                descriptor.setValue(finalValue);
                try {
                    result = bluetoothGatt.writeDescriptor(descriptor);
                    Log.d(TAG, "Gatt closed successfully");
                } catch (SecurityException e) {
                    Log.e(TAG, "Security exception while closing Gatt", e);
                    result=false;
                }
                if (!result) {
                    Log.e(TAG, "writeDescriptor failed");
                    completeCommand();
                }
            } else {
                completeCommand();
            }
        });

        return true;
    }

    // Очистка кеша сервисов через рефлексию (статья #2)
    public boolean clearServicesCache() {
        if (bluetoothGatt == null) {
            return false;
        }

        try {
            Method refreshMethod = bluetoothGatt.getClass().getMethod("refresh");
            if (refreshMethod != null) {
                boolean result = (boolean) refreshMethod.invoke(bluetoothGatt);
                Log.d(TAG, "Services cache cleared: " + result);
                return result;
            }
        } catch (Exception e) {
            Log.e(TAG, "Could not invoke refresh method", e);
        }
        return false;
    }

    // Поиск сервиса по UUID
    public BluetoothGattService getService(UUID serviceUuid) {
        if (bluetoothGatt == null) {
            return null;
        }
        return bluetoothGatt.getService(serviceUuid);
    }

    // Поиск характеристики в сервисе
    public BluetoothGattCharacteristic getCharacteristic(BluetoothGattService service,
                                                         UUID characteristicUuid) {
        if (service == null) {
            return null;
        }
        return service.getCharacteristic(characteristicUuid);
    }
    public void createBond() {
        if (bluetoothGatt != null) {
            try {
                BluetoothDevice device = bluetoothGatt.getDevice();
                Log.d(TAG, "Creating bond with device: " + device.getAddress());

                // Метод createBond() доступен через рефлексию или напрямую (API 19+)
                boolean result = device.createBond();
                Log.d(TAG, "createBond() returned: " + result);
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while creating bond", e);
            }
        }
    }

    // Очередь команд (статья #3)
    private void enqueueCommand(Runnable command) {
        commandQueue.add(command);
        nextCommand();
    }

    private void nextCommand() {
        if (isCommandRunning || commandQueue.isEmpty()) {
            return;
        }

        isCommandRunning = true;
        Runnable nextCommand = commandQueue.peek();
        bleHandler.post(nextCommand);
    }

    private void completeCommand() {
        isCommandRunning = false;
        if (!commandQueue.isEmpty()) {
            commandQueue.poll();
        }
        nextCommand();
    }

    private void retryCommand() {
        isCommandRunning = false;
        if (retryCount < MAX_RETRIES) {
            retryCount++;
            Log.d(TAG, "Retrying command, attempt " + retryCount);
            nextCommand();
        } else {
            Log.e(TAG, "Max retries reached, removing command");
            commandQueue.poll();
            nextCommand();
        }
    }

    private void clearCommandQueue() {
        commandQueue.clear();
        isCommandRunning = false;
    }

    public boolean isConnected() {
        return bluetoothGatt != null;
    }

    public String getDeviceAddress() {
        return deviceAddress;
    }
    public void resetConnectionState() {
        Log.d(TAG, "Resetting connection state");
        isConnecting = false;
        isDisconnecting = false;
        clearCommandQueue();

        if (bluetoothGatt != null) {
            try {
                bluetoothGatt.close();
                Log.d(TAG, "Gatt closed during reset");
            } catch (SecurityException e) {
                Log.e(TAG, "Security exception while closing Gatt during reset", e);
            }
            bluetoothGatt = null;
        }
    }
}

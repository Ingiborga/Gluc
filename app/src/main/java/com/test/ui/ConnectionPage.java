package com.test.ui;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.Observer;

import com.test.R;
import com.test.ble.BleDataCallback;
import com.test.ble.GlucoseMonitorService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import com.test.ble.BleFactory;

public class ConnectionPage extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private TextView glucoseValueText;
    private TextView statusText;
    private TextView timestampText;
    private Button startServiceButton;
    private Button stopServiceButton;
    private TextView deviceListText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.connection_layout);

        glucoseValueText = findViewById(R.id.glucoseValueText);//
        statusText = findViewById(R.id.statusText);
        timestampText = findViewById(R.id.timestampText);
        startServiceButton = findViewById(R.id.startServiceButton);
        stopServiceButton = findViewById(R.id.stopServiceButton);
        deviceListText = findViewById(R.id.deviceListText);
        startServiceButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                startGlucoseService();
            }
        });

        stopServiceButton.setOnClickListener(new android.view.View.OnClickListener() {
            @Override
            public void onClick(android.view.View v) {
                stopGlucoseService();
            }
        });
        BleFactory.setCallback(new BleDataCallback() {

            @Override
            public void onGlucoseDataReceived(float glucoseValue, long timestamp) {
                // Данные глюкозы пришли
                runOnUiThread(() -> {
                    glucoseValueText.setText(String.format("%.1f mmol/L", glucoseValue));

                    SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
                    timestampText.setText("Last update: " + sdf.format(new Date(timestamp)));
                });
            }

            @Override
            public void onDeviceStatusChanged(String status) {
                // Статус подключения изменился
                runOnUiThread(() -> {
                    statusText.setText("Status: " + status);
                });
            }

            @Override
            public void onError(String error) {
                // Ошибка
                runOnUiThread(() -> {
                    Toast.makeText(ConnectionPage.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    statusText.setText("Status: ERROR - " + error);
                });
            }

            @Override
            public void onDeviceFound(String deviceName, String deviceAddress, int rssi) {
                // Найдено BLE устройство
                runOnUiThread(() -> {
                    String deviceInfo = deviceName + " [" + deviceAddress + "] RSSI: " + rssi;
                    deviceListText.append(deviceInfo + "\n");
                });
            }
        });
        checkPermissions();
    }

    private void startGlucoseService() {
        Intent intent = new Intent(this, GlucoseMonitorService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        Toast.makeText(this, "Glucose monitoring started", Toast.LENGTH_SHORT).show();
    }

    private void stopGlucoseService() {
        Intent intent = new Intent(this, GlucoseMonitorService.class);
        stopService(intent);
        Toast.makeText(this, "Glucose monitoring stopped", Toast.LENGTH_SHORT).show();
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12+
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            // Android 13+
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
            // Android 11 и ниже
            permissions = new String[]{
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
            };
        }

        boolean allGranted = true;
        for (String permission : permissions) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                allGranted = false;
                break;
            }
        }

        if (!allGranted) {
            ActivityCompat.requestPermissions(this, permissions, PERMISSION_REQUEST_CODE);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSION_REQUEST_CODE) {
            boolean allGranted = true;
            for (int result : grantResults) {
                if (result != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    break;
                }
            }
            if (allGranted) {
                Toast.makeText(this, "Permissions granted", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Permissions required for BLE", Toast.LENGTH_LONG).show();
            }
        }
    }
    @Override
    protected void onDestroy() {
        super.onDestroy();
        // ========== ВАЖНО: Отписываемся ==========
        BleFactory.stop();
    }
}
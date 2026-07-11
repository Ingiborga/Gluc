package com.test;
import android.view.Menu;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.MenuItem;
import android.widget.TextView;
import android.widget.Toast;
import android.util.Log;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.navigation.NavController;
import androidx.navigation.Navigation;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.test.ble.BleDataCallback;
import com.test.broadcast.AidexBroadcastReceiver;
import com.test.broadcast.BroadcastService;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class MainActivity extends AppCompatActivity {
    private static final int PERMISSION_REQUEST_CODE = 100;

    private AidexBroadcastReceiver libreReceiver;
    private TextView glucoseValueText;
    private TextView glucoseMgdlText;
    private TextView timestampText;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        glucoseValueText = findViewById(R.id.glucoseValueText);//сюда приходит глюкоза
        glucoseMgdlText = findViewById(R.id.glucoseMgdlText);//сюда приходит глюкоза в другом измерении
        timestampText = findViewById(R.id.timestampText);
        statusText = findViewById(R.id.statusText);

        BottomNavigationView bottomNavigationView = findViewById(R.id.bottomNavigationView);
        bottomNavigationView.setSelectedItemId(R.id.action_glucose);

        bottomNavigationView.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.action_glucose) {
                // Остаемся на главном экране
                return true;
            } else if (id == R.id.action_profile) {
                startActivity(new Intent(this, ProfileActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            } else if (id == R.id.action_graph) {
                startActivity(new Intent(this, GraphActivity.class));
                overridePendingTransition(0, 0);
                finish();
                return true;
            }

            return false;
        });

        AidexBroadcastReceiver.setCallback(new BleDataCallback() {//сюда приходит значение глюкозы
            @Override
            public void onGlucoseDataReceived(float glucoseValue, long timestamp) {
                runOnUiThread(() -> updateGlucoseDisplay(glucoseValue, timestamp));
            }
            @Override
            public void onDeviceStatusChanged(String status) {
                runOnUiThread(() -> {
                    if (statusText != null) {
                        statusText.setText("Status: " + status);
                    }
                    Log.d("MainActivity", "Status: " + status);
                });
            }
            @Override
            public void onError(String error) {
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error: " + error, Toast.LENGTH_LONG).show();
                    if (statusText != null) {
                        statusText.setText("Status: ERROR - " + error);
                    }
                    Log.e("MainActivity", "Error: " + error);
                });
            }
            @Override
            public void onDeviceFound(String deviceName, String deviceAddress, int rssi) {
                Log.d("MainActivity", "Device found: " + deviceName + " [" + deviceAddress + "] RSSI: " + rssi);
            }
        });
        checkPermissions();

    }


    private void updateGlucoseDisplay(float glucoseMmolL, long timestamp) {//в окошечки приходят значения клюкозы
        if (glucoseValueText != null) {
            glucoseValueText.setText(String.format(Locale.US, "%.1f", glucoseMmolL));
        }
        if (glucoseMgdlText != null) {
            float glucoseMgDl = glucoseMmolL * 18.0f;
            glucoseMgdlText.setText(String.format(Locale.US, "%.0f mg/dL", glucoseMgDl));
        }
        if (timestampText != null) {
            SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss", Locale.getDefault());
            timestampText.setText("Last update: " + sdf.format(new Date(timestamp)));
        }
        Log.d("MainActivity", String.format("Glucose updated: %.1f mmol/L (%.0f mg/dL)",
                glucoseMmolL, glucoseMmolL * 18.0f));
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {//при запуске страницы запускаются сервисы по чтению глюкозы
        super.onResume();

        libreReceiver = new AidexBroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(libreReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(libreReceiver, filter);
        }

        Intent intent = new Intent(this, BroadcastService.class);
        startService(intent);

        if (statusText != null) {
            statusText.setText("Status: Monitoring...");
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (libreReceiver != null) {
            unregisterReceiver(libreReceiver);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(this, BroadcastService.class);
        stopService(intent);
        AidexBroadcastReceiver.setCallback(null);
    }

    private void checkPermissions() {
        String[] permissions;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            permissions = new String[]{
                    Manifest.permission.BLUETOOTH_SCAN,
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.POST_NOTIFICATIONS
            };
        } else {
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
                Toast.makeText(this, "Permissions required", Toast.LENGTH_LONG).show();
            }
        }
    }
}
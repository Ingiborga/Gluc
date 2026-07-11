package com.test;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;

import androidx.appcompat.app.AppCompatActivity;

import com.test.broadcast.BroadcastReceiver;
import com.test.broadcast.BroadcastService;

public class AuthActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private BroadcastReceiver libreReceiver;

    private  SharedPreferences.Editor editor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initPrefs();

        libreReceiver = new BroadcastReceiver();
        IntentFilter filter = new IntentFilter();
        filter.addAction("com.librelink.app.ThirdPartyIntegration.GLUCOSE_READING");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(libreReceiver, filter, Context.RECEIVER_EXPORTED);
        } else {
            registerReceiver(libreReceiver, filter);
        }

        Intent serviceIntent = new Intent(this, BroadcastService.class);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(serviceIntent);
        } else {
            startService(serviceIntent);
        }

        if (prefs.contains("email")) {
            // Настройки есть
            startActivity(new Intent(this, MainActivity.class));
            finish();
        }
        //radiogroup
        findViewById(R.id.ottai).setOnClickListener((view)->onRadioButtonClicked(view));
        // устанавливаем обработчики для кнопок
        findViewById(R.id.aidex).setOnClickListener((view)->onRadioButtonClicked(view));
    }
    private void initPrefs() {
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        editor = prefs.edit();
    }
    public void onRadioButtonClicked(View view) {
        RadioButton radio = (RadioButton) view;
        // если переключатель отмечен
        boolean checked = radio.isChecked();
        // получаем текст нажатой радиокнопки
        String text = radio.getText().toString();
        // Получаем нажатый переключатель
        switch(text) {
            case "Ottai":
                editor.putString("glucometer", "ottai");
                break;
            case "Aidex":
                editor.putString("glucometer", "aidex");
                break;
        }
    }
    public void PushButton(View view){
        EditText user_email = findViewById(R.id.editTextTextEmailAddress);
        EditText user_pass = findViewById(R.id.editTextTextPassword);
        //сохранение в память
        editor.putString("email", user_email.getText().toString());
        editor.putString("password", user_pass.getText().toString());
        editor.apply();
        startActivity(new Intent(this, MainActivity.class));
        overridePendingTransition(0, 0);
        finish();
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    protected void onResume() {//при запуске страницы запускаются сервисы по чтению глюкозы
        super.onResume();


    }
    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

    }

}
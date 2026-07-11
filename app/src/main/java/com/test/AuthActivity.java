package com.test;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.test.broadcast.AidexBroadcastReceiver;
import com.test.broadcast.BroadcastService;

public class AuthActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private  SharedPreferences.Editor editor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initPrefs();

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

    @Override
    protected void onResume() {//при запуске страницы запускаются сервисы по чтению глюкозы
        super.onResume();

        //при нажатии кнопок сохранение данных локально и отправка на сервер

    }

    @Override
    protected void onPause() {
        super.onPause();

    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent intent = new Intent(this, BroadcastService.class);
        stopService(intent);
        AidexBroadcastReceiver.setCallback(null);
    }

}
package com.test;

import android.content.Intent;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.test.broadcast.AidexBroadcastReceiver;
import com.test.broadcast.BroadcastService;

public class AuthActivity extends AppCompatActivity {
    private TextView user_email;
    private TextView user_pass;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.fragment_login);

        //добавить про радиогрупп!!!

        user_email = findViewById(R.id.editTextTextEmailAddress);
        user_pass = findViewById(R.id.editTextTextPassword);

        //проверка, если данные есть, то переход на следующую страницу
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
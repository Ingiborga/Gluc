package com.test.activity;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import retrofit2.Callback;
import retrofit2.Response;
import retrofit2.Retrofit;
import retrofit2.Call;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

import androidx.appcompat.app.AppCompatActivity;

import com.test.ServerConnector;

import com.test.R;
import com.test.broadcast.BroadcastReceiver;
import com.test.broadcast.BroadcastService;
import com.test.db.DBHelper;
import com.test.db.DbTools;

import java.io.IOException;

public class AuthActivity extends AppCompatActivity {

    private SharedPreferences prefs;
    private BroadcastReceiver libreReceiver;

    private  SharedPreferences.Editor editor;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_login);
        initPrefs();
        DBHelper dbHelper = new DBHelper(this);

        DbTools.init(dbHelper);
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
            navigateToMain();
        }
        //radiogroup
        findViewById(R.id.ottai).setOnClickListener((view)->onRadioButtonSensorClicked(view));
        // устанавливаем обработчики для кнопок
        findViewById(R.id.aidex).setOnClickListener((view)->onRadioButtonSensorClicked(view));
    }
    private void initPrefs() {
        prefs = getSharedPreferences("MyPrefs", MODE_PRIVATE);
        editor = prefs.edit();
    }
    public void onRadioButtonSensorClicked(View view) {
        RadioButton radio = (RadioButton) view;
        // если переключатель отмечен
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
    public String log_or_auth(View view) {
        RadioGroup radio = findViewById(R.id.login_auth);        // если переключатель отмечен
        // получаем текст нажатой радиокнопки
        int selectedId = radio.getCheckedRadioButtonId();
        String text="";
        if (selectedId == R.id.register) {
            text = ((RadioButton) findViewById(selectedId)).getText().toString();
        } else if (selectedId == R.id.login) {
            text = ((RadioButton) findViewById(selectedId)).getText().toString();
        }
        return text;
    }
    public void PushButton(View view) throws IOException {
        EditText user_email = findViewById(R.id.editTextTextEmailAddress);
        EditText user_pass = findViewById(R.id.editTextTextPassword);
        EditText user_name = findViewById(R.id.editTextTextUsername);

        //сохранение в память
        String mailtext = user_email.getText().toString();
        String passtext = user_pass.getText().toString();
        String nametext = user_name.getText().toString();

        if (passtext.length()<8){
            Toast.makeText(this, "Пароль должен содержать не менее 8 символов", Toast.LENGTH_LONG).show();
            return;
        }
        if (!mailtext.contains("@")){
            Toast.makeText(this, "Некорректный формат почты", Toast.LENGTH_LONG).show();
            return;
        }
        if (mailtext.isEmpty()) {
            Toast.makeText(this, "Введите почту", Toast.LENGTH_SHORT).show();
            return;
        }
        if (passtext.isEmpty()) {
            Toast.makeText(this, "Введите пароль", Toast.LENGTH_SHORT).show();
            return;
        }
        switch(log_or_auth(view)) {
            case "Регистрация":
                if (nametext.isEmpty()) {
                    Toast.makeText(this, "Введите Имя", Toast.LENGTH_SHORT).show();
                    return;
                }
                ServerConnector.RegisterRequest(mailtext, passtext, nametext, new Callback<ServerConnector.ResponseMessage>() {
                    @Override
                    public void onResponse(Call<ServerConnector.ResponseMessage> call, Response<ServerConnector.ResponseMessage> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d("ServerConnector", "Запрос успешно получен");
                            ServerConnector.ResponseMessage body = response.body();

                            Log.d("ServerConnector", "Access Token: " + body.access_token);
                            Log.d("ServerConnector", "User ID: " + body.user_id);
                            Log.d("ServerConnector", "Name: " + body.name);

                            editor.putString("email", user_email.getText().toString());
                            editor.putString("password", user_pass.getText().toString());
                            editor.putString("username", user_name.getText().toString());
                            editor.putString("lower_limit_glucose", "5");
                            editor.putString("upper_limit_glucose", "10");

                            editor.apply();
                            navigateToMain();
                        }
                        else {
                            Log.d("ServerConnector", "Код ответа: " + response.code());

                            String errorMessage = "Неизвестная ошибка";
                            try {
                                if (response.errorBody() != null) {
                                    errorMessage = response.errorBody().string();
                                    Log.d("ServerConnector", "Тело ошибки: " + errorMessage);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            String finalErrorMessage = errorMessage;
                            runOnUiThread(() -> {
                                Toast.makeText(AuthActivity.this,
                                        "Ошибка сервера (код " + response.code() + "): " + finalErrorMessage,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }

                    @Override
                    public void onFailure(Call<ServerConnector.ResponseMessage> call, Throwable t) {
                        Log.d("ServerConnector", "Запрос не получен");
                        Log.d("ServerConnector", String.valueOf(t.getMessage()));
                        Toast.makeText(AuthActivity.this, "Ошибка клиента", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            case "Вход":
                ServerConnector.LoginRequest(mailtext, passtext, new Callback<ServerConnector.ResponseMessage>() {
                    @Override
                    public void onResponse(Call<ServerConnector.ResponseMessage> call, Response<ServerConnector.ResponseMessage> response) {
                        if (response.isSuccessful() && response.body() != null) {
                            Log.d("ServerConnector", "Запрос успешно получен");
                            ServerConnector.ResponseMessage body = response.body();

                            Log.d("ServerConnector", "Access Token: " + body.access_token);
                            Log.d("ServerConnector", "User ID: " + body.user_id);
                            Log.d("ServerConnector", "Name: " + body.name);

                            editor.putString("email", user_email.getText().toString());
                            editor.putString("password", user_pass.getText().toString());
                            editor.putString("username", user_name.getText().toString());
                            editor.putString("lower_limit_glucose", "5");
                            editor.putString("upper_limit_glucose", "10");

                            editor.apply();
                            navigateToMain();
                        }
                        else {
                            Log.d("ServerConnector", "Код ответа: " + response.code());

                            String errorMessage = "Неизвестная ошибка";
                            try {
                                if (response.errorBody() != null) {
                                    errorMessage = response.errorBody().string();
                                    Log.d("ServerConnector", "Тело ошибки: " + errorMessage);
                                }
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            String finalErrorMessage = errorMessage;
                            runOnUiThread(() -> {
                                Toast.makeText(AuthActivity.this,
                                        "Ошибка сервера (код " + response.code() + "): " + finalErrorMessage,
                                        Toast.LENGTH_LONG).show();
                            });
                        }
                    }
                    @Override
                    public void onFailure(Call<ServerConnector.ResponseMessage> call, Throwable t) {
                        Log.d("ServerConnector", "Запрос не получен");
                        Log.d("ServerConnector", String.valueOf(t.getMessage()));
                        Toast.makeText(AuthActivity.this, "Ошибка клиента", Toast.LENGTH_SHORT).show();
                    }
                });
                break;
            default:
                Toast.makeText(this, "Выберите действие", Toast.LENGTH_SHORT).show();
                break;
        }

    }
    private void navigateToMain() {
        Intent intent = new Intent(AuthActivity.this, MainActivity.class);
        startActivity(intent);
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
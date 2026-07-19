package com.test.server_connector;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.util.List;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.Header;
import retrofit2.http.POST;

public class ServerConnector {
    public static final String serverURL = "http://192.168.0.190:8001/";

    public static class ResponseMessage {
        public String access_token;
        public String token_type;
        public int user_id;
        public String name;
        public String status;

        @Override
        public String toString() {
            return "ResponseMessage{" +
                    "access_token='" + access_token + '\'' +
                    ", token_type='" + token_type + '\'' +
                    ", user_id=" + user_id +
                    ", name='" + name + '\'' +
                    '}';
        }
    }
    public static class LoginRequest {
        String email, password;
        public LoginRequest(String login, String password) {
            this.email = login;
            this.password = password;
        }
    }
    public static class RegisterRequest {
        String email, password,name;
        public RegisterRequest(String login, String password,String name) {
            this.email = login;
            this.password = password;
            this.name=name;
        }
    }
    public static class GlucoseRecord {
        public String glucose_value;
        public String date;

        public GlucoseRecord(String glucose_value, String date) {
            this.glucose_value = glucose_value;
            this.date = date;
        }
    }

    public static class SendDataRequest {
        public String username;
        public List<GlucoseRecord> data;

        public SendDataRequest(String username, List<GlucoseRecord> data) {
            this.username = username;
            this.data = data;
        }
    }
    public interface UserService {
        @POST("auth/register")
        Call<ResponseMessage> registerUser(@Body RegisterRequest RegisterRequest);

        @POST("auth/login")
        Call<ResponseMessage> loginUser(@Body LoginRequest LoginRequest);

        @POST("api/glucose/data")
        Call<ResponseMessage> sendData(        @Header("Authorization") String authorization,
                                               @Body SendDataRequest SendDataRequest);
    }
    public static void RegisterRequest(String login, String password, String username, Callback<ResponseMessage> callback) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverURL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UserService userService = retrofit.create(UserService.class);
        Call<ResponseMessage> call = userService.registerUser(new RegisterRequest(login, password,username));

        Log.d("ServerConnector", "Отправка запроса на сервер");
        call.enqueue(callback);
    }
    public static void LoginRequest(String login, String password, Callback<ResponseMessage> callback) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverURL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();

        UserService userService = retrofit.create(UserService.class);
        Call<ResponseMessage> call = userService.loginUser(new LoginRequest(login, password));

        Log.d("ServerConnector", "Отправка запроса на сервер");
        call.enqueue(callback);
    }
    public static void SendDataRequest(Context context,String login, List<GlucoseRecord> records, Callback<ResponseMessage> callback) {
        Retrofit retrofit = new Retrofit.Builder()
                .baseUrl(serverURL)
                .addConverterFactory(GsonConverterFactory.create())
                .build();
        SharedPreferences prefs = context.getSharedPreferences("MyPrefs", Context.MODE_PRIVATE);
        String token = prefs.getString("access_token", "");

        UserService userService = retrofit.create(UserService.class);
        Call<ResponseMessage> call = userService.sendData(
                "Bearer " + token,  // или просто token, зависит от сервера
                new SendDataRequest(login, records)
        );

        Log.d("ServerConnector", "Отправка запроса на сервер");
        call.enqueue(callback);
    }
}
package com.test;

import android.util.Log;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;
import retrofit2.http.Body;
import retrofit2.http.GET;
import retrofit2.http.POST;

public class ServerConnector {
    public static final String serverURL = "http://192.168.0.190:8001/";

    public static class ResponseMessage {
        public String access_token;
        public String token_type;
        public int user_id;
        public String name;

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
    public interface UserService {
        @POST("auth/register")
        Call<ResponseMessage> registerUser(@Body RegisterRequest RegisterRequest);

        @POST("auth/login")
        Call<ResponseMessage> loginUser(@Body LoginRequest LoginRequest);

        @GET("logout")
        Call<ResponseMessage> logoutUser();
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
}
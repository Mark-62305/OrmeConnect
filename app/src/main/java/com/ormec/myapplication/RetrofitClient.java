package com.ormec.myapplication;

import android.content.Context;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.logging.HttpLoggingInterceptor;
import retrofit2.Retrofit;
import retrofit2.converter.gson.GsonConverterFactory;

public class RetrofitClient {

    // For USB debugging with: adb reverse tcp:4000 tcp:4000
    private static final String BASE_URL = "http://127.0.0.1:4000/api/mobile/";

    private static Retrofit retrofit;

    public static ApiServices getApiService() {
        if (retrofit == null) {

            HttpLoggingInterceptor logging = new HttpLoggingInterceptor();
            logging.setLevel(HttpLoggingInterceptor.Level.BODY);

            OkHttpClient client = new OkHttpClient.Builder()
                    .addInterceptor(chain -> {
                        Request original = chain.request();

                        Context ctx = MyApp.getInstance().getApplicationContext();
                        String token = ctx.getSharedPreferences("auth", Context.MODE_PRIVATE)
                                .getString("accessToken", null);

                        // Skip Authorization header for public endpoints
                        String path = original.url().encodedPath(); // e.g. /api/mobile/qr-login
                        boolean isPublic =
                                path.endsWith("/login") ||
                                        path.endsWith("/signup") ||
                                        path.endsWith("/qr-login");

                        if (!isPublic && token != null && !token.trim().isEmpty()) {
                            Request authed = original.newBuilder()
                                    .header("Authorization", "Bearer " + token)
                                    .build();
                            return chain.proceed(authed);
                        }

                        return chain.proceed(original);
                    })
                    .addInterceptor(logging)
                    .build();

            retrofit = new Retrofit.Builder()
                    .baseUrl(BASE_URL)
                    .client(client)
                    .addConverterFactory(GsonConverterFactory.create())
                    .build();
        }

        return retrofit.create(ApiServices.class);
    }
}
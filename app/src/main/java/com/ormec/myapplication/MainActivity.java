package com.ormec.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ormec.myapplication.models.MeterHistoryResponse;
import com.ormec.myapplication.models.MeterReading;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MainActivity extends AppCompatActivity {

    private EditText email, password;
    private Button login, visitorMode,qrLogin;
    private TextView signup;

    // SharedPreferences keys
    private static final String PREFS_NAME = "MyAppPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        email = findViewById(R.id.etEmail);
        password = findViewById(R.id.etPassword);
        login = findViewById(R.id.btnLogin);
        signup = findViewById(R.id.tvCreate);
        visitorMode = findViewById(R.id.btnVisitorMode);
        qrLogin = findViewById(R.id.btnQrlogin);

        login.setOnClickListener(v -> attemptLogin());

        signup.setOnClickListener(v -> {
            Intent i = new Intent(MainActivity.this, CreateAccountActivity.class);
            startActivity(i);
        });

        visitorMode.setOnClickListener(v -> {
            // Navigate to Visitor Dashboard
            Toast.makeText(MainActivity.this, "Entering Visitor Mode", Toast.LENGTH_SHORT).show();

            // Save visitor mode flag
            SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
            prefs.edit()
                    .putLong(KEY_USER_ID, -1) // -1 indicates visitor
                    .putString(KEY_USER_ROLE, "visitor")
                    .apply();

            Intent intent = new Intent(MainActivity.this, DashboardVisitorActivity.class);
            startActivity(intent);
            finish();
        });

        qrLogin.setOnClickListener(v -> {
            // Navigate to QR Login Activity
            Intent intent = new Intent(MainActivity.this, QrLoginActivity.class);
            startActivity(intent);
        });
    }

    private void attemptLogin() {
        String userEmail = email.getText().toString().trim();
        String userPassword = password.getText().toString().trim();

        if (userEmail.isEmpty() || userPassword.isEmpty()) {
            Toast.makeText(this, "Enter email & password", Toast.LENGTH_SHORT).show();
            return;
        }

        ApiServices api = RetrofitClient.getApiService();
        // Remove role parameter from login call - adjust this based on your API
        Call<ResponseBody> call = api.login(userEmail, userPassword);

        login.setEnabled(false); // avoid double tap

        call.enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                login.setEnabled(true);

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MainActivity.this,
                            "Server error: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                    return;
                }

                try {
                    String result = response.body().string();
                    Log.d("LOGIN_RESPONSE", result);

                    // Expecting JSON from PHP, example:
                    // {"status":"success","message":"...","user":{"id":1,"email":"...","role":"..."}}
                    JSONObject json = new JSONObject(result);
                    String status = json.optString("status", "");
                    String message = json.optString("message", "Login failed.");

                    if ("success".equalsIgnoreCase(status)) {
                        JSONObject userObj = json.optJSONObject("user");
                        long userId = -1;
                        String userRole = "";

                        if (userObj != null) {
                            userId = userObj.optLong("id", -1);
                            userRole = userObj.optString("role", "user");
                        }

                        if (userId <= 0) {
                            Toast.makeText(MainActivity.this,
                                    "Login success but user_id missing in response.",
                                    Toast.LENGTH_SHORT).show();
                            return;
                        }

                        // Save user_id and role in SharedPreferences
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit()
                                .putLong(KEY_USER_ID, userId)
                                .putString(KEY_USER_ROLE, userRole)
                                .apply();

                        Toast.makeText(MainActivity.this,
                                "Login successful",
                                Toast.LENGTH_SHORT).show();

                        // Navigate to DashboardActivity
                        Intent intent = new Intent(MainActivity.this, DashboardActivity.class);
                        startActivity(intent);
                        finish();

                    } else {
                        Toast.makeText(MainActivity.this,
                                message,
                                Toast.LENGTH_SHORT).show();
                    }

                } catch (IOException e) {
                    Log.e("LOGIN_ERROR", "Parsing error", e);
                    Toast.makeText(MainActivity.this,
                            "Response parsing failed",
                            Toast.LENGTH_SHORT).show();
                } catch (JSONException e) {
                    Log.e("LOGIN_ERROR", "JSON parse error", e);
                    Toast.makeText(MainActivity.this,
                            "Invalid server response.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                login.setEnabled(true);
                Log.e("LOGIN_ERROR", "Network failure", t);
                Toast.makeText(MainActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }
}
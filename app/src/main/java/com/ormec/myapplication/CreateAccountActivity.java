package com.ormec.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.json.JSONObject;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class CreateAccountActivity extends AppCompatActivity {

    private EditText etAccountNumber, etName, etMembershipNumber, etAddress, etContactNumber, etPassword, etConfirmPassword;
    private Button btnCreateAccount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_create_account);

        etAccountNumber = findViewById(R.id.etAccountNumber);
        etName = findViewById(R.id.etName);
        etMembershipNumber = findViewById(R.id.etMembershipNumber);
        etAddress = findViewById(R.id.etAddress);
        etContactNumber = findViewById(R.id.etContactNumber);
        etPassword = findViewById(R.id.etPassword);
        etConfirmPassword = findViewById(R.id.etConfirmPassword);

        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnCreateAccount.setOnClickListener(v -> attemptSignup());
    }

    private void attemptSignup() {
        String accountNumber = etAccountNumber.getText().toString().trim();
        String name = etName.getText().toString().trim();
        String membershipNo = etMembershipNumber.getText().toString().trim();
        String address = etAddress.getText().toString().trim();
        String contactNumber = etContactNumber.getText().toString().trim();
        String password = etPassword.getText().toString();
        String confirmPassword = etConfirmPassword.getText().toString();

        if (accountNumber.isEmpty() || name.isEmpty() || membershipNo.isEmpty()
                || address.isEmpty() || contactNumber.isEmpty()
                || password.isEmpty() || confirmPassword.isEmpty()) {
            Toast.makeText(this, "All fields are required!", Toast.LENGTH_SHORT).show();
            return;
        }

        if (!password.equals(confirmPassword)) {
            Toast.makeText(this, "Passwords do not match!", Toast.LENGTH_SHORT).show();
            return;
        }

        btnCreateAccount.setEnabled(false);

        ApiServices api = RetrofitClient.getApiService();
        api.signup(accountNumber, name, membershipNo, address, contactNumber, password)
                .enqueue(new Callback<ResponseBody>() {
                    @Override
                    public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                        btnCreateAccount.setEnabled(true);

                        if (!response.isSuccessful() || response.body() == null) {
                            Toast.makeText(CreateAccountActivity.this, "Server error: " + response.code(), Toast.LENGTH_SHORT).show();
                            return;
                        }

                        try {
                            String raw = response.body().string();
                            Log.d("SIGNUP_RAW", raw);

                            JSONObject json = new JSONObject(raw);
                            String status = json.optString("status", "");
                            String message = json.optString("message", raw);

                            if ("success".equalsIgnoreCase(status)) {
                                Toast.makeText(CreateAccountActivity.this, message, Toast.LENGTH_SHORT).show();
                                finish();
                            } else {
                                Toast.makeText(CreateAccountActivity.this, "Signup failed: " + message, Toast.LENGTH_SHORT).show();
                            }
                        } catch (Exception e) {
                            Log.e("SIGNUP_PARSE", "Parse error", e);
                            Toast.makeText(CreateAccountActivity.this, "Invalid server response.", Toast.LENGTH_SHORT).show();
                        }
                    }

                    @Override
                    public void onFailure(Call<ResponseBody> call, Throwable t) {
                        btnCreateAccount.setEnabled(true);
                        Log.e("SIGNUP_FAIL", "Network error", t);
                        Toast.makeText(CreateAccountActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                    }
                });
    }

}

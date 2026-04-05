package com.ormec.myapplication;

import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.ormec.myapplication.models.UserProfile;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ProfileActivity extends AppCompatActivity {

    private TextView tvName, tvMemberId, tvAddress, tvContact, tvFullName;

    private static final String PREFS_NAME   = "MyAppPrefs";
    private static final String KEY_USER_ID  = "user_id";
    private static final String KEY_USER_ROLE = "user_role"; // in case you need it later

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        // Header + card fields
        tvName      = findViewById(R.id.tvName);
        tvMemberId  = findViewById(R.id.tvMemberId);
        tvAddress   = findViewById(R.id.tvAddress);
        tvContact   = findViewById(R.id.tvContact);
        tvFullName  = findViewById(R.id.tvFullName);

        // Button that opens My Meters page
        Button btnMeterStatus = findViewById(R.id.btnMeterStatus);
        btnMeterStatus.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, MyMetersActivity.class)));

        setupBottomNav();
        fetchProfile();
    }

    /**
     * Call the API to load the profile of the currently logged-in user.
     */
    private void fetchProfile() {

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);

        // THIS IS THE LINE YOU ARE LOOKING FOR
        long userId = prefs.getLong(KEY_USER_ID, -1);

        if (userId <= 0) {
            showErrorDialog("No logged-in user detected.");
            return;
        }

        ApiServices api = RetrofitClient.getApiService();

        // AND THIS IS THE OTHER LINE
        Call<UserProfile> call = api.getUserProfile(userId);

        call.enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showErrorDialog("Server error or invalid response (code " + response.code() + ").");
                    return;
                }

                UserProfile profile = response.body();

                tvName.setText(profile.getFullName());
                tvFullName.setText(profile.getFullName());
                String memberDisplay = (profile.getMemberId() != null && !profile.getMemberId().isEmpty())
                        ? profile.getMemberId()
                        : String.valueOf(userId);
                tvMemberId.setText("Member ID: " + memberDisplay);
                tvAddress.setText(profile.getAddress());
                tvContact.setText(profile.getContactNumber());

            }

            @Override
            public void onFailure(Call<UserProfile> call, Throwable t) {
                showErrorDialog("Network error: " + t.getMessage());
            }
        });
    }


    /**
     * Show an error dialog. Confirm → back to login, Cancel → close app.
     */
    private void showErrorDialog(String message) {
        new AlertDialog.Builder(ProfileActivity.this)
                .setTitle("Connection Error")
                .setMessage(message + "\nGo back to login page?")
                .setCancelable(false)
                .setPositiveButton("Confirm", (dialog, which) -> {
                    Intent intent = new Intent(ProfileActivity.this, MainActivity.class);
                    intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(intent);
                    finish();
                })
                .setNegativeButton("Cancel", (dialog, which) -> {
                    // Close the entire app stack
                    finishAffinity();
                })
                .show();
    }

    /**
     * Wire the bottom navigation exactly like other screens, with Profile active.
     */
    private void setupBottomNav() {
        LinearLayout navHome       = findViewById(R.id.navHome);
        LinearLayout navIncident   = findViewById(R.id.navIncident);
        LinearLayout navCalculator = findViewById(R.id.navCalculator);
        LinearLayout navProfile    = findViewById(R.id.navProfile);
        LinearLayout navLogout     = findViewById(R.id.navLogout);

        TextView tvNavHome       = findViewById(R.id.tvNavHome);
        TextView tvNavIncident   = findViewById(R.id.tvNavIncident);
        TextView tvNavCalculator = findViewById(R.id.tvNavCalculator);
        TextView tvNavProfile    = findViewById(R.id.tvNavProfile);
        TextView tvNavLogout     = findViewById(R.id.tvNavLogout);

        int grey = Color.parseColor("#B0B0B0");

        tvNavHome.setTextColor(grey);
        tvNavIncident.setTextColor(grey);
        tvNavCalculator.setTextColor(grey);
        // Profile is the active tab → green
        tvNavProfile.setTextColor(Color.parseColor("#16A34A"));
        // Logout text stays red from your layout

        navHome.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, DashboardActivity.class)));

        navIncident.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, IncidentReportsActivity.class)));

        navCalculator.setOnClickListener(v ->
                startActivity(new Intent(ProfileActivity.this, BillingCalculatorActivity.class)));

        navProfile.setOnClickListener(v -> {
            // already here – optionally do nothing
        });

        navLogout.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();
            Intent i = new Intent(ProfileActivity.this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}

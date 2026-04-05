package com.ormec.myapplication;

import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ormec.myapplication.models.UserProfile;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity {

    private TextView tvMemberName, tvMemberId;
    private TextView tvActiveMeters, tvDisconnectedMeters;
    private ImageView ivAnnouncement;
    // Changed from LinearLayout to CardView to match XML
    private CardView btnQuickBenefits, btnQuickMeters, btnQuickCalculator;

    private static final String PREFS_NAME  = "MyAppPrefs";
    private static final String KEY_USER_ID = "user_id";
    private static final String KEY_USER_ROLE = "user_role";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        setupBottomNav();

        tvMemberName          = findViewById(R.id.tvMemberName);
        tvMemberId            = findViewById(R.id.tvMemberId);
        tvActiveMeters        = findViewById(R.id.tvActiveMeters);
        tvDisconnectedMeters  = findViewById(R.id.tvDisconnectedMeters);
        ivAnnouncement        = findViewById(R.id.ivAnnouncement);

        btnQuickBenefits      = findViewById(R.id.btnQuickBenefits);
        btnQuickMeters        = findViewById(R.id.btnQuickMeters);
        btnQuickCalculator    = findViewById(R.id.btnQuickCalculator);

        findViewById(R.id.bannerSeminarScheduling).setOnClickListener(v -> {
            startActivity(new Intent(DashboardActivity.this, SchedulingActivity.class));
        });
        // Check if user is visitor or logged in
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long userId = prefs.getLong(KEY_USER_ID, -1);
        String userRole = prefs.getString(KEY_USER_ROLE, "");

        Log.d("DASHBOARD", "userId: " + userId + ", role: " + userRole);

        // Handle visitor mode
        if (userId == -1 && "visitor".equals(userRole)) {
            tvMemberName.setText("Guest User");
            tvMemberId.setText("Visitor Mode");
            tvActiveMeters.setText("0");
            tvDisconnectedMeters.setText("0");
            Toast.makeText(this, "You are in Visitor Mode. Some features may be limited.", Toast.LENGTH_LONG).show();
        } else if (userId > 0) {
            // Load profile header (name + member ID) from API
            loadDashboardHeaderFromApi();

            // TODO: replace with real counts from API
            tvActiveMeters.setText("2");
            tvDisconnectedMeters.setText("0");
        } else {
            // No valid session
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        btnQuickBenefits.setOnClickListener(v ->
                startActivity(new Intent(this, BenefitsActivity.class)));

        btnQuickMeters.setOnClickListener(v ->
                startActivity(new Intent(this, MyMetersActivity.class)));

        btnQuickCalculator.setOnClickListener(v ->
                startActivity(new Intent(this, BillingCalculatorActivity.class)));
    }

    private void loadDashboardHeaderFromApi() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        long userId = prefs.getLong(KEY_USER_ID, -1);

        if (userId <= 0) {
            // no session, go back to login
            Toast.makeText(this, "Session expired. Please login again.", Toast.LENGTH_SHORT).show();
            startActivity(new Intent(this, MainActivity.class));
            finish();
            return;
        }

        ApiServices api = RetrofitClient.getApiService();
        Call<UserProfile> call = api.getUserProfile(userId);

        call.enqueue(new Callback<UserProfile>() {
            @Override
            public void onResponse(Call<UserProfile> call, Response<UserProfile> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(DashboardActivity.this,
                            "Failed to load profile (" + response.code() + ")",
                            Toast.LENGTH_SHORT).show();
                    // Don't return to login, just show error
                    tvMemberName.setText("User");
                    tvMemberId.setText("Member ID: " + userId);
                    return;
                }

                UserProfile profile = response.body();

                // Bind to header
                tvMemberName.setText(profile.getFullName());

                String memberDisplay = (profile.getMemberId() != null &&
                        !profile.getMemberId().isEmpty())
                        ? profile.getMemberId()
                        : String.valueOf(userId);

                tvMemberId.setText("Member ID: " + memberDisplay);
            }

            @Override
            public void onFailure(Call<UserProfile> call, Throwable t) {
                Toast.makeText(DashboardActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
                // Don't return to login, just show error
                tvMemberName.setText("User");
                tvMemberId.setText("Member ID: " + userId);
            }
        });
    }

    private void setupBottomNav() {
        LinearLayout navHome       = findViewById(R.id.navHome);
        LinearLayout navIncident   = findViewById(R.id.navIncident);
        LinearLayout navCalculator = findViewById(R.id.navCalculator);
        LinearLayout navProfile    = findViewById(R.id.navProfile);
        LinearLayout navLogout     = findViewById(R.id.navLogout);

        TextView tvHome       = findViewById(R.id.tvNavHome);
        TextView tvIncident   = findViewById(R.id.tvNavIncident);
        TextView tvCalculator = findViewById(R.id.tvNavCalculator);
        TextView tvProfile    = findViewById(R.id.tvNavProfile);
        TextView tvLogout     = findViewById(R.id.tvNavLogout);

        int grey = Color.parseColor("#B0B0B0");
        tvIncident.setTextColor(grey);
        tvCalculator.setTextColor(grey);
        tvProfile.setTextColor(grey);
        tvHome.setTextColor(Color.parseColor("#16A34A")); // active

        navHome.setOnClickListener(v -> {
            // already here
        });

        navIncident.setOnClickListener(v ->
                startActivity(new Intent(this, IncidentReportsActivity.class)));

        navCalculator.setOnClickListener(v ->
                startActivity(new Intent(this, BillingCalculatorActivity.class)));

        navProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        navLogout.setOnClickListener(v -> {
            getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
                    .edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}
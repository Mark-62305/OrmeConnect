package com.ormec.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ormec.myapplication.models.MeterHistoryResponse;
import com.ormec.myapplication.models.MeterReading;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MeterHistoryActivity extends AppCompatActivity {

    private TextView tvHistoryMeterNumber;
    private TextView tvNoHistory;
    private LinearLayout containerHistoryList;

    private ApiServices api;
    private long meterId;
    private String meterNumber;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_meter_history);

        api = RetrofitClient.getApiService();

        tvHistoryMeterNumber = findViewById(R.id.tvHistoryMeterNumber);
        tvNoHistory          = findViewById(R.id.tvNoHistory);
        containerHistoryList = findViewById(R.id.containerHistoryList);

        TextView tvBack = findViewById(R.id.tvBack);
        tvBack.setOnClickListener(v -> onBackPressed());

        // Get data from intent
        Intent intent = getIntent();
        meterId = intent.getLongExtra("meter_id", -1);
        meterNumber = intent.getStringExtra("meter_number");

        if (meterNumber != null && !meterNumber.isEmpty()) {
            tvHistoryMeterNumber.setText("Meter #" + meterNumber);
        } else {
            tvHistoryMeterNumber.setText("Meter #—");
        }

        if (meterId <= 0) {
            Toast.makeText(this, "Invalid meter information", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupBottomNav();
        loadHistory();
    }

    private void loadHistory() {
        // Example: last 60 days of history (same as your prediction call)
        api.getMeterHistory(meterId, 60).enqueue(new Callback<MeterHistoryResponse>() {
            @Override
            public void onResponse(Call<MeterHistoryResponse> call,
                                   Response<MeterHistoryResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    showNoHistory("Server error: " + response.code());
                    return;
                }

                MeterHistoryResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) {
                    showNoHistory("API status: " + body.getStatus());
                    return;
                }

                List<MeterReading> readings = body.getValues();
                if (readings == null || readings.isEmpty()) {
                    showNoHistory("No history available for this meter.");
                    return;
                }

                tvNoHistory.setVisibility(View.GONE);
                containerHistoryList.removeAllViews();

                for (MeterReading r : readings) {
                    addHistoryRow(r);
                }
            }

            @Override
            public void onFailure(Call<MeterHistoryResponse> call, Throwable t) {
                showNoHistory("Network error: " + t.getMessage());
            }
        });
    }

    private void showNoHistory(String message) {
        tvNoHistory.setText(message);
        tvNoHistory.setVisibility(View.VISIBLE);
        containerHistoryList.removeAllViews();
    }

    private void addHistoryRow(MeterReading reading) {
        if (reading == null) return;

        View row = getLayoutInflater().inflate(
                R.layout.item_meter_history_row,
                containerHistoryList,
                false
        );

        TextView tvReadingDate = row.findViewById(R.id.tvReadingDate);
        TextView tvReadingKwh  = row.findViewById(R.id.tvReadingKwh);
        TextView tvReadingNote = row.findViewById(R.id.tvReadingNote);

        String readingDate = reading.getReading_date();
        String subReadingDate = "";

        if (readingDate != null) {
            subReadingDate = readingDate.length() > 10
                    ? readingDate.substring(0, 10)
                    : readingDate;
        }

        tvReadingDate.setText(subReadingDate);
        tvReadingKwh.setText(String.format(Locale.getDefault(), "%.2f kWh", reading.getKwh()));

        tvReadingNote.setVisibility(View.GONE);

        containerHistoryList.addView(row);
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
        tvHome.setTextColor(Color.parseColor("#16A34A")); // "Home" as active section

        navHome.setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));

        navIncident.setOnClickListener(v ->
                startActivity(new Intent(this, IncidentReportsActivity.class)));

        navCalculator.setOnClickListener(v ->
                startActivity(new Intent(this, BillingCalculatorActivity.class)));

        navProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));

        navLogout.setOnClickListener(v -> {
            getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                    .edit()
                    .clear()
                    .apply();

            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}

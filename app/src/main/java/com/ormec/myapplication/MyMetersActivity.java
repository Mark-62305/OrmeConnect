package com.ormec.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.ormec.myapplication.models.MeterHistoryResponse;
import com.ormec.myapplication.models.MeterInfo;
import com.ormec.myapplication.models.MeterListResponse;
import com.ormec.myapplication.models.MeterReading;

import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class MyMetersActivity extends AppCompatActivity {

    private static final String TAG = "MyMetersActivity";

    private LinearLayout containerMeters;
    private ApiServices api;
    private ConsumptionPredictor predictor; // TFLite model

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_my_meters);

        containerMeters = findViewById(R.id.containerMeters);
        api = RetrofitClient.getApiService();

        // Initialize TFLite model
        predictor = new ConsumptionPredictor(this);

        if (predictor.isReady()) {
            Log.i(TAG, "TFLite predictor ready");
        } else {
            Log.w(TAG, "TFLite predictor not ready, will use fallback");
        }

        setupBottomNav();
        loadMeters();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Clean up TFLite resources
        if (predictor != null) {
            predictor.close();
        }
    }

    private void loadMeters() {
        long userId = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                .getLong("user_id", -1);

        if (userId <= 0) {
            Toast.makeText(this, "No logged-in user_id in prefs", Toast.LENGTH_SHORT).show();
            return;
        }

        Log.d(TAG, "Loading meters for user_id: " + userId);

        api.getMeters(userId).enqueue(new Callback<MeterListResponse>() {
            @Override
            public void onResponse(Call<MeterListResponse> call,
                                   Response<MeterListResponse> response) {

                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(MyMetersActivity.this,
                            "Server error: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "getMeters failed: " + response.code());
                    return;
                }

                MeterListResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) {
                    Toast.makeText(MyMetersActivity.this,
                            "API status: " + body.getStatus(),
                            Toast.LENGTH_SHORT).show();
                    Log.e(TAG, "getMeters API error: " + body.getStatus());
                    return;
                }

                if (body.getMeters() == null || body.getMeters().isEmpty()) {
                    Toast.makeText(MyMetersActivity.this,
                            "No meters found for this user.",
                            Toast.LENGTH_SHORT).show();
                    containerMeters.removeAllViews();
                    return;
                }

                Log.d(TAG, "Loaded " + body.getMeters().size() + " meters");
                containerMeters.removeAllViews();
                for (MeterInfo m : body.getMeters()) {
                    addMeterCardFromApi(m);
                }
            }

            @Override
            public void onFailure(Call<MeterListResponse> call, Throwable t) {
                Toast.makeText(MyMetersActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
                Log.e(TAG, "getMeters network error", t);
            }
        });
    }

    private void addMeterCardFromApi(MeterInfo meter) {
        LinearLayout card = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.item_meter_card, containerMeters, false);

        ((TextView) card.findViewById(R.id.tvMeterName))
                .setText("Meter #" + meter.getMeter_number());
        ((TextView) card.findViewById(R.id.tvMeterStatus))
                .setText(meter.getStatus());
        ((TextView) card.findViewById(R.id.tvLastReading))
                .setText(String.format(Locale.getDefault(), "%.2f kWh",
                        meter.getLast_reading_kwh()));
        ((TextView) card.findViewById(R.id.tvBillAmount))
                .setText(meter.getBill_amount() == null
                        ? "—"
                        : String.format(Locale.getDefault(), "₱%.2f", meter.getBill_amount()));

        TextView tvPred = card.findViewById(R.id.tvPredictedConsumption);
        tvPred.setText("Predicting...");

        // View History button
        Button btnViewHistory = card.findViewById(R.id.btnViewHistory);
        btnViewHistory.setOnClickListener(v -> {
            Intent i = new Intent(MyMetersActivity.this, MeterHistoryActivity.class);
            i.putExtra("meter_id", meter.getId());
            i.putExtra("meter_number", meter.getMeter_number());
            startActivity(i);
        });

        containerMeters.addView(card);

        // Fetch history and run prediction
        long meterId = meter.getId();
        Log.d(TAG, "Fetching history for meter_id: " + meterId);

        api.getMeterHistory(meterId, 60).enqueue(new Callback<MeterHistoryResponse>() {
            @Override
            public void onResponse(Call<MeterHistoryResponse> call,
                                   Response<MeterHistoryResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    tvPred.setText("Prediction unavailable");
                    Log.e(TAG, "getMeterHistory failed: " + response.code());
                    return;
                }

                MeterHistoryResponse hist = response.body();
                if (!"success".equalsIgnoreCase(hist.getStatus())) {
                    tvPred.setText("API error");
                    Log.e(TAG, "getMeterHistory API error: " + hist.getStatus());
                    return;
                }

                if (hist.getValues() == null || hist.getValues().isEmpty()) {
                    tvPred.setText("No data");
                    Log.w(TAG, "No history data for meter " + meterId);
                    return;
                }

                Log.d(TAG, "Got " + hist.getValues().size() + " readings for meter " + meterId);

                // Try TFLite prediction first
                float predictedKwh = runTFLitePrediction(hist.getValues());

                if (predictedKwh < 0) {
                    // Fallback to simple average if model fails
                    Log.w(TAG, "TFLite prediction failed, using fallback");
                    predictedKwh = runSimpleAveragePrediction(hist.getValues());
                    tvPred.setText(String.format(Locale.getDefault(),
                            "Predicted (avg): %.2f kWh", predictedKwh));
                } else {
                    Log.i(TAG, "TFLite prediction: " + predictedKwh + " kWh");
                    tvPred.setText(String.format(Locale.getDefault(),
                            "Predicted: %.2f kWh", predictedKwh));
                }
            }

            @Override
            public void onFailure(Call<MeterHistoryResponse> call, Throwable t) {
                tvPred.setText("Network error");
                Log.e(TAG, "getMeterHistory network error", t);
            }
        });
    }

    /**
     * Run prediction using TensorFlow Lite model
     */
    private float runTFLitePrediction(List<MeterReading> readings) {
        if (predictor == null || !predictor.isReady()) {
            Log.w(TAG, "TFLite predictor not ready");
            return -1f;
        }

        if (readings.size() < predictor.getRequiredSequenceLength()) {
            Log.w(TAG, "Not enough readings for TFLite: need " +
                    predictor.getRequiredSequenceLength() + ", got " + readings.size());
            return -1f;
        }

        return predictor.predict(readings);
    }

    /**
     * Fallback: simple baseline prediction as average of past readings
     */
    private float runSimpleAveragePrediction(List<MeterReading> readings) {
        if (readings == null || readings.isEmpty()) {
            return 0f;
        }

        float sum = 0f;
        for (MeterReading r : readings) {
            sum += r.getKwh();
        }
        return sum / readings.size();
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
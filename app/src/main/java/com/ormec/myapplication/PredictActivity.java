package com.ormec.myapplication;

import android.os.Bundle;
import android.util.Log;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import org.tensorflow.lite.Interpreter;

import android.content.res.AssetFileDescriptor;
import android.content.SharedPreferences;

import com.ormec.myapplication.models.MeterHistoryResponse;
import com.ormec.myapplication.models.MeterListResponse;
import com.ormec.myapplication.models.MeterReading;


import java.io.FileInputStream;
import java.io.IOException;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;
import java.util.List;
import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class PredictActivity extends AppCompatActivity {

    private static final String TAG = "PredictActivity";
    private static final int SEQ_LENGTH = 60;

    // Replace these with real values from scaler_info.json or your training script
    private static final float TRAIN_MIN_KWH = 5.0f;   // example - UPDATE THIS!
    private static final float TRAIN_MAX_KWH = 70.0f;  // example - UPDATE THIS!

    private EditText etRate;
    private Button btnPredict;
    private TextView tvResult;

    private Interpreter tflite;
    private ApiServices api;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_predict);

        etRate = findViewById(R.id.etRate);
        btnPredict = findViewById(R.id.btnPredict);
        tvResult = findViewById(R.id.tvResult);

        api = RetrofitClient.getApiService();

        try {
            tflite = new Interpreter(loadModelFile("consumption_model.tflite"));
            Log.i(TAG, "TFLite model loaded successfully");
        } catch (IOException e) {
            Log.e(TAG, "Error loading model", e);
            tvResult.setText("Error loading model: " + e.getMessage());
        }

        btnPredict.setOnClickListener(v -> startPredictionFlow());
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (tflite != null) {
            tflite.close();
        }
    }

    private MappedByteBuffer loadModelFile(String modelFile) throws IOException {
        AssetFileDescriptor fileDescriptor = getAssets().openFd(modelFile);
        FileInputStream inputStream = new FileInputStream(fileDescriptor.getFileDescriptor());
        FileChannel fileChannel = inputStream.getChannel();
        long startOffset = fileDescriptor.getStartOffset();
        long declaredLength = fileDescriptor.getDeclaredLength();
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength);
    }

    private void startPredictionFlow() {
        String rateStr = etRate.getText().toString().trim();
        if (rateStr.isEmpty()) {
            tvResult.setText("Please enter rate.");
            return;
        }

        float rate;
        try {
            rate = Float.parseFloat(rateStr);
        } catch (NumberFormatException e) {
            tvResult.setText("Invalid rate.");
            return;
        }

        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        long userId = prefs.getLong("user_id", -1);

        if (userId <= 0) {
            tvResult.setText("No logged-in user_id.");
            return;
        }

        tvResult.setText("Loading meters...");

        // FIXED: First get the user's meters, then use the first meter's ID
        api.getMeters(userId).enqueue(new Callback<MeterListResponse>() {
            @Override
            public void onResponse(Call<MeterListResponse> call, Response<MeterListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    tvResult.setText("Server error: " + response.code());
                    Log.e(TAG, "getMeters failed: " + response.code());
                    return;
                }

                MeterListResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus()) ||
                        body.getMeters() == null ||
                        body.getMeters().isEmpty()) {
                    tvResult.setText("No meters found for this user.");
                    return;
                }

                // Use the first meter
                long meterId = body.getMeters().get(0).getId();
                String meterNumber = body.getMeters().get(0).getMeter_number();

                Log.d(TAG, "Using meter: " + meterNumber + " (ID: " + meterId + ")");
                tvResult.setText("Loading history for Meter #" + meterNumber + "...");

                // Now get the meter history with the correct meter_id
                loadHistoryAndPredict(meterId, rate);
            }

            @Override
            public void onFailure(Call<MeterListResponse> call, Throwable t) {
                tvResult.setText("Network error: " + t.getMessage());
                Log.e(TAG, "getMeters network error", t);
            }
        });
    }

    private void loadHistoryAndPredict(long meterId, float rate) {
        // FIXED: Now correctly using meter_id instead of user_id
        Call<MeterHistoryResponse> call = api.getMeterHistory(meterId, SEQ_LENGTH);
        call.enqueue(new Callback<MeterHistoryResponse>() {
            @Override
            public void onResponse(Call<MeterHistoryResponse> call, Response<MeterHistoryResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    tvResult.setText("Server error: " + response.code());
                    Log.e(TAG, "getMeterHistory failed: " + response.code());
                    return;
                }

                MeterHistoryResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus()) || body.getValues() == null) {
                    tvResult.setText("Failed to get meter history.");
                    Log.e(TAG, "getMeterHistory API error: " + body.getStatus());
                    return;
                }

                List<MeterReading> readings = body.getValues();
                if (readings.size() < SEQ_LENGTH) {
                    tvResult.setText("Not enough data: need " + SEQ_LENGTH + " readings, got " + readings.size());
                    Log.w(TAG, "Insufficient readings: " + readings.size() + "/" + SEQ_LENGTH);
                    return;
                }

                Log.d(TAG, "Got " + readings.size() + " readings, running prediction");
                runPrediction(readings, rate);
            }

            @Override
            public void onFailure(Call<MeterHistoryResponse> call, Throwable t) {
                tvResult.setText("Network error: " + t.getMessage());
                Log.e(TAG, "getMeterHistory network error", t);
            }
        });
    }

    private void runPrediction(List<MeterReading> readings, float rate) {
        try {
            // Input shape: [1, 60, 1]
            float[][][] input = new float[1][SEQ_LENGTH][1];

            // Take last 60 readings
            int startIdx = readings.size() - SEQ_LENGTH;
            for (int i = 0; i < SEQ_LENGTH; i++) {
                float rawKwh = readings.get(startIdx + i).getKwh();

                // Scale using MinMaxScaler formula
                float scaled = (rawKwh - TRAIN_MIN_KWH) / (TRAIN_MAX_KWH - TRAIN_MIN_KWH);
                input[0][i][0] = scaled;
            }

            // Output shape: [1, 1]
            float[][] output = new float[1][1];
            tflite.run(input, output);

            float predScaled = output[0][0];

            // Inverse scale back to kWh
            float pkwh = predScaled * (TRAIN_MAX_KWH - TRAIN_MIN_KWH) + TRAIN_MIN_KWH;
            float bill = pkwh * rate;

            Log.i(TAG, "Prediction successful: " + pkwh + " kWh, bill: ₱" + bill);

            tvResult.setText(
                    "Predicted next month consumption: " +
                            String.format(Locale.getDefault(), "%.2f", pkwh) + " kWh\n" +
                            "Estimated bill: ₱" +
                            String.format(Locale.getDefault(), "%.2f", bill)
            );

        } catch (Exception ex) {
            tvResult.setText("Error running prediction: " + ex.getMessage());
            Log.e(TAG, "Prediction error", ex);
        }
    }
}
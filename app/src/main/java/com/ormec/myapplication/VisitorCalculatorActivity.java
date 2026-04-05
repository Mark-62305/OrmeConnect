package com.ormec.myapplication;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ormec.myapplication.models.BillingRateItem;
import com.ormec.myapplication.models.BillingRatesResponse;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

/**
 * VisitorCalculatorActivity
 *
 * Simplified billing calculator for visitors (without bottom navigation).
 * Allows visitors to estimate electricity bills based on consumption and consumer type.
 */
public class VisitorCalculatorActivity extends AppCompatActivity {

    private RadioGroup rgTypes;
    private RadioButton rbResidential, rbCommercial, rbIndustrial, rbPublicBuilding, rbStreetLight;
    private EditText etConsumption;
    private TextView tvRate, tvResult, tvResultConsumption, tvResultRate;
    private Button btnCalculate;
    private CardView cvResult;

    // fallback fixed rates (PHP/kWh)
    private static final double RATE_RESIDENTIAL   = 15.7972;
    private static final double RATE_COMMERCIAL    = 14.6875;
    private static final double RATE_INDUSTRIAL    = 14.2968;
    private static final double RATE_PUBLIC_BUILD  = 14.4085;
    private static final double RATE_STREET_LIGHTS = 16.4986;

    private ApiServices api;
    private Float rateResidential, rateCommercial, rateIndustrial, ratePublic, rateStreet;
    private double currentRate = RATE_RESIDENTIAL;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_visitor_calculator);

        api = RetrofitClient.getApiService();

        // Initialize views
        rgTypes          = findViewById(R.id.rgTypes);
        rbResidential    = findViewById(R.id.rbResidential);
        rbCommercial     = findViewById(R.id.rbCommercial);
        rbIndustrial     = findViewById(R.id.rbIndustrial);
        rbPublicBuilding = findViewById(R.id.rbPublicBuilding);
        rbStreetLight    = findViewById(R.id.rbStreetLight);

        etConsumption       = findViewById(R.id.etConsumption);
        tvRate              = findViewById(R.id.tvRate);
        tvResult            = findViewById(R.id.tvResult);
        tvResultConsumption = findViewById(R.id.tvResultConsumption);
        tvResultRate        = findViewById(R.id.tvResultRate);
        btnCalculate        = findViewById(R.id.btnCalculate);
        cvResult            = findViewById(R.id.cvResult);

        loadRatesFromServer();
        updateRateLabel();

        // Radio button change listener
        rgTypes.setOnCheckedChangeListener((group, checkedId) -> {
            if (checkedId == R.id.rbResidential) {
                currentRate = rateResidential != null ? rateResidential : RATE_RESIDENTIAL;
            } else if (checkedId == R.id.rbCommercial) {
                currentRate = rateCommercial != null ? rateCommercial : RATE_COMMERCIAL;
            } else if (checkedId == R.id.rbIndustrial) {
                currentRate = rateIndustrial != null ? rateIndustrial : RATE_INDUSTRIAL;
            } else if (checkedId == R.id.rbPublicBuilding) {
                currentRate = ratePublic != null ? ratePublic : RATE_PUBLIC_BUILD;
            } else if (checkedId == R.id.rbStreetLight) {
                currentRate = rateStreet != null ? rateStreet : RATE_STREET_LIGHTS;
            }
            updateRateLabel();
        });

        btnCalculate.setOnClickListener(v -> calculateBill());
    }

    /**
     * Load billing rates from the server
     * Falls back to hard-coded rates if server request fails
     */
    private void loadRatesFromServer() {
        api.getBillingRates().enqueue(new Callback<BillingRatesResponse>() {
            @Override
            public void onResponse(Call<BillingRatesResponse> call,
                                   Response<BillingRatesResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                BillingRatesResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) return;

                for (BillingRateItem item : body.getRates()) {
                    switch (item.getType()) {
                        case "residential":
                            rateResidential = item.getRate_per_kwh();
                            break;
                        case "commercial":
                            rateCommercial = item.getRate_per_kwh();
                            break;
                        case "industrial":
                            rateIndustrial = item.getRate_per_kwh();
                            break;
                        case "public":
                            ratePublic = item.getRate_per_kwh();
                            break;
                        case "street_light":
                            rateStreet = item.getRate_per_kwh();
                            break;
                    }
                }

                // Update current rate if residential rate was loaded
                if (rateResidential != null) {
                    currentRate = rateResidential;
                    updateRateLabel();
                }
            }

            @Override
            public void onFailure(Call<BillingRatesResponse> call, Throwable t) {
                // silently fall back to hard-coded rates
                Toast.makeText(VisitorCalculatorActivity.this,
                        "Using default rates", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Update the rate label to display the current rate
     */
    private void updateRateLabel() {
        tvRate.setText(String.format("₱%.4f / kWh", currentRate));
    }

    /**
     * Calculate the estimated electricity bill based on consumption
     */
    private void calculateBill() {
        String consStr = etConsumption.getText().toString().trim();

        if (consStr.isEmpty()) {
            Toast.makeText(this, "Please enter consumption value", Toast.LENGTH_SHORT).show();
            cvResult.setVisibility(View.GONE);
            return;
        }

        try {
            double kwh = Double.parseDouble(consStr);

            if (kwh <= 0) {
                Toast.makeText(this, "Please enter a valid positive number", Toast.LENGTH_SHORT).show();
                cvResult.setVisibility(View.GONE);
                return;
            }

            double bill = kwh * currentRate;

            // Update result card
            tvResultConsumption.setText(String.format("%.2f kWh", kwh));
            tvResultRate.setText(String.format("₱%.4f/kWh", currentRate));
            tvResult.setText(String.format("₱%.2f", bill));

            // Show result card with animation
            cvResult.setVisibility(View.VISIBLE);
            cvResult.setAlpha(0f);
            cvResult.animate()
                    .alpha(1f)
                    .setDuration(300)
                    .start();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            cvResult.setVisibility(View.GONE);
        }
    }
}
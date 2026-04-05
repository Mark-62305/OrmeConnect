/* =========================================================
 * file: app/src/main/java/com/ormec/myapplication/BillingCalculatorActivity.java
 * Behavior:
 *  - NO fixed/fallback rates
 *  - Disable Calculate until DB rates are loaded
 *  - If API fails or rates missing, block calculation and show message
 *  - Long-press Calculate = force reload rates from DB
 * ========================================================= */
package com.ormec.myapplication;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

import com.ormec.myapplication.models.BillingRateItem;
import com.ormec.myapplication.models.BillingRatesResponse;

import java.util.Locale;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BillingCalculatorActivity extends AppCompatActivity {

    private RadioGroup rgTypes;
    private RadioButton rbResidential, rbCommercial, rbIndustrial, rbPublicBuilding, rbStreetLight;
    private EditText etConsumption;
    private TextView tvRate, tvResult, tvResultConsumption, tvResultRate;
    private Button btnCalculate;
    private CardView cvResult;

    private ApiServices api;

    private Double rateResidential;
    private Double rateCommercial;
    private Double rateIndustrial;
    private Double ratePublic;
    private Double rateStreet;

    private Double currentRate;

    private boolean ratesLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_billing_calculator);

        api = RetrofitClient.getApiService();

        rgTypes = findViewById(R.id.rgTypes);
        rbResidential = findViewById(R.id.rbResidential);
        rbCommercial = findViewById(R.id.rbCommercial);
        rbIndustrial = findViewById(R.id.rbIndustrial);
        rbPublicBuilding = findViewById(R.id.rbPublicBuilding);
        rbStreetLight = findViewById(R.id.rbStreetLight);

        etConsumption = findViewById(R.id.etConsumption);
        tvRate = findViewById(R.id.tvRate);
        tvResult = findViewById(R.id.tvResult);
        tvResultConsumption = findViewById(R.id.tvResultConsumption);
        tvResultRate = findViewById(R.id.tvResultRate);
        btnCalculate = findViewById(R.id.btnCalculate);
        cvResult = findViewById(R.id.cvResult);

        setupBottomNav();

        setRatesLoadingState();

        // Load DB rates immediately (required)
        loadRatesFromServer(false);

        rgTypes.setOnCheckedChangeListener((group, checkedId) -> {
            if (!ratesLoaded) {
                Toast.makeText(this, "Rates not loaded yet. Check DB/API.", Toast.LENGTH_SHORT).show();
                return;
            }
            currentRate = getRateForSelectedType(checkedId);
            updateRateLabel();
        });

        btnCalculate.setOnClickListener(v -> calculateBill());

        // Long-press = force reload from DB
        btnCalculate.setOnLongClickListener(v -> {
            loadRatesFromServer(true);
            return true;
        });
    }

    private void setRatesLoadingState() {
        ratesLoaded = false;
        btnCalculate.setEnabled(false);
        tvRate.setText("Loading rates from database...");
        cvResult.setVisibility(View.GONE);

        rateResidential = null;
        rateCommercial = null;
        rateIndustrial = null;
        ratePublic = null;
        rateStreet = null;
        currentRate = null;
    }

    private void setRatesErrorState(String message) {
        ratesLoaded = false;
        btnCalculate.setEnabled(false);
        cvResult.setVisibility(View.GONE);
        tvRate.setText("Rates unavailable (DB/API).");
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void setRatesReadyState() {
        ratesLoaded = true;
        btnCalculate.setEnabled(true);

        // Ensure currentRate matches current selected radio
        int checkedId = rgTypes.getCheckedRadioButtonId();
        if (checkedId == -1) {
            rbResidential.setChecked(true);
            checkedId = R.id.rbResidential;
        }

        currentRate = getRateForSelectedType(checkedId);
        updateRateLabel();
    }

    private Double getRateForSelectedType(int checkedId) {
        if (checkedId == R.id.rbResidential) return rateResidential;
        if (checkedId == R.id.rbCommercial) return rateCommercial;
        if (checkedId == R.id.rbIndustrial) return rateIndustrial;
        if (checkedId == R.id.rbPublicBuilding) return ratePublic;
        if (checkedId == R.id.rbStreetLight) return rateStreet;
        return null;
    }

    private boolean hasAllRequiredRates() {
        return rateResidential != null
                && rateCommercial != null
                && rateIndustrial != null
                && ratePublic != null
                && rateStreet != null;
    }

    private void loadRatesFromServer(boolean showToastOnSuccess) {
        setRatesLoadingState();

        api.getBillingRates().enqueue(new Callback<BillingRatesResponse>() {
            @Override
            public void onResponse(Call<BillingRatesResponse> call, Response<BillingRatesResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    setRatesErrorState("Failed to load rates: HTTP " + response.code());
                    return;
                }

                BillingRatesResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) {
                    String msg = body.getMessage() != null ? body.getMessage() : "Server returned fail";
                    setRatesErrorState("Failed to load rates: " + msg);
                    return;
                }

                // Debug proof in Logcat (if backend provides db fingerprint)
                if (body.getDb() != null) {
                    Log.d("BILLING_DB",
                            "db=" + body.getDb().getDb_name()
                                    + "@" + body.getDb().getMysql_host()
                                    + ":" + body.getDb().getMysql_port());
                }

                applyRates(body);

                if (!hasAllRequiredRates()) {
                    setRatesErrorState("Rates loaded but incomplete. Check DB descriptions/types mapping.");
                    return;
                }

                setRatesReadyState();

                if (showToastOnSuccess) {
                    Toast.makeText(BillingCalculatorActivity.this,
                            "Rates loaded from DB successfully.",
                            Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<BillingRatesResponse> call, Throwable t) {
                setRatesErrorState("Failed to load rates: " + (t.getMessage() == null ? "Network error" : t.getMessage()));
            }
        });
    }

    private void applyRates(BillingRatesResponse body) {
        rateResidential = null;
        rateCommercial = null;
        rateIndustrial = null;
        ratePublic = null;
        rateStreet = null;

        if (body.getRates() == null) return;

        for (BillingRateItem item : body.getRates()) {
            if (item == null || item.getType() == null) continue;

            String type = item.getType().trim().toLowerCase(Locale.US);
            Double rate = item.getRate_per_kwh() == null ? null : item.getRate_per_kwh().doubleValue();
            if (rate == null) continue;

            switch (type) {
                case "residential":
                    rateResidential = rate;
                    break;
                case "commercial":
                    rateCommercial = rate;
                    break;
                case "industrial":
                    rateIndustrial = rate;
                    break;
                case "public":
                case "public_building":
                    ratePublic = rate;
                    break;
                case "street_light":
                case "street_lights":
                case "streetlight":
                    rateStreet = rate;
                    break;
                default:
                    Log.w("BILLING_RATE", "Unknown type from DB: " + type);
            }
        }
    }

    private void updateRateLabel() {
        if (!ratesLoaded || currentRate == null) {
            tvRate.setText("Rates unavailable.");
            return;
        }
        tvRate.setText(String.format(Locale.US, "₱%.4f / kWh", currentRate));
    }

    private void calculateBill() {
        if (!ratesLoaded || currentRate == null) {
            Toast.makeText(this, "Cannot calculate: rates not loaded from DB.", Toast.LENGTH_SHORT).show();
            cvResult.setVisibility(View.GONE);
            return;
        }

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

            tvResultConsumption.setText(String.format(Locale.US, "%.2f kWh", kwh));
            tvResultRate.setText(String.format(Locale.US, "₱%.4f/kWh", currentRate));
            tvResult.setText(String.format(Locale.US, "₱%.2f", bill));

            cvResult.setVisibility(View.VISIBLE);
            cvResult.setAlpha(0f);
            cvResult.animate().alpha(1f).setDuration(300).start();

        } catch (NumberFormatException e) {
            Toast.makeText(this, "Invalid number format", Toast.LENGTH_SHORT).show();
            cvResult.setVisibility(View.GONE);
        }
    }

    private void setupBottomNav() {
        LinearLayout navHome = findViewById(R.id.navHome);
        LinearLayout navIncident = findViewById(R.id.navIncident);
        LinearLayout navCalculator = findViewById(R.id.navCalculator);
        LinearLayout navProfile = findViewById(R.id.navProfile);
        LinearLayout navLogout = findViewById(R.id.navLogout);

        TextView tvHome = findViewById(R.id.tvNavHome);
        TextView tvIncident = findViewById(R.id.tvNavIncident);
        TextView tvCalculator = findViewById(R.id.tvNavCalculator);
        TextView tvProfile = findViewById(R.id.tvNavProfile);
        TextView tvLogout = findViewById(R.id.tvNavLogout);

        int grey = Color.parseColor("#B0B0B0");
        tvHome.setTextColor(grey);
        tvIncident.setTextColor(grey);
        tvProfile.setTextColor(grey);
        tvCalculator.setTextColor(Color.parseColor("#16A34A"));

        navHome.setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)));
        navIncident.setOnClickListener(v -> startActivity(new Intent(this, IncidentReportsActivity.class)));
        navCalculator.setOnClickListener(v -> {});
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        navLogout.setOnClickListener(v -> {
            getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }
}

// app/src/main/java/com/ormec/myapplication/BenefitsActivity.java
package com.ormec.myapplication;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.ormec.myapplication.models.BenefitHistoryItem;
import com.ormec.myapplication.models.BenefitItem;
import com.ormec.myapplication.models.BenefitListResponse;
import com.ormec.myapplication.models.BenefitStatusResponse;
import com.ormec.myapplication.models.SimpleResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BenefitsActivity extends AppCompatActivity {

    private static final String TAG = "BenefitsActivity";
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L; // 10 MB total per upload

    private ApiServices api;
    private LinearLayout containerBenefits;
    private LinearLayout containerHistory;
    private TextView tvApplicationStatus;

    // Upload UI
    private Button btnPickFiles;
    private Button btnClearFiles;
    private TextView tvSelectedFiles;
    private TextView tvTotalSize;

    private final List<Uri> selectedUris = new ArrayList<>();
    private int pendingBenefitId = -1; // Store which benefit is being applied for

    private final ActivityResultLauncher<String[]> pickDocumentsLauncher =
            registerForActivityResult(new ActivityResultContracts.OpenMultipleDocuments(), uris -> {
                if (uris == null || uris.isEmpty()) return;

                long total = 0L;
                for (Uri u : uris) {
                    long size = getUriSizeBytes(u);
                    if (size <= 0L) {
                        Toast.makeText(this, "Couldn't read file size for one or more files.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    total += size;
                    if (total > MAX_UPLOAD_BYTES) {
                        Toast.makeText(this, "Upload limit is 10 MB total. Please choose smaller files.", Toast.LENGTH_LONG).show();
                        return;
                    }
                }

                selectedUris.clear();
                selectedUris.addAll(uris);
                renderSelectedFiles();
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benefits);

        api = RetrofitClient.getApiService();

        containerBenefits = findViewById(R.id.containerBenefits);
        containerHistory = findViewById(R.id.containerBenefitHistory);
        tvApplicationStatus = findViewById(R.id.tvApplicationStatus);

        // Upload views
        btnPickFiles = findViewById(R.id.btnPickFiles);
        btnClearFiles = findViewById(R.id.btnClearFiles);
        tvSelectedFiles = findViewById(R.id.tvSelectedFiles);
        tvTotalSize = findViewById(R.id.tvTotalSize);

        btnPickFiles.setOnClickListener(v -> pickDocumentsLauncher.launch(new String[]{"*/*"}));
        btnClearFiles.setOnClickListener(v -> {
            selectedUris.clear();
            renderSelectedFiles();
        });

        renderSelectedFiles();

        loadBenefits();
        loadBenefitStatus();
        setupBottomNav();
    }

    private long getUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getLong("user_id", -1);
    }

    private void loadBenefits() {
        api.getBenefits().enqueue(new Callback<BenefitListResponse>() {
            @Override
            public void onResponse(Call<BenefitListResponse> call, Response<BenefitListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                BenefitListResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) return;

                containerBenefits.removeAllViews();
                for (BenefitItem item : body.getBenefits()) {
                    addBenefitCard(item);
                }
            }

            @Override
            public void onFailure(Call<BenefitListResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load benefits", t);
            }
        });
    }

    private void addBenefitCard(BenefitItem item) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(24, 24, 24, 24);

        TextView tvName = new TextView(this);
        tvName.setText(item.getName());
        tvName.setTextSize(16f);
        tvName.setTextColor(Color.BLACK);

        TextView tvDesc = new TextView(this);
        tvDesc.setText(item.getDescription());
        tvDesc.setTextColor(Color.DKGRAY);

        Button btnApply = new Button(this);
        btnApply.setText("Apply Now");
        btnApply.setOnClickListener(v -> applyForBenefit(item.getId()));

        card.addView(tvName);
        card.addView(tvDesc);
        card.addView(btnApply);

        containerBenefits.addView(card);
    }

    private void applyForBenefit(int benefitId) {
        long userId = getUserId();
        if (userId <= 0) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }

        pendingBenefitId = benefitId;

        // Check if files are selected
        if (selectedUris.isEmpty()) {
            // No files - apply without documents
            applyWithoutFiles(userId, benefitId);
        } else {
            // Files selected - apply with documents
            applyWithFiles(userId, benefitId);
        }
    }

    /**
     * Apply for benefit without documents
     */
    private void applyWithoutFiles(long userId, int benefitId) {
        api.applyBenefit(userId, benefitId).enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                if (response.isSuccessful() && response.body() != null &&
                        "success".equalsIgnoreCase(response.body().getStatus())) {
                    Toast.makeText(BenefitsActivity.this, "Application submitted successfully!", Toast.LENGTH_SHORT).show();
                    loadBenefitStatus();
                } else {
                    Toast.makeText(BenefitsActivity.this, "Application failed", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SimpleResponse> call, Throwable t) {
                Log.e(TAG, "Failed to apply for benefit", t);
                Toast.makeText(BenefitsActivity.this, "Network error", Toast.LENGTH_SHORT).show();
            }
        });
    }

    /**
     * Apply for benefit with uploaded documents
     */
    private void applyWithFiles(long userId, int benefitId) {
        // Validate total file size
        long total = 0L;
        for (Uri u : selectedUris) {
            long size = getUriSizeBytes(u);
            if (size <= 0L) {
                Toast.makeText(this, "Couldn't read file size.", Toast.LENGTH_SHORT).show();
                return;
            }
            total += size;
        }

        if (total > MAX_UPLOAD_BYTES) {
            Toast.makeText(this, "Total file size exceeds 10 MB limit.", Toast.LENGTH_LONG).show();
            return;
        }

        // Prepare multipart file parts
        List<MultipartBody.Part> fileParts = new ArrayList<>();

        for (int i = 0; i < selectedUris.size(); i++) {
            Uri uri = selectedUris.get(i);
            String fileName = getUriDisplayName(uri);
            if (fileName == null) fileName = "document_" + (i + 1);
            String mime = resolveMimeType(uri);
            if (mime == null) mime = "application/octet-stream";

            byte[] bytes;
            try {
                bytes = readAllBytes(uri, MAX_UPLOAD_BYTES);
            } catch (IOException e) {
                Toast.makeText(this, "Failed to read file: " + fileName, Toast.LENGTH_SHORT).show();
                Log.e(TAG, "readAllBytes failed", e);
                return;
            }

            RequestBody fileBody = RequestBody.create(bytes, MediaType.parse(mime));
            MultipartBody.Part part = MultipartBody.Part.createFormData("files[]", fileName, fileBody);
            fileParts.add(part);
        }

        // Disable button during upload
        btnPickFiles.setEnabled(false);
        btnClearFiles.setEnabled(false);

        // Make API call with simple parameters
        api.applyBenefitWithFiles(userId, benefitId, fileParts).enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                btnPickFiles.setEnabled(true);
                btnClearFiles.setEnabled(true);

                if (response.isSuccessful() && response.body() != null &&
                        "success".equalsIgnoreCase(response.body().getStatus())) {
                    Toast.makeText(BenefitsActivity.this, "Application with documents submitted successfully!", Toast.LENGTH_SHORT).show();
                    selectedUris.clear();
                    renderSelectedFiles();
                    loadBenefitStatus();
                } else {
                    String message = response.body() != null ? response.body().getMessage() : "Unknown error";
                    Toast.makeText(BenefitsActivity.this, "Application failed: " + message, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SimpleResponse> call, Throwable t) {
                btnPickFiles.setEnabled(true);
                btnClearFiles.setEnabled(true);
                Log.e(TAG, "Failed to upload files with benefit application", t);
                Toast.makeText(BenefitsActivity.this, "Upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void loadBenefitStatus() {
        long userId = getUserId();
        if (userId <= 0) return;

        api.getBenefitStatus(userId).enqueue(new Callback<BenefitStatusResponse>() {
            @Override
            public void onResponse(Call<BenefitStatusResponse> call, Response<BenefitStatusResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;

                BenefitStatusResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) return;

                String status = body.getCurrent_application_status();
                tvApplicationStatus.setText(status == null ? "No active application" : status);

                containerHistory.removeAllViews();
                List<BenefitHistoryItem> history = body.getHistory();
                if (history != null) {
                    for (BenefitHistoryItem h : history) {
                        addHistoryItem(h);
                    }
                }
            }

            @Override
            public void onFailure(Call<BenefitStatusResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load benefit status", t);
            }
        });
    }

    private void addHistoryItem(BenefitHistoryItem item) {
        TextView tv = new TextView(this);
        String text = item.getBenefit_name() + " - " + item.getStatus() + " (" + item.getDate() + ")";
        tv.setText(text);
        tv.setPadding(16, 16, 16, 16);
        containerHistory.addView(tv);
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
        tvCalculator.setTextColor(grey);
        tvProfile.setTextColor(grey);

        navHome.setOnClickListener(v -> startActivity(new Intent(this, DashboardActivity.class)));
        navIncident.setOnClickListener(v -> startActivity(new Intent(this, IncidentReportsActivity.class)));
        navCalculator.setOnClickListener(v -> startActivity(new Intent(this, BillingCalculatorActivity.class)));
        navProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));

        navLogout.setOnClickListener(v -> {
            getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

    private void renderSelectedFiles() {
        if (selectedUris.isEmpty()) {
            tvSelectedFiles.setText("No files selected");
            tvTotalSize.setText("Total: 0 B / 10 MB");
            btnClearFiles.setEnabled(false);
            return;
        }

        StringBuilder sb = new StringBuilder();
        long total = 0L;
        for (Uri u : selectedUris) {
            String name = getUriDisplayName(u);
            long size = getUriSizeBytes(u);
            total += Math.max(size, 0L);
            sb.append("• ").append(name == null ? u.toString() : name)
                    .append(" (").append(formatBytes(size)).append(")")
                    .append("\n");
        }

        tvSelectedFiles.setText(sb.toString().trim());
        tvTotalSize.setText(String.format(Locale.US, "Total: %s / 10 MB", formatBytes(total)));
        btnClearFiles.setEnabled(true);
    }

    private String getUriDisplayName(Uri uri) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private long getUriSizeBytes(Uri uri) {
        ContentResolver resolver = getContentResolver();
        try (Cursor cursor = resolver.query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) { }

        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) return -1L;
            long count = 0L;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                count += n;
                if (count > MAX_UPLOAD_BYTES) return count;
            }
            return count;
        } catch (Exception e) {
            return -1L;
        }
    }

    private String resolveMimeType(Uri uri) {
        ContentResolver resolver = getContentResolver();
        String type = resolver.getType(uri);
        if (type != null) return type;

        String name = getUriDisplayName(uri);
        if (name == null) return null;

        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;

        String ext = name.substring(dot + 1).toLowerCase(Locale.US);
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
    }

    private byte[] readAllBytes(Uri uri, long limitBytes) throws IOException {
        ContentResolver resolver = getContentResolver();
        try (InputStream in = resolver.openInputStream(uri)) {
            if (in == null) throw new IOException("openInputStream returned null");
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            long total = 0L;
            int n;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > limitBytes) throw new IOException("File(s) exceed upload limit");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        }
    }

    private String formatBytes(long bytes) {
        if (bytes < 0) return "Unknown";
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format(Locale.US, "%.1f KB", kb);
        double mb = kb / 1024.0;
        return String.format(Locale.US, "%.2f MB", mb);
    }
}
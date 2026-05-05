// app/src/main/java/com/ormec/myapplication/BenefitsActivity.java
package com.ormec.myapplication;

import android.content.ContentResolver;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Color;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.MimeTypeMap;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.chip.Chip;
import com.ormec.myapplication.models.BenefitHistoryItem;
import com.ormec.myapplication.models.BenefitItem;
import com.ormec.myapplication.models.BenefitListResponse;
import com.ormec.myapplication.models.BenefitStatusResponse;
import com.ormec.myapplication.models.SimpleResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class BenefitsActivity extends AppCompatActivity {

    private static final String TAG = "BenefitsActivity";
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L; // 10 MB

    private ApiServices api;
    private LinearLayout containerBenefits;
    private LinearLayout containerHistory;
    private LinearLayout containerProgressCards;
    private LinearLayout historyEmptyState;
    private Chip chipBenefitsCount;
    private Chip tvApplicationStatus;

    // Upload UI
    private MaterialButton btnPickFiles;
    private MaterialButton btnClearFiles;
    private TextView tvSelectedFiles;
    private TextView tvTotalSize;
    private final List<Uri> selectedUris = new ArrayList<>();

    // benefit_id → Apply button (so we can disable after status loads)
    private final Map<Integer, MaterialButton> benefitButtonMap = new HashMap<>();

    // Lowercase benefit names that are already pending/review/processing/approved.
    // We match by name because BenefitHistoryItem only exposes benefit_name, not benefit_id.
    private final Set<String> appliedBenefitNames = new HashSet<>();

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

    // ─────────────────────────────────────────────
    // Lifecycle
    // ─────────────────────────────────────────────

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_benefits);

        api = RetrofitClient.getApiService();

        containerBenefits      = findViewById(R.id.containerBenefits);
        containerHistory       = findViewById(R.id.containerBenefitHistory);
        containerProgressCards = findViewById(R.id.containerProgressCards);
        historyEmptyState      = findViewById(R.id.historyEmptyState);
        chipBenefitsCount      = findViewById(R.id.chipBenefitsCount);
        tvApplicationStatus    = findViewById(R.id.tvApplicationStatus);

        btnPickFiles    = findViewById(R.id.btnPickFiles);
        btnClearFiles   = findViewById(R.id.btnClearFiles);
        tvSelectedFiles = findViewById(R.id.tvSelectedFiles);
        tvTotalSize     = findViewById(R.id.tvTotalSize);

        btnPickFiles.setOnClickListener(v -> pickDocumentsLauncher.launch(new String[]{"*/*"}));
        btnClearFiles.setOnClickListener(v -> { selectedUris.clear(); renderSelectedFiles(); });
        renderSelectedFiles();

        // Load status first — populates appliedBenefitNames before benefit cards render
        loadBenefitStatus();
        setupBottomNav();
    }

    // ─────────────────────────────────────────────
    // Auth
    // ─────────────────────────────────────────────

    private long getUserId() {
        return getSharedPreferences("MyAppPrefs", MODE_PRIVATE).getLong("user_id", -1);
    }

    // ─────────────────────────────────────────────
    // Load available benefit cards
    // ─────────────────────────────────────────────

    private void loadBenefits() {
        api.getBenefits().enqueue(new Callback<BenefitListResponse>() {
            @Override
            public void onResponse(Call<BenefitListResponse> call,
                                   Response<BenefitListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                BenefitListResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) return;

                List<BenefitItem> benefits = body.getBenefits();
                chipBenefitsCount.setText(benefits.size() + " available");
                containerBenefits.removeAllViews();
                benefitButtonMap.clear();
                for (BenefitItem item : benefits) {
                    addBenefitCard(item);
                }
            }

            @Override
            public void onFailure(Call<BenefitListResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load benefits", t);
            }
        });
    }

    // ─────────────────────────────────────────────
    // Benefit card (redesigned)
    // ─────────────────────────────────────────────

    private void addBenefitCard(BenefitItem item) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(Color.parseColor("#E8F5E9"));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(18), dpToPx(18), dpToPx(18), dpToPx(18));

        // ── Icon bubble + name/description ──
        LinearLayout topRow = new LinearLayout(this);
        topRow.setOrientation(LinearLayout.HORIZONTAL);
        topRow.setGravity(Gravity.CENTER_VERTICAL);

        FrameLayout iconBubble = new FrameLayout(this);
        int bubbleSize = dpToPx(48);
        LinearLayout.LayoutParams bubbleLp = new LinearLayout.LayoutParams(bubbleSize, bubbleSize);
        bubbleLp.setMarginEnd(dpToPx(14));
        iconBubble.setLayoutParams(bubbleLp);
        iconBubble.setBackground(roundedBg("#E8F5E9", 24));

        ImageView icon = new ImageView(this);
        FrameLayout.LayoutParams iconLp = new FrameLayout.LayoutParams(dpToPx(24), dpToPx(24));
        iconLp.gravity = Gravity.CENTER;
        icon.setLayoutParams(iconLp);
        icon.setImageResource(R.drawable.ic_info_outline);
        icon.setColorFilter(Color.parseColor("#2E7D32"));
        iconBubble.addView(icon);

        LinearLayout textCol = new LinearLayout(this);
        textCol.setOrientation(LinearLayout.VERTICAL);
        textCol.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(item.getName());
        tvName.setTextSize(15f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#1B5E20"));

        TextView tvDesc = new TextView(this);
        tvDesc.setText(item.getDescription());
        tvDesc.setTextSize(12.5f);
        tvDesc.setTextColor(Color.parseColor("#6D8B74"));
        tvDesc.setLineSpacing(dpToPx(2), 1f);
        LinearLayout.LayoutParams descLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        descLp.topMargin = dpToPx(3);
        tvDesc.setLayoutParams(descLp);

        textCol.addView(tvName);
        textCol.addView(tvDesc);
        topRow.addView(iconBubble);
        topRow.addView(textCol);

        // ── Divider ──
        View divider = new View(this);
        LinearLayout.LayoutParams divLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(1));
        divLp.topMargin    = dpToPx(14);
        divLp.bottomMargin = dpToPx(12);
        divider.setLayoutParams(divLp);
        divider.setBackgroundColor(Color.parseColor("#F1F8E9"));

        // ── Apply button ──
        // Disabled if this benefit name is already in appliedBenefitNames
        MaterialButton btnApply = new MaterialButton(this);
        btnApply.setLayoutParams(new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(44)));
        btnApply.setCornerRadius(dpToPx(10));
        btnApply.setTextSize(13.5f);

        boolean alreadyApplied = item.getName() != null
                && appliedBenefitNames.contains(item.getName().toLowerCase(Locale.US));
        styleApplyButton(btnApply, alreadyApplied);

        btnApply.setOnClickListener(v -> applyForBenefit(item.getId(), btnApply));
        benefitButtonMap.put(item.getId(), btnApply);

        inner.addView(topRow);
        inner.addView(divider);
        inner.addView(btnApply);
        card.addView(inner);
        containerBenefits.addView(card);
    }

    private void styleApplyButton(MaterialButton btn, boolean disabled) {
        btn.setEnabled(!disabled);
        if (disabled) {
            btn.setText("Already Applied");
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#C8E6C9")));
            btn.setTextColor(Color.parseColor("#81C784"));
        } else {
            btn.setText("Apply Now");
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(Color.parseColor("#2E7D32")));
            btn.setTextColor(Color.WHITE);
        }
    }

    // ─────────────────────────────────────────────
    // Apply (with or without files)
    // ─────────────────────────────────────────────

    private void applyForBenefit(int benefitId, MaterialButton btn) {
        long userId = getUserId();
        if (userId <= 0) {
            Toast.makeText(this, "Please log in first", Toast.LENGTH_SHORT).show();
            return;
        }
        btn.setEnabled(false);
        btn.setText("Submitting…");

        if (selectedUris.isEmpty()) {
            applyWithoutFiles(userId, benefitId, btn);
        } else {
            applyWithFiles(userId, benefitId, btn);
        }
    }

    private void applyWithoutFiles(long userId, int benefitId, MaterialButton btn) {
        api.applyBenefit(userId, benefitId).enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && "success".equalsIgnoreCase(response.body().getStatus())) {
                    Toast.makeText(BenefitsActivity.this,
                            "Application submitted!", Toast.LENGTH_SHORT).show();
                    styleApplyButton(btn, true);
                    loadBenefitStatus(); // refreshes history + disables matched buttons
                } else {
                    btn.setEnabled(true);
                    btn.setText("Apply Now");
                    Toast.makeText(BenefitsActivity.this,
                            "Application failed. Try again.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SimpleResponse> call, Throwable t) {
                btn.setEnabled(true);
                btn.setText("Apply Now");
                Log.e(TAG, "applyBenefit failed", t);
                Toast.makeText(BenefitsActivity.this,
                        "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void applyWithFiles(long userId, int benefitId, MaterialButton btn) {
        List<MultipartBody.Part> parts = new ArrayList<>();
        try {
            for (Uri uri : selectedUris) {
                byte[] bytes = readAllBytes(uri, MAX_UPLOAD_BYTES);
                String name  = getUriDisplayName(uri);
                if (name == null) name = "file";
                String mime  = resolveMimeType(uri);
                if (mime == null) mime = "application/octet-stream";
                RequestBody rb = RequestBody.create(bytes, MediaType.parse(mime));
                parts.add(MultipartBody.Part.createFormData("files[]", name, rb));
            }
        } catch (IOException e) {
            btn.setEnabled(true);
            btn.setText("Apply Now");
            Toast.makeText(this, "Failed to read files: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            return;
        }

        api.applyBenefitWithFiles(userId, benefitId, parts).enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(Call<SimpleResponse> call, Response<SimpleResponse> response) {
                if (response.isSuccessful() && response.body() != null
                        && "success".equalsIgnoreCase(response.body().getStatus())) {
                    Toast.makeText(BenefitsActivity.this,
                            "Application with documents submitted!", Toast.LENGTH_SHORT).show();
                    selectedUris.clear();
                    renderSelectedFiles();
                    styleApplyButton(btn, true);
                    loadBenefitStatus();
                } else {
                    btn.setEnabled(true);
                    btn.setText("Apply Now");
                    String msg = response.body() != null ? response.body().getMessage() : "Unknown error";
                    Toast.makeText(BenefitsActivity.this,
                            "Application failed: " + msg, Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<SimpleResponse> call, Throwable t) {
                btn.setEnabled(true);
                btn.setText("Apply Now");
                Log.e(TAG, "applyBenefitWithFiles failed", t);
                Toast.makeText(BenefitsActivity.this,
                        "Upload failed: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ─────────────────────────────────────────────
    // Load benefit status + history
    // ─────────────────────────────────────────────

    private void loadBenefitStatus() {
        long userId = getUserId();
        if (userId <= 0) { loadBenefits(); return; }

        api.getBenefitStatus(userId).enqueue(new Callback<BenefitStatusResponse>() {
            @Override
            public void onResponse(Call<BenefitStatusResponse> call,
                                   Response<BenefitStatusResponse> response) {
                if (!response.isSuccessful() || response.body() == null) { loadBenefits(); return; }
                BenefitStatusResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())) { loadBenefits(); return; }

                // Overall status chip
                String currentStatus = body.getCurrent_application_status();
                tvApplicationStatus.setText(
                        (currentStatus == null || currentStatus.isEmpty())
                                ? "No active application" : currentStatus);

                // ── Build the applied-names set BEFORE rendering benefit cards ──
                appliedBenefitNames.clear();
                List<BenefitHistoryItem> history = body.getHistory();

                containerHistory.removeAllViews();
                containerProgressCards.removeAllViews();

                if (history == null || history.isEmpty()) {
                    historyEmptyState.setVisibility(View.VISIBLE);
                    containerHistory.addView(historyEmptyState);
                } else {
                    historyEmptyState.setVisibility(View.GONE);
                    for (BenefitHistoryItem h : history) {
                        String s = h.getStatus() == null ? "" : h.getStatus().toLowerCase(Locale.US);
                        // Disable the Apply button for any non-rejected status
                        if (!s.equals("rejected") && !s.isEmpty() && h.getBenefit_name() != null) {
                            appliedBenefitNames.add(h.getBenefit_name().toLowerCase(Locale.US));
                        }
                        addHistoryCard(h);
                        addProgressCard(h);
                    }
                }

                // Now render cards — appliedBenefitNames is fully populated
                loadBenefits();
            }

            @Override
            public void onFailure(Call<BenefitStatusResponse> call, Throwable t) {
                Log.e(TAG, "Failed to load benefit status", t);
                loadBenefits();
            }
        });
    }

    // ─────────────────────────────────────────────
    // History card
    // ─────────────────────────────────────────────

    private void addHistoryCard(BenefitHistoryItem item) {
        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(dpToPx(4), dpToPx(4), dpToPx(4), dpToPx(4));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(12));
        card.setCardElevation(0);
        card.setCardBackgroundColor(Color.parseColor("#F9FBF9"));
        card.setStrokeColor(Color.parseColor("#E8F5E9"));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dpToPx(14), dpToPx(12), dpToPx(14), dpToPx(12));

        View dot = new View(this);
        LinearLayout.LayoutParams dotLp = new LinearLayout.LayoutParams(dpToPx(10), dpToPx(10));
        dotLp.setMarginEnd(dpToPx(12));
        dot.setLayoutParams(dotLp);
        dot.setBackground(roundedBg(statusColor(item.getStatus()), 5));

        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setLayoutParams(new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvName = new TextView(this);
        tvName.setText(item.getBenefit_name());
        tvName.setTextSize(13.5f);
        tvName.setTypeface(null, Typeface.BOLD);
        tvName.setTextColor(Color.parseColor("#263238"));

        TextView tvDate = new TextView(this);
        tvDate.setText(formatDate(item.getDate()));
        tvDate.setTextSize(11.5f);
        tvDate.setTextColor(Color.parseColor("#90A4AE"));
        LinearLayout.LayoutParams dateLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dateLp.topMargin = dpToPx(2);
        tvDate.setLayoutParams(dateLp);

        col.addView(tvName);
        col.addView(tvDate);

        TextView tvStatus = new TextView(this);
        tvStatus.setText(capitalize(item.getStatus()));
        tvStatus.setTextSize(11f);
        tvStatus.setTextColor(Color.parseColor(statusColor(item.getStatus())));
        tvStatus.setBackground(roundedBg(statusBg(item.getStatus()), 20));
        tvStatus.setPadding(dpToPx(10), dpToPx(4), dpToPx(10), dpToPx(4));
        tvStatus.setTypeface(null, Typeface.BOLD);

        row.addView(dot);
        row.addView(col);
        row.addView(tvStatus);
        card.addView(row);
        containerHistory.addView(card);
    }

    // ─────────────────────────────────────────────
    // Per-benefit progress card
    // ─────────────────────────────────────────────

    private void addProgressCard(BenefitHistoryItem item) {
        final String[] stepLabels = {"Submitted", "Review", "Processing", "Approved"};
        final int stepCount = stepLabels.length;
        final int currentStep = stepIndexFor(item.getStatus());

        MaterialCardView card = new MaterialCardView(this);
        LinearLayout.LayoutParams cardLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        cardLp.setMargins(0, 0, 0, dpToPx(12));
        card.setLayoutParams(cardLp);
        card.setRadius(dpToPx(16));
        card.setCardElevation(dpToPx(2));
        card.setCardBackgroundColor(Color.WHITE);
        card.setStrokeColor(Color.parseColor("#E8F5E9"));
        card.setStrokeWidth(dpToPx(1));

        LinearLayout inner = new LinearLayout(this);
        inner.setOrientation(LinearLayout.VERTICAL);
        inner.setPadding(dpToPx(18), dpToPx(16), dpToPx(18), dpToPx(18));

        // Title row
        LinearLayout titleRow = new LinearLayout(this);
        titleRow.setOrientation(LinearLayout.HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);

        TextView tvBenefitName = new TextView(this);
        tvBenefitName.setText(item.getBenefit_name());
        tvBenefitName.setTextSize(14f);
        tvBenefitName.setTypeface(null, Typeface.BOLD);
        tvBenefitName.setTextColor(Color.parseColor("#1B5E20"));
        tvBenefitName.setLayoutParams(new LinearLayout.LayoutParams(
                0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView tvDate = new TextView(this);
        tvDate.setText(formatDate(item.getDate()));
        tvDate.setTextSize(11f);
        tvDate.setTextColor(Color.parseColor("#90A4AE"));

        titleRow.addView(tvBenefitName);
        titleRow.addView(tvDate);

        // Step labels
        LinearLayout labelsRow = new LinearLayout(this);
        labelsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams labelsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        labelsLp.topMargin    = dpToPx(14);
        labelsLp.bottomMargin = dpToPx(6);
        labelsRow.setLayoutParams(labelsLp);

        for (int i = 0; i < stepCount; i++) {
            TextView lbl = new TextView(this);
            lbl.setText(stepLabels[i]);
            lbl.setTextSize(9.5f);
            lbl.setGravity(Gravity.CENTER);
            lbl.setLayoutParams(new LinearLayout.LayoutParams(
                    0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
            lbl.setTextColor(i <= currentStep
                    ? Color.parseColor("#2E7D32")
                    : Color.parseColor("#B0BEC5"));
            if (i <= currentStep) lbl.setTypeface(null, Typeface.BOLD);
            labelsRow.addView(lbl);
        }

        // Progress track + fill
        FrameLayout trackFrame = new FrameLayout(this);
        LinearLayout.LayoutParams trackLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, dpToPx(8));
        trackLp.bottomMargin = dpToPx(4);
        trackFrame.setLayoutParams(trackLp);

        View track = new View(this);
        track.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        track.setBackground(roundedBg("#E8F5E9", 4));

        View fill = new View(this);
        fill.setBackground(roundedBg("#2E7D32", 4));
        trackFrame.post(() -> {
            int w = trackFrame.getWidth();
            if (w > 0) {
                int fillW = (stepCount > 1)
                        ? (int) (w * ((float) Math.max(currentStep, 0) / (stepCount - 1))) : w;
                FrameLayout.LayoutParams fp = new FrameLayout.LayoutParams(
                        fillW, ViewGroup.LayoutParams.MATCH_PARENT);
                fill.setLayoutParams(fp);
            }
        });

        trackFrame.addView(track);
        trackFrame.addView(fill);

        // Step dots
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams dotsLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        dotsLp.topMargin = dpToPx(-10);
        dotsRow.setLayoutParams(dotsLp);

        for (int i = 0; i < stepCount; i++) {
            FrameLayout dotHolder = new FrameLayout(this);
            dotHolder.setLayoutParams(new LinearLayout.LayoutParams(0, dpToPx(20), 1f));

            View dot = new View(this);
            FrameLayout.LayoutParams dotLp = new FrameLayout.LayoutParams(dpToPx(16), dpToPx(16));
            dotLp.gravity = Gravity.CENTER;
            dot.setLayoutParams(dotLp);
            dot.setBackground(roundedBg(i <= currentStep ? "#2E7D32" : "#C8E6C9", 8));

            dotHolder.addView(dot);
            dotsRow.addView(dotHolder);
        }

        inner.addView(titleRow);
        inner.addView(labelsRow);
        inner.addView(trackFrame);
        inner.addView(dotsRow);
        card.addView(inner);
        containerProgressCards.addView(card);
    }

    // ─────────────────────────────────────────────
    // File upload UI
    // ─────────────────────────────────────────────

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
            sb.append("• ").append(name != null ? name : u.toString())
                    .append(" (").append(formatBytes(size)).append(")\n");
        }
        tvSelectedFiles.setText(sb.toString().trim());
        tvTotalSize.setText(String.format(Locale.US, "Total: %s / 10 MB", formatBytes(total)));
        btnClearFiles.setEnabled(true);
    }

    // ─────────────────────────────────────────────
    // Bottom nav
    // ─────────────────────────────────────────────

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

        int grey = Color.parseColor("#B0B0B0");
        tvHome.setTextColor(grey);
        tvIncident.setTextColor(grey);
        tvCalculator.setTextColor(grey);
        tvProfile.setTextColor(grey);

        navHome.setOnClickListener(v       -> startActivity(new Intent(this, DashboardActivity.class)));
        navIncident.setOnClickListener(v   -> startActivity(new Intent(this, IncidentReportsActivity.class)));
        navCalculator.setOnClickListener(v -> startActivity(new Intent(this, BillingCalculatorActivity.class)));
        navProfile.setOnClickListener(v    -> startActivity(new Intent(this, ProfileActivity.class)));
        navLogout.setOnClickListener(v -> {
            getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

    // ─────────────────────────────────────────────
    // Utility
    // ─────────────────────────────────────────────

    private String formatDate(String raw) {
        if (raw == null || raw.isEmpty()) return "";
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'",
                "yyyy-MM-dd'T'HH:mm:ss'Z'",
                "yyyy-MM-dd'T'HH:mm:ss",
                "yyyy-MM-dd"
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(pattern, Locale.US);
                sdf.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
                Date date = sdf.parse(raw);
                if (date != null) {
                    return new SimpleDateFormat("MMM d, yyyy", Locale.US).format(date);
                }
            } catch (ParseException ignored) { }
        }
        return raw;
    }

    private int stepIndexFor(String status) {
        if (status == null) return 0;
        switch (status.toLowerCase(Locale.US)) {
            case "submitted": case "pending": return 0;
            case "review":                    return 1;
            case "processing":                return 2;
            case "approved":                  return 3;
            default:                          return 0;
        }
    }

    private String statusColor(String status) {
        if (status == null) return "#78909C";
        switch (status.toLowerCase(Locale.US)) {
            case "approved":   return "#2E7D32";
            case "pending":    return "#F57F17";
            case "review":     return "#1565C0";
            case "processing": return "#6A1B9A";
            default:           return "#78909C";
        }
    }

    private String statusBg(String status) {
        if (status == null) return "#ECEFF1";
        switch (status.toLowerCase(Locale.US)) {
            case "approved":   return "#E8F5E9";
            case "pending":    return "#FFF8E1";
            case "review":     return "#E3F2FD";
            case "processing": return "#F3E5F5";
            default:           return "#ECEFF1";
        }
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1).toLowerCase(Locale.US);
    }

    private android.graphics.drawable.GradientDrawable roundedBg(String hex, int radiusDp) {
        android.graphics.drawable.GradientDrawable d = new android.graphics.drawable.GradientDrawable();
        d.setColor(Color.parseColor(hex));
        d.setCornerRadius(dpToPx(radiusDp));
        return d;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────
    // File helpers
    // ─────────────────────────────────────────────

    private String getUriDisplayName(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (idx >= 0) return cursor.getString(idx);
            }
        } catch (Exception ignored) { }
        return null;
    }

    private long getUriSizeBytes(Uri uri) {
        try (Cursor cursor = getContentResolver().query(uri, null, null, null, null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int idx = cursor.getColumnIndex(OpenableColumns.SIZE);
                if (idx >= 0 && !cursor.isNull(idx)) return cursor.getLong(idx);
            }
        } catch (Exception ignored) { }
        try (InputStream in = getContentResolver().openInputStream(uri)) {
            if (in == null) return -1L;
            long count = 0L;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) {
                count += n;
                if (count > MAX_UPLOAD_BYTES) return count;
            }
            return count;
        } catch (Exception e) { return -1L; }
    }

    private String resolveMimeType(Uri uri) {
        String type = getContentResolver().getType(uri);
        if (type != null) return type;
        String name = getUriDisplayName(uri);
        if (name == null) return null;
        int dot = name.lastIndexOf('.');
        if (dot < 0) return null;
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(
                name.substring(dot + 1).toLowerCase(Locale.US));
    }

    private byte[] readAllBytes(Uri uri, long limitBytes) throws IOException {
        try (InputStream in = getContentResolver().openInputStream(uri)) {
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
        return String.format(Locale.US, "%.2f MB", kb / 1024.0);
    }
}
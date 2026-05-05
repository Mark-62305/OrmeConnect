package com.ormec.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.graphics.Color;
import android.content.Intent;
import com.ormec.myapplication.models.ScheduleRequestBody;
import com.ormec.myapplication.models.ScheduleRequestResponse;

import androidx.appcompat.app.AppCompatActivity;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class SchedulingActivity extends AppCompatActivity {

    private TextView tvStepLabel, tvProgressTitle, tvProgressPercent, tvSeminarDate;
    private View stepBar1, stepBar2, stepBar3;
    private Button btnNext;

    // Request info card views
    private LinearLayout cardRequestInfo, rowApprovedDate, rowRemarks;
    private TextView tvRequestStatusBadge, tvRequestSubmittedDate,
            tvRequestSeminarDate, tvRequestTime,
            tvApprovedDate, tvRemarks;

    private String selectedDate = null;   // yyyy-MM-dd
    private String startTime = "08:00";
    private String endTime   = "17:00";

    private void setSubmitEnabled(boolean enabled) {
        btnNext.setEnabled(enabled);
        btnNext.setAlpha(enabled ? 1.0f : 0.6f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seminar_scheduling);

        btnNext               = findViewById(R.id.btnNext);
        tvProgressTitle       = findViewById(R.id.tvProgressTitle);
        tvProgressPercent     = findViewById(R.id.tvProgressPercent);
        tvStepLabel           = findViewById(R.id.tvStepLabel);
        tvSeminarDate         = findViewById(R.id.tvSeminarDate);
        stepBar1              = findViewById(R.id.stepBar1);
        stepBar2              = findViewById(R.id.stepBar2);
        stepBar3              = findViewById(R.id.stepBar3);

        // Request info card
        cardRequestInfo       = findViewById(R.id.cardRequestInfo);
        rowApprovedDate       = findViewById(R.id.rowApprovedDate);
        rowRemarks            = findViewById(R.id.rowRemarks);
        tvRequestStatusBadge  = findViewById(R.id.tvRequestStatusBadge);
        tvRequestSubmittedDate= findViewById(R.id.tvRequestSubmittedDate);
        tvRequestSeminarDate  = findViewById(R.id.tvRequestSeminarDate);
        tvRequestTime         = findViewById(R.id.tvRequestTime);
        tvApprovedDate        = findViewById(R.id.tvApprovedDate);
        tvRemarks             = findViewById(R.id.tvRemarks);

        btnNext.setText("Submit Schedule Request");
        CalendarView calendar = findViewById(R.id.calendarViewSeminar);
        calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            this.selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, (month + 1), dayOfMonth);
            this.tvSeminarDate.setText(this.selectedDate);
        });

        fetchLatestStatus();
        btnNext.setOnClickListener(v -> submitScheduleRequest());

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
    }

    // ─────────────────────────────────────────
    // Fetch latest status on open
    // ─────────────────────────────────────────
    private void fetchLatestStatus() {
        long userId = getUserId();
        if (userId == -1) { setApprovalUI("draft", null); return; }

        RetrofitClient.getApiService()
                .getLatestScheduleRequest(userId)
                .enqueue(new retrofit2.Callback<ScheduleRequestResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<ScheduleRequestResponse> call,
                                           retrofit2.Response<ScheduleRequestResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            // 404 → no prior request, allow submit
                            setApprovalUI("draft", null);
                            return;
                        }
                        ScheduleRequestResponse body = res.body();
                        String s = body.getEffectiveStatus();
                        setApprovalUI(s != null ? s : "draft", body.request);
                    }
                    @Override
                    public void onFailure(retrofit2.Call<ScheduleRequestResponse> call, Throwable t) {
                        setApprovalUI("draft", null);
                    }
                });
    }

    private long getUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getLong("user_id", -1);
    }

    // ─────────────────────────────────────────
    // Submit
    // ─────────────────────────────────────────
    private void submitScheduleRequest() {
        if (selectedDate == null) {
            Toast.makeText(this, "Please select a seminar date.", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = getUserId();
        if (userId == -1) {
            Toast.makeText(this, "User ID not found. Please login again.", Toast.LENGTH_SHORT).show();
            setApprovalUI("draft", null);
            return;
        }

        setApprovalUI("pending", null);

        ScheduleRequestBody body = new ScheduleRequestBody(userId, selectedDate, "08:00:00", "17:00:00");

        RetrofitClient.getApiService().createScheduleRequest(body)
                .enqueue(new retrofit2.Callback<ScheduleRequestResponse>() {
                    @Override
                    public void onResponse(retrofit2.Call<ScheduleRequestResponse> call,
                                           retrofit2.Response<ScheduleRequestResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            Toast.makeText(SchedulingActivity.this,
                                    "Submit failed: " + res.code(), Toast.LENGTH_SHORT).show();
                            setApprovalUI("draft", null);
                            return;
                        }
                        ScheduleRequestResponse r = res.body();
                        // After submit, re-fetch to get full details for the card
                        fetchLatestStatus();
                        Toast.makeText(SchedulingActivity.this,
                                r.message != null ? r.message : "Submitted",
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override
                    public void onFailure(retrofit2.Call<ScheduleRequestResponse> call, Throwable t) {
                        Toast.makeText(SchedulingActivity.this,
                                "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        setApprovalUI("draft", null);
                    }
                });
    }

    // ─────────────────────────────────────────
    // UI update — step bars + info card
    // ─────────────────────────────────────────
    private void setApprovalUI(String statusRaw, ScheduleRequestResponse.RequestDetail detail) {
        String status = normalizeStatus(statusRaw);

        int green  = Color.parseColor("#22C55E");
        int amber  = Color.parseColor("#FCD34D");
        int red    = Color.parseColor("#EF4444");
        int grey   = Color.parseColor("#E5E7EB");

        // ── Step bars ──
        switch (status) {
            case "pending":
                stepBar1.setBackgroundColor(green);
                stepBar2.setBackgroundColor(amber);
                stepBar3.setBackgroundColor(grey);
                tvProgressPercent.setText("PENDING");
                break;
            case "approved":
                stepBar1.setBackgroundColor(green);
                stepBar2.setBackgroundColor(green);
                stepBar3.setBackgroundColor(green);
                tvProgressPercent.setText("APPROVED");
                break;
            case "rejected":
                stepBar1.setBackgroundColor(green);
                stepBar2.setBackgroundColor(red);
                stepBar3.setBackgroundColor(grey);
                tvProgressPercent.setText("REJECTED");
                break;
            default: // draft
                stepBar1.setBackgroundColor(green);
                stepBar2.setBackgroundColor(grey);
                stepBar3.setBackgroundColor(grey);
                tvProgressPercent.setText("DRAFT");
                break;
        }

        tvProgressTitle.setText("Admin Approval");
        tvStepLabel.setText("Status: " + status.toUpperCase());
        setSubmitEnabled(!status.equals("pending") && !status.equals("approved"));

        // ── Request info card ──
        if (detail == null && status.equals("draft")) {
            cardRequestInfo.setVisibility(View.GONE);
            return;
        }

        // Show the card for pending / approved / rejected
        cardRequestInfo.setVisibility(View.VISIBLE);

        // Status badge color
        switch (status) {
            case "approved":
                tvRequestStatusBadge.setBackgroundColor(green);
                tvRequestStatusBadge.setTextColor(Color.WHITE);
                tvRequestStatusBadge.setText("APPROVED");
                break;
            case "rejected":
                tvRequestStatusBadge.setBackgroundColor(red);
                tvRequestStatusBadge.setTextColor(Color.WHITE);
                tvRequestStatusBadge.setText("REJECTED");
                break;
            default: // pending
                tvRequestStatusBadge.setBackgroundColor(Color.parseColor("#DCFCE7"));
                tvRequestStatusBadge.setTextColor(Color.parseColor("#0F5132"));
                tvRequestStatusBadge.setText("PENDING");
                break;
        }

        if (detail != null) {
            // Submitted date
            tvRequestSubmittedDate.setText(formatDateTime(detail.created_at));

            // Seminar date
            tvRequestSeminarDate.setText(formatDate(detail.seminar_date));

            // Time range
            tvRequestTime.setText(formatTime(detail.start_time) + " – " + formatTime(detail.end_time));

            // Approved date row
            if ("approved".equals(status) && detail.reviewed_at != null && !detail.reviewed_at.isEmpty()) {
                rowApprovedDate.setVisibility(View.VISIBLE);
                tvApprovedDate.setText(formatDateTime(detail.reviewed_at));
            } else {
                rowApprovedDate.setVisibility(View.GONE);
            }

            // Remarks row (for rejected)
            if ("rejected".equals(status) && detail.remarks != null && !detail.remarks.isEmpty()) {
                rowRemarks.setVisibility(View.VISIBLE);
                tvRemarks.setText(detail.remarks);
            } else {
                rowRemarks.setVisibility(View.GONE);
            }
        }
    }

    // ─────────────────────────────────────────
    // Date formatting helpers
    // ─────────────────────────────────────────

    /** Formats ISO datetime string → "Apr 18, 2026  2:33 PM" */
    /** Formats ISO datetime string → "Apr 18, 2026  8:54 PM" */
    private String formatDateTime(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        // Replace trailing Z with +00:00 so SimpleDateFormat parses timezone correctly
        String normalized = iso.replace("Z", "+00:00");
        String[] patterns = {
                "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
                "yyyy-MM-dd'T'HH:mm:ssXXX",
                "yyyy-MM-dd HH:mm:ss"   // fallback for non-ISO from DB
        };
        for (String pattern : patterns) {
            try {
                SimpleDateFormat inFmt = new SimpleDateFormat(pattern, Locale.US);
                Date d = inFmt.parse(pattern.contains("XXX") ? normalized : iso);
                // Display in device local time
                SimpleDateFormat outFmt = new SimpleDateFormat("MMM dd, yyyy  h:mm a", Locale.US);
                return outFmt.format(d);
            } catch (ParseException ignored) {}
        }
        return iso; // raw fallback
    }

    /** Formats ISO date or datetime → "Apr 30, 2026" */
    private String formatDate(String iso) {
        if (iso == null || iso.isEmpty()) return "—";
        try {
            if (iso.contains("T")) {
                // It's a full datetime from MySQL — parse as UTC, show only date in local tz
                String normalized = iso.replace("Z", "+00:00");
                // Try with milliseconds first
                try {
                    SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", Locale.US);
                    Date d = inFmt.parse(normalized);
                    return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(d);
                } catch (ParseException ignored) {}
                // Try without milliseconds
                SimpleDateFormat inFmt = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ssXXX", Locale.US);
                Date d = inFmt.parse(normalized);
                return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(d);
            }
            // Plain "yyyy-MM-dd" — parse directly, no timezone shift needed
            Date d = new SimpleDateFormat("yyyy-MM-dd", Locale.US).parse(iso.substring(0, 10));
            return new SimpleDateFormat("MMM dd, yyyy", Locale.US).format(d);
        } catch (ParseException e) {
            return iso; // raw fallback
        }
    }

    /** Formats "HH:mm:ss" → "8:00 AM" */
    private String formatTime(String t) {
        if (t == null || t.isEmpty()) return "—";
        try {
            Date d = new SimpleDateFormat("HH:mm:ss", Locale.US).parse(t);
            return new SimpleDateFormat("h:mm a", Locale.US).format(d);
        } catch (ParseException e) {
            return t;
        }
    }

    private String normalizeStatus(String s) {
        if (s == null) return "draft";
        return s.trim().toLowerCase();
    }
}
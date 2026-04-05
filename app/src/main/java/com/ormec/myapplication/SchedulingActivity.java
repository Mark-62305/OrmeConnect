package com.ormec.myapplication;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.CalendarView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.content.Intent;
import androidx.cardview.widget.CardView;
import com.ormec.myapplication.models.ScheduleRequestBody;
import com.ormec.myapplication.models.ScheduleRequestResponse;

import androidx.appcompat.app.AppCompatActivity;

import java.util.Locale;

public class SchedulingActivity extends AppCompatActivity {

    private TextView tvStepLabel, tvProgressTitle, tvProgressPercent, tvSeminarDate, tvStartTime, tvEndTime;
    private ProgressBar progressBar;
    private Button btnNext;

    private String selectedDate = null;   // yyyy-MM-dd
    private String startTime = "08:00";   // HH:mm
    private String endTime   = "17:00";   // HH:mm


    private void setSubmitEnabled(boolean enabled) {
        btnNext.setEnabled(enabled);
        btnNext.setAlpha(enabled ? 1.0f : 0.6f);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_seminar_scheduling);

        // bind your existing XML ids...
        btnNext = findViewById(R.id.btnNext);
        progressBar = findViewById(R.id.progressBar);
        tvProgressTitle = findViewById(R.id.tvProgressTitle);
        tvProgressPercent = findViewById(R.id.tvProgressPercent);
        tvStepLabel = findViewById(R.id.tvStepLabel);
        tvSeminarDate = findViewById(R.id.tvSeminarDate);

        btnNext.setText("Submit Schedule Request");
        CalendarView calendar = findViewById(R.id.calendarViewSeminar);

// DO NOT redeclare tvSeminarDate here if you already have the field
        calendar.setOnDateChangeListener((view, year, month, dayOfMonth) -> {
            this.selectedDate = String.format(Locale.US, "%04d-%02d-%02d", year, (month + 1), dayOfMonth);
            this.tvSeminarDate.setText(this.selectedDate);
        });
        // On load, check latest status and disable if pending
        fetchLatestStatus();

        btnNext.setOnClickListener(v -> submitScheduleRequest());
    }


    private void fetchLatestStatus() {
        RetrofitClient.getApiService().getLatestScheduleRequest().enqueue(new retrofit2.Callback<ScheduleRequestResponse>() {
            @Override public void onResponse(retrofit2.Call<ScheduleRequestResponse> call, retrofit2.Response<ScheduleRequestResponse> res) {
                if (!res.isSuccessful()) {
                    // if none found, allow submit
                    setApprovalUI("draft");
                    return;
                }
                ScheduleRequestResponse body = res.body();
                if (body == null) { setApprovalUI("draft"); return; }
                setApprovalUI(body.status);
            }
            @Override public void onFailure(retrofit2.Call<ScheduleRequestResponse> call, Throwable t) {
                setApprovalUI("draft");
            }
        });
    }

    private long getUserId() {
        SharedPreferences prefs = getSharedPreferences("MyAppPrefs", MODE_PRIVATE);
        return prefs.getLong("user_id", -1);
    }
    private void submitScheduleRequest() {
        if (selectedDate == null) {
            Toast.makeText(this, "Please select a seminar date.", Toast.LENGTH_SHORT).show();
            return;
        }

        long userId = getUserId();
        if (userId == -1) {
            Toast.makeText(this, "User ID not found. Please login again.", Toast.LENGTH_SHORT).show();
            setApprovalUI("draft");
            return;
        }

        setApprovalUI("pending");

        String start = "08:00:00";
        String end   = "17:00:00";

        // IMPORTANT: ScheduleRequestBody must include user_id
        ScheduleRequestBody body = new ScheduleRequestBody(userId, selectedDate, start, end);

        RetrofitClient.getApiService().createScheduleRequest(body)
                .enqueue(new retrofit2.Callback<ScheduleRequestResponse>() {
                    @Override public void onResponse(retrofit2.Call<ScheduleRequestResponse> call,
                                                     retrofit2.Response<ScheduleRequestResponse> res) {
                        if (!res.isSuccessful() || res.body() == null) {
                            Toast.makeText(SchedulingActivity.this, "Submit failed: " + res.code(), Toast.LENGTH_SHORT).show();
                            setApprovalUI("draft");
                            return;
                        }
                        setApprovalUI(res.body().status);
                        Toast.makeText(SchedulingActivity.this,
                                res.body().message != null ? res.body().message : "Submitted",
                                Toast.LENGTH_SHORT).show();
                    }

                    @Override public void onFailure(retrofit2.Call<ScheduleRequestResponse> call, Throwable t) {
                        Toast.makeText(SchedulingActivity.this, "Network error: " + t.getMessage(), Toast.LENGTH_SHORT).show();
                        setApprovalUI("draft");
                    }
                });
    }

    // Matches “progress” to admin approval
    private void setApprovalUI(String statusRaw) {
        String status = normalizeStatus(statusRaw);

        int pct = 0;
        switch (status) {
            case "pending":  pct = 50; break;
            case "approved": pct = 100; break;
            case "rejected": pct = 100; break;
            default:         pct = 0;
        }

        progressBar.setProgress(pct);
        tvProgressTitle.setText("Admin Approval");
        tvProgressPercent.setText(status.toUpperCase());

        tvStepLabel.setText("Status: " + status.toUpperCase());

        // ✅ disable submit if still in progress
        setSubmitEnabled(!status.equals("pending"));
    }

    // ✅ This “matches #2” (handles approved/pending/rejected even if backend uses lowercase)
    private String normalizeStatus(String s) {
        if (s == null) return "draft";
        return s.trim().toLowerCase();
    }
}
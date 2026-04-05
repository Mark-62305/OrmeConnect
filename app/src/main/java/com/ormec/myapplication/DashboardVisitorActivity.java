package com.ormec.myapplication;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.cardview.widget.CardView;

/**
 * DashboardVisitorActivity
 *
 * Dashboard for visitors who are browsing without logging in.
 * Provides limited functionality including:
 * - Viewing benefits/perks
 * - Using the billing calculator
 * - Creating a new account
 * - Logging out (returning to login screen)
 */
public class DashboardVisitorActivity extends AppCompatActivity {

    private CardView btnQuickBenefits;
    private CardView btnQuickCalculator;
    private CardView btnCreateAccount;
    private CardView btnLogout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard_visitor);

        // Initialize card views
        initializeViews();

        // Set up click listeners
        setupClickListeners();
    }

    /**
     * Initialize all view components from the layout
     */
    private void initializeViews() {
        btnQuickBenefits = findViewById(R.id.btnQuickBenefits);
        btnQuickCalculator = findViewById(R.id.btnQuickCalculator);
        btnCreateAccount = findViewById(R.id.btnCreateAccount);
        btnLogout = findViewById(R.id.btnLogout);
    }

    /**
     * Set up click listeners for all interactive cards
     */
    private void setupClickListeners() {
        // Benefits Card - Navigate to Benefits Activity
        btnQuickBenefits.setOnClickListener(v -> {
            // TODO: Implement BenefitsActivity when ready
            Toast.makeText(this, "Benefits feature coming soon", Toast.LENGTH_SHORT).show();
            // Intent intent = new Intent(DashboardVisitorActivity.this, BenefitsActivity.class);
            // startActivity(intent);
        });

        // Calculator Card - Navigate to Visitor Calculator
        btnQuickCalculator.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardVisitorActivity.this, VisitorCalculatorActivity.class);
            startActivity(intent);
        });

        // Create Account Card - Navigate to Create Account Activity
        btnCreateAccount.setOnClickListener(v -> {
            Intent intent = new Intent(DashboardVisitorActivity.this, CreateAccountActivity.class);
            startActivity(intent);
        });

        // Logout Card - Clear visitor session and return to MainActivity
        btnLogout.setOnClickListener(v -> {
            logoutVisitor();
        });
    }

    /**
     * Handles visitor logout
     * Clears SharedPreferences and returns to login screen
     */
    private void logoutVisitor() {
        // Clear visitor session data
        getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                .edit()
                .clear()
                .apply();

        // Show confirmation message
        Toast.makeText(this, "Logged out successfully", Toast.LENGTH_SHORT).show();

        // Navigate back to MainActivity (login screen)
        Intent intent = new Intent(DashboardVisitorActivity.this, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(intent);
        finish();
    }

    /**
     * Override back button to prevent going back to login
     * while in visitor mode
     */
    @Override
    public void onBackPressed() {
        // Show message or dialog asking if user wants to exit visitor mode
        Toast.makeText(this, "Press Logout to exit visitor mode", Toast.LENGTH_SHORT).show();
        // Optionally, you can call super.onBackPressed() to allow default behavior
        // super.onBackPressed();
    }
}
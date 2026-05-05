package com.ormec.myapplication;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.content.ContextCompat;
import androidx.annotation.OptIn;
import androidx.camera.core.ExperimentalGetImage;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.common.InputImage;
import com.ormec.myapplication.models.QrMemberLoginRequest;
import com.ormec.myapplication.models.QrMemberLoginResponse;
import com.ormec.myapplication.models.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

import retrofit2.Call;

public class QrLoginActivity extends AppCompatActivity {

    private PreviewView previewView;
    private TextView tvHint;
    private ProgressBar progress;

    private ExecutorService cameraExecutor;
    private BarcodeScanner barcodeScanner;

    private final AtomicBoolean handlingResult = new AtomicBoolean(false);

    private final ActivityResultLauncher<String> requestCameraPermission =
            registerForActivityResult(new ActivityResultContracts.RequestPermission(), granted -> {
                if (granted) startCamera();
                else {
                    Toast.makeText(this, "Camera permission is required.", Toast.LENGTH_LONG).show();
                    finish();
                }
            });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_qr_login);

        previewView = findViewById(R.id.previewView);
        tvHint = findViewById(R.id.tvHint);
        progress = findViewById(R.id.progress);

        cameraExecutor = Executors.newSingleThreadExecutor();

        BarcodeScannerOptions options = new BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build();
        barcodeScanner = BarcodeScanning.getClient(options);

        ensurePermissionThenStart();
    }

    private void ensurePermissionThenStart() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED) {
            startCamera();
        } else {
            requestCameraPermission.launch(Manifest.permission.CAMERA);
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> future = ProcessCameraProvider.getInstance(this);

        future.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = future.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis analysis = new ImageAnalysis.Builder()
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();

                analysis.setAnalyzer(cameraExecutor, this::analyzeImage);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(
                        this,
                        CameraSelector.DEFAULT_BACK_CAMERA,
                        preview,
                        analysis
                );

            } catch (Exception e) {
                Toast.makeText(this, "Camera error: " + e.getMessage(), Toast.LENGTH_LONG).show();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void analyzeImage(@NonNull ImageProxy imageProxy) {
        if (handlingResult.get()) {
            imageProxy.close();
            return;
        }

        if (imageProxy.getImage() == null) {
            imageProxy.close();
            return;
        }

        InputImage image = InputImage.fromMediaImage(
                imageProxy.getImage(),
                imageProxy.getImageInfo().getRotationDegrees()
        );

        barcodeScanner.process(image)
                .addOnSuccessListener(barcodes -> {
                    if (barcodes == null || barcodes.isEmpty()) return;

                    Barcode first = barcodes.get(0);
                    String raw = first.getRawValue();

                    if (raw != null && !raw.trim().isEmpty()) {
                        if (handlingResult.compareAndSet(false, true)) {
                            onQrScanned(raw.trim());
                        }
                    }
                })
                .addOnCompleteListener(t -> imageProxy.close());
    }

    /**
     * QR payload is expected to be the member_code (plain text)
     * Example QR content: "MEM-000123"
     */
    private void onQrScanned(String qrPayload) {
        runOnUiThread(() -> {
            progress.setVisibility(View.VISIBLE);
            tvHint.setText("Logging in…");
        });

        final String memberCode = qrPayload.length() > 11 ? qrPayload.substring(0, 11) : qrPayload;

        ApiServices api = RetrofitClient.getApiService();
        Call<QrMemberLoginResponse> call = api.qrMemberLogin(new QrMemberLoginRequest(memberCode));

        call.enqueue(new retrofit2.Callback<QrMemberLoginResponse>() {
            @Override
            public void onResponse(Call<QrMemberLoginResponse> call, retrofit2.Response<QrMemberLoginResponse> response) {
                if (!response.isSuccessful() || response.body() == null) {
                    onLoginFailed("Invalid server response.");
                    return;
                }

                QrMemberLoginResponse body = response.body();

                if (!"success".equalsIgnoreCase(body.status)) {
                    onLoginFailed(body.message != null ? body.message : "Invalid member code.");
                    return;
                }

// Save accessToken
                if (body.accessToken != null && !body.accessToken.trim().isEmpty()) {
                    getSharedPreferences("auth", MODE_PRIVATE)
                            .edit()
                            .putString("accessToken", body.accessToken)
                            .apply();
                }

// ← ADD THIS: Save user_id and user_role so DashboardActivity can find them
                if (body.user != null) {
                    getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                            .edit()
                            .putLong("user_id", (long) body.user.id)
                            .putString("user_role", body.user.role)
                            .apply();
                }

                runOnUiThread(() -> {
                    progress.setVisibility(View.GONE);
                    startActivity(new Intent(QrLoginActivity.this, DashboardActivity.class));
                    finish();
                });
            }

            @Override
            public void onFailure(Call<QrMemberLoginResponse> call, Throwable t) {
                onLoginFailed(t.getMessage());
            }
        });
    }

    private void onLoginFailed(String msg) {
        runOnUiThread(() -> {
            progress.setVisibility(View.GONE);
            tvHint.setText("Scan QR to login");
            handlingResult.set(false);
            Toast.makeText(this, msg == null ? "Login failed" : msg, Toast.LENGTH_LONG).show();
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) cameraExecutor.shutdown();
        if (barcodeScanner != null) barcodeScanner.close();
    }
}
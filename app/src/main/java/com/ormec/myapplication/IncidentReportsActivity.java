package com.ormec.myapplication;

import android.Manifest;
import android.annotation.SuppressLint;
import android.database.Cursor;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.net.Uri;
import android.os.Bundle;
import android.provider.OpenableColumns;
import android.webkit.MimeTypeMap;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import com.ormec.myapplication.models.IncidentItem;
import com.ormec.myapplication.models.IncidentListResponse;
import com.ormec.myapplication.models.SimpleResponse;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class IncidentReportsActivity extends AppCompatActivity implements OnMapReadyCallback {

    // ── Constants ──────────────────────────────────────────────────────────────
    private static final int PICK_IMAGE_REQUEST   = 101;
    private static final int LOCATION_PERM_REQUEST = 102;
    private static final long MAX_UPLOAD_BYTES = 10L * 1024L * 1024L; // 10 MB

    // Default centre (Philippines) – shown before the user grants GPS
    private static final double DEFAULT_LAT = 10.3157;
    private static final double DEFAULT_LNG = 123.8854;

    // ── UI ─────────────────────────────────────────────────────────────────────
    private RadioGroup rgCategory;
    private RadioButton rbOutage, rbDamagedPole, rbOther;
    private EditText  etDescription;
    private Button    btnChooseFile, btnSubmit, btnUseMyLocation;
    private TextView  tvLocation, tvCoordinates, tvSelectedFile;
    private LinearLayout containerIncidentHistory;

    // ── Map ────────────────────────────────────────────────────────────────────
    private MapView   mapView;
    private GoogleMap googleMap;

    // ── State ──────────────────────────────────────────────────────────────────
    private Uri    attachedImageUri;
    private double pickedLat = DEFAULT_LAT;
    private double pickedLng = DEFAULT_LNG;

    private FusedLocationProviderClient fusedLocationClient;

    // ══════════════════════════════════════════════════════════════════════════
    //  Lifecycle
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_incident_reports);

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this);

        // --- bind views ---
        rgCategory    = findViewById(R.id.rgCategory);
        rbOutage      = findViewById(R.id.rbOutage);
        rbDamagedPole = findViewById(R.id.rbDamagedPole);
        rbOther       = findViewById(R.id.rbOther);
        etDescription = findViewById(R.id.etDescription);
        btnChooseFile = findViewById(R.id.btnChooseFile);
        btnSubmit     = findViewById(R.id.btnSubmit);
        tvLocation    = findViewById(R.id.tvLocation);
        tvCoordinates = findViewById(R.id.tvCoordinates);
        tvSelectedFile= findViewById(R.id.tvSelectedFile);
        btnUseMyLocation      = findViewById(R.id.btnUseMyLocation);
        containerIncidentHistory = findViewById(R.id.containerIncidentHistory);

        // --- MapView lifecycle ---
        mapView = findViewById(R.id.mapView);
        mapView.onCreate(savedInstanceState);
        mapView.getMapAsync(this);   // → onMapReady()

        // --- listeners ---
        btnChooseFile.setOnClickListener(v -> openImagePicker());
        btnSubmit.setOnClickListener(v -> submitIncident());
        btnUseMyLocation.setOnClickListener(v -> requestLocationPermissionAndFetch());

        setupBottomNav();
        loadIncidentHistory();
    }

    // ── MapView lifecycle forwarding (required!) ────────────────────────────

    @Override protected void onResume()  { super.onResume();  mapView.onResume();  }
    @Override protected void onPause()   { super.onPause();   mapView.onPause();   }
    @Override protected void onStart()   { super.onStart();   mapView.onStart();   }
    @Override protected void onStop()    { super.onStop();    mapView.onStop();    }
    @Override protected void onDestroy() { super.onDestroy(); mapView.onDestroy(); }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mapView.onLowMemory();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        mapView.onSaveInstanceState(outState);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Map ready callback
    // ══════════════════════════════════════════════════════════════════════════

    @Override
    public void onMapReady(@NonNull GoogleMap map) {
        googleMap = map;
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setMapToolbarEnabled(false);

        // Move camera to default location
        LatLng defaultPos = new LatLng(DEFAULT_LAT, DEFAULT_LNG);
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(defaultPos, 13f));

        // Allow tap-to-pin: user taps map → moves the marker there
        googleMap.setOnMapClickListener(latLng -> {
            pickedLat = latLng.latitude;
            pickedLng = latLng.longitude;
            updateMarker(latLng);
            reverseGeocode(latLng);
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  GPS / Location
    // ══════════════════════════════════════════════════════════════════════════

    private void requestLocationPermissionAndFetch() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            fetchCurrentLocation();
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION,
                            Manifest.permission.ACCESS_COARSE_LOCATION
                    },
                    LOCATION_PERM_REQUEST
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERM_REQUEST) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                fetchCurrentLocation();
            } else {
                Toast.makeText(this,
                        "Location permission denied. Tap the map to pin manually.",
                        Toast.LENGTH_LONG).show();
            }
        }
    }

    @SuppressLint("MissingPermission")
    private void fetchCurrentLocation() {
        btnUseMyLocation.setEnabled(false);
        btnUseMyLocation.setText("Fetching…");

        fusedLocationClient.getLastLocation().addOnSuccessListener(location -> {
            btnUseMyLocation.setEnabled(true);
            btnUseMyLocation.setText("📍 Use My Location");

            if (location != null) {
                pickedLat = location.getLatitude();
                pickedLng = location.getLongitude();
                LatLng latLng = new LatLng(pickedLat, pickedLng);
                updateMarker(latLng);
                if (googleMap != null) {
                    googleMap.animateCamera(CameraUpdateFactory.newLatLngZoom(latLng, 16f));
                }
                reverseGeocode(latLng);
            } else {
                // Last location not cached – request a fresh single update
                fetchFreshLocation();
            }
        }).addOnFailureListener(e -> {
            btnUseMyLocation.setEnabled(true);
            btnUseMyLocation.setText("📍 Use My Location");
            Toast.makeText(this, "Could not get location: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        });
    }

    @SuppressLint("MissingPermission")
    private void fetchFreshLocation() {
        com.google.android.gms.location.LocationRequest req =
                com.google.android.gms.location.LocationRequest.create()
                        .setPriority(com.google.android.gms.location.Priority.PRIORITY_HIGH_ACCURACY)
                        .setNumUpdates(1)
                        .setInterval(0);

        fusedLocationClient.requestLocationUpdates(req,
                new com.google.android.gms.location.LocationCallback() {
                    @Override
                    public void onLocationResult(
                            @NonNull com.google.android.gms.location.LocationResult result) {
                        fusedLocationClient.removeLocationUpdates(this);
                        android.location.Location loc = result.getLastLocation();
                        if (loc != null) {
                            pickedLat = loc.getLatitude();
                            pickedLng = loc.getLongitude();
                            LatLng latLng = new LatLng(pickedLat, pickedLng);
                            runOnUiThread(() -> {
                                updateMarker(latLng);
                                if (googleMap != null) {
                                    googleMap.animateCamera(
                                            CameraUpdateFactory.newLatLngZoom(latLng, 16f));
                                }
                                reverseGeocode(latLng);
                            });
                        }
                    }
                },
                getMainLooper());
    }

    // ── Marker helper ──────────────────────────────────────────────────────────

    private void updateMarker(LatLng latLng) {
        if (googleMap == null) return;
        googleMap.clear();
        googleMap.addMarker(new MarkerOptions()
                .position(latLng)
                .title("Incident Location"));
        tvCoordinates.setText(String.format(Locale.US,
                "Lat: %.6f   Lng: %.6f", latLng.latitude, latLng.longitude));
    }

    // ── Reverse geocode ────────────────────────────────────────────────────────

    private void reverseGeocode(LatLng latLng) {
        new Thread(() -> {
            try {
                Geocoder geocoder = new Geocoder(this, Locale.getDefault());
                List<Address> addresses =
                        geocoder.getFromLocation(latLng.latitude, latLng.longitude, 1);
                String label;
                if (addresses != null && !addresses.isEmpty()) {
                    Address addr = addresses.get(0);
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i <= addr.getMaxAddressLineIndex(); i++) {
                        if (i > 0) sb.append(", ");
                        sb.append(addr.getAddressLine(i));
                    }
                    label = sb.toString();
                } else {
                    label = String.format(Locale.US,
                            "%.5f, %.5f", latLng.latitude, latLng.longitude);
                }
                runOnUiThread(() -> tvLocation.setText(label));
            } catch (IOException e) {
                runOnUiThread(() ->
                        tvLocation.setText(String.format(Locale.US,
                                "%.5f, %.5f", latLng.latitude, latLng.longitude)));
            }
        }).start();
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Image picker
    // ══════════════════════════════════════════════════════════════════════════

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        startActivityForResult(
                Intent.createChooser(intent, "Select Photo"), PICK_IMAGE_REQUEST);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode,
                                    @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_REQUEST
                && resultCode == RESULT_OK && data != null) {
            attachedImageUri = data.getData();
            if (attachedImageUri != null) {
                long size = getUriSizeBytes(attachedImageUri);
                if (size <= 0L) {
                    attachedImageUri = null;
                    tvSelectedFile.setText("No file chosen");
                    Toast.makeText(this, "Couldn't read image size.", Toast.LENGTH_SHORT).show();
                    return;
                }
                if (size > MAX_UPLOAD_BYTES) {
                    attachedImageUri = null;
                    tvSelectedFile.setText("No file chosen");
                    Toast.makeText(this, "Upload limit is 10 MB. Please choose a smaller photo.",
                            Toast.LENGTH_LONG).show();
                    return;
                }
                String name = getUriDisplayName(attachedImageUri);
                tvSelectedFile.setText("Attached: " + (name != null ? name : "photo")
                        + " (" + formatBytes(size) + ")");
            }
        }
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Submit
    // ══════════════════════════════════════════════════════════════════════════

    private void submitIncident() {
        int checkedId = rgCategory.getCheckedRadioButtonId();
        String category;
        if      (checkedId == R.id.rbOutage)      category = "Outage";
        else if (checkedId == R.id.rbDamagedPole) category = "Damaged Pole";
        else                                       category = "Other";

        String description = etDescription.getText().toString().trim();
        if (description.isEmpty()) {
            etDescription.setError("Please describe the incident.");
            return;
        }

        // Build location string: "address (lat, lng)"
        String addressText = tvLocation.getText().toString();
        String location = addressText
                + " ("
                + String.format(Locale.US, "%.6f", pickedLat)
                + ", "
                + String.format(Locale.US, "%.6f", pickedLng)
                + ")";

        long userId = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                .getLong("user_id", -1);
        if (userId <= 0) {
            Toast.makeText(this, "No logged-in user.", Toast.LENGTH_SHORT).show();
            return;
        }

        btnSubmit.setEnabled(false);
        btnSubmit.setText("Submitting...");

        ApiServices api = RetrofitClient.getApiService();
        Call<SimpleResponse> call;
        if (attachedImageUri == null) {
            call = api.reportIncident(userId, category, description, location);
        } else {
            MultipartBody.Part imagePart;
            try {
                imagePart = buildImagePart(attachedImageUri);
            } catch (IOException e) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Report");
                Toast.makeText(this, "Failed to read photo: " + e.getMessage(),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            call = api.reportIncidentWithImage(userId, category, description, location, imagePart);
        }

        call.enqueue(new Callback<SimpleResponse>() {
            @Override
            public void onResponse(@NonNull Call<SimpleResponse> call,
                                   @NonNull Response<SimpleResponse> response) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Report");
                if (!response.isSuccessful() || response.body() == null) {
                    Toast.makeText(IncidentReportsActivity.this,
                            "Server error: " + response.code(),
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                SimpleResponse body = response.body();
                if ("success".equalsIgnoreCase(body.getStatus())) {
                    Toast.makeText(IncidentReportsActivity.this,
                            "Incident sent.", Toast.LENGTH_SHORT).show();
                    etDescription.setText("");
                    attachedImageUri = null;
                    tvSelectedFile.setText("No file chosen");
                    loadIncidentHistory();
                } else {
                    Toast.makeText(IncidentReportsActivity.this,
                            body.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(@NonNull Call<SimpleResponse> call,
                                  @NonNull Throwable t) {
                btnSubmit.setEnabled(true);
                btnSubmit.setText("Submit Report");
                Toast.makeText(IncidentReportsActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    private MultipartBody.Part buildImagePart(Uri uri) throws IOException {
        byte[] bytes = readAllBytes(uri, MAX_UPLOAD_BYTES);
        String name = getUriDisplayName(uri);
        if (name == null) name = "incident_photo";
        String mime = resolveMimeType(uri);
        if (mime == null) mime = "application/octet-stream";
        RequestBody requestBody = RequestBody.create(bytes, MediaType.parse(mime));
        return MultipartBody.Part.createFormData("files[]", name, requestBody);
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Incident history
    // ══════════════════════════════════════════════════════════════════════════

    private void addIncidentCard(IncidentItem incident) {
        LinearLayout card = (LinearLayout) getLayoutInflater()
                .inflate(R.layout.item_incident_card, containerIncidentHistory, false);

        TextView tvTitle  = card.findViewById(R.id.tvIncidentTitle);
        TextView tvAddr   = card.findViewById(R.id.tvIncidentLocation);
        TextView tvStatus = card.findViewById(R.id.tvIncidentStatus);
        TextView tvDate   = card.findViewById(R.id.tvIncidentDate);

        tvTitle.setText(incident.getCategory());
        tvAddr.setText(incident.getDescription());
        tvStatus.setText(incident.getStatus());
        tvDate.setText(incident.getReported_at());

        containerIncidentHistory.addView(card);
    }

    private void loadIncidentHistory() {
        long userId = getSharedPreferences("MyAppPrefs", MODE_PRIVATE)
                .getLong("user_id", -1);
        if (userId <= 0) return;

        ApiServices api = RetrofitClient.getApiService();
        api.getIncidents(userId).enqueue(new Callback<IncidentListResponse>() {
            @Override
            public void onResponse(@NonNull Call<IncidentListResponse> call,
                                   @NonNull Response<IncidentListResponse> response) {
                if (!response.isSuccessful() || response.body() == null) return;
                IncidentListResponse body = response.body();
                if (!"success".equalsIgnoreCase(body.getStatus())
                        || body.getIncidents() == null) return;

                containerIncidentHistory.removeAllViews();
                for (IncidentItem incident : body.getIncidents()) {
                    addIncidentCard(incident);
                }
            }

            @Override
            public void onFailure(@NonNull Call<IncidentListResponse> call,
                                  @NonNull Throwable t) {
                Toast.makeText(IncidentReportsActivity.this,
                        "Network error: " + t.getMessage(),
                        Toast.LENGTH_SHORT).show();
            }
        });
    }

    // ══════════════════════════════════════════════════════════════════════════
    //  Bottom nav
    // ══════════════════════════════════════════════════════════════════════════

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
        tvCalculator.setTextColor(grey);
        tvProfile.setTextColor(grey);
        tvIncident.setTextColor(Color.parseColor("#16A34A"));

        navHome.setOnClickListener(v ->
                startActivity(new Intent(this, DashboardActivity.class)));
        navIncident.setOnClickListener(v -> { /* already here */ });
        navCalculator.setOnClickListener(v ->
                startActivity(new Intent(this, BillingCalculatorActivity.class)));
        navProfile.setOnClickListener(v ->
                startActivity(new Intent(this, ProfileActivity.class)));
        navLogout.setOnClickListener(v -> {
            getSharedPreferences("MyAppPrefs", MODE_PRIVATE).edit().clear().apply();
            Intent i = new Intent(this, MainActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(i);
            finish();
        });
    }

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
        } catch (Exception e) {
            return -1L;
        }
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
                if (total > limitBytes) throw new IOException("Photo exceeds upload limit");
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

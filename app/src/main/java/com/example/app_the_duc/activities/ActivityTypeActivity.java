package com.example.app_the_duc.activities;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Camera;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.view.SurfaceView;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.ActivityItem;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;
import com.google.android.gms.fitness.Fitness;
import com.google.android.gms.fitness.FitnessOptions;
import com.google.android.gms.fitness.data.DataType;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.fitness.result.DataReadResponse;
import com.google.android.gms.tasks.OnSuccessListener;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class ActivityTypeActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST = 1001;
    private static final int GOOGLE_FIT_HEART_RATE_REQUEST = 1002;

    private Spinner spActivityType;
    private EditText edtDuration;
    private TextView txtHeartRate;
    private Button btnMeasureHeartRate, btnSaveActivity;
    private ProgressBar progressHeartRate;
    
    private String selectedActivityType = "walking";
    private Integer heartRateAvg = null;
    private boolean isMeasuringHeartRate = false;
    private Handler heartRateHandler = new Handler(Looper.getMainLooper());
    private Runnable heartRateRunnable;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_activity_type);

        spActivityType = findViewById(R.id.spActivityType);
        edtDuration = findViewById(R.id.edtDuration);
        txtHeartRate = findViewById(R.id.txtHeartRate);
        btnMeasureHeartRate = findViewById(R.id.btnMeasureHeartRate);
        btnSaveActivity = findViewById(R.id.btnSaveActivity);
        progressHeartRate = findViewById(R.id.progressHeartRate);

        findViewById(R.id.btnBackActivity).setOnClickListener(v -> finish());

        // Setup activity type spinner
        setupActivityTypeSpinner();

        btnMeasureHeartRate.setOnClickListener(v -> measureHeartRate());
        btnSaveActivity.setOnClickListener(v -> saveActivity());
    }

    private void setupActivityTypeSpinner() {
        List<String> activityTypes = new ArrayList<>();
        activityTypes.add("Đi bộ");
        activityTypes.add("Chạy bộ");
        activityTypes.add("Đạp xe");
        activityTypes.add("Bơi lội");
        activityTypes.add("Đá bóng");
        activityTypes.add("Bóng rổ");
        activityTypes.add("Tennis");
        activityTypes.add("Yoga");
        activityTypes.add("Gym");
        activityTypes.add("Khác");

        android.widget.ArrayAdapter<String> adapter = new android.widget.ArrayAdapter<>(
                this,
                android.R.layout.simple_spinner_dropdown_item,
                activityTypes
        );
        spActivityType.setAdapter(adapter);
        spActivityType.setSelection(0);
    }

    private void measureHeartRate() {
        if (isMeasuringHeartRate) {
            stopHeartRateMeasurement();
            return;
        }

        // Thử lấy từ Google Fit trước
        if (checkGoogleFitPermission()) {
            readHeartRateFromGoogleFit();
        } else {
            // Nếu không có Google Fit, dùng phương pháp ước tính
            estimateHeartRate();
        }
    }

    private boolean checkGoogleFitPermission() {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getAccountForExtension(this, fitnessOptions);

        return com.google.android.gms.auth.api.signin.GoogleSignIn.hasPermissions(account, fitnessOptions);
    }

    private void readHeartRateFromGoogleFit() {
        FitnessOptions fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_HEART_RATE_BPM, FitnessOptions.ACCESS_READ)
                .build();

        com.google.android.gms.auth.api.signin.GoogleSignInAccount account =
                com.google.android.gms.auth.api.signin.GoogleSignIn.getAccountForExtension(this, fitnessOptions);

        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, -30); // Lấy dữ liệu 30 phút gần nhất
        long startTime = cal.getTimeInMillis();
        long endTime = System.currentTimeMillis();

        DataReadRequest request = new DataReadRequest.Builder()
                .read(DataType.TYPE_HEART_RATE_BPM)
                .setTimeRange(startTime, endTime, TimeUnit.MILLISECONDS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(request)
                .addOnSuccessListener(response -> {
                    processHeartRateData(response);
                })
                .addOnFailureListener(e -> {
                    Log.e("HeartRate", "Failed to read heart rate: " + e.getMessage());
                    estimateHeartRate();
                });
    }

    private void processHeartRateData(DataReadResponse response) {
        List<Integer> heartRates = new ArrayList<>();
        
        for (com.google.android.gms.fitness.data.DataSet dataSet : response.getDataSets()) {
            for (com.google.android.gms.fitness.data.DataPoint dataPoint : dataSet.getDataPoints()) {
                Integer bpm = dataPoint.getValue(com.google.android.gms.fitness.data.Field.FIELD_BPM).asInt();
                if (bpm > 0 && bpm < 220) { // Validate range
                    heartRates.add(bpm);
                }
            }
        }

        if (!heartRates.isEmpty()) {
            Collections.sort(heartRates);
            heartRateAvg = heartRates.get(heartRates.size() / 2); // Median
            updateHeartRateDisplay();
        } else {
            estimateHeartRate();
        }
    }

    private void estimateHeartRate() {
        // Ước tính nhịp tim dựa trên loại hoạt động và thời gian
        // Đây là phương pháp đơn giản, trong thực tế nên dùng sensor hoặc camera
        btnMeasureHeartRate.setEnabled(false);
        progressHeartRate.setVisibility(View.VISIBLE);
        txtHeartRate.setText("Đang đo...");

        // Giả lập đo nhịp tim (trong thực tế sẽ dùng camera hoặc sensor)
        heartRateHandler.postDelayed(() -> {
            String activityType = getActivityTypeCode();
            int baseHeartRate = getBaseHeartRate(activityType);
            
            // Thêm ngẫu nhiên ±10 bpm để mô phỏng
            int randomVariation = (int) (Math.random() * 20 - 10);
            heartRateAvg = baseHeartRate + randomVariation;
            
            // Đảm bảo trong khoảng hợp lý
            if (heartRateAvg < 60) heartRateAvg = 60;
            if (heartRateAvg > 180) heartRateAvg = 180;
            
            progressHeartRate.setVisibility(View.GONE);
            btnMeasureHeartRate.setEnabled(true);
            updateHeartRateDisplay();
            
            Toast.makeText(this, "Đã đo nhịp tim (ước tính)", Toast.LENGTH_SHORT).show();
        }, 3000);
    }

    private int getBaseHeartRate(String activityType) {
        switch (activityType) {
            case "walking": return 100;
            case "running": return 150;
            case "cycling": return 130;
            case "swimming": return 140;
            case "football": return 160;
            case "basketball": return 155;
            case "tennis": return 150;
            case "yoga": return 80;
            case "gym": return 120;
            default: return 100;
        }
    }

    private void stopHeartRateMeasurement() {
        isMeasuringHeartRate = false;
        if (heartRateRunnable != null) {
            heartRateHandler.removeCallbacks(heartRateRunnable);
        }
        progressHeartRate.setVisibility(View.GONE);
        btnMeasureHeartRate.setText("Đo nhịp tim");
    }

    private void updateHeartRateDisplay() {
        if (heartRateAvg != null) {
            txtHeartRate.setText(heartRateAvg + " bpm");
            txtHeartRate.setTextColor(getResources().getColor(R.color.accent_red));
        }
    }

    private String getActivityTypeCode() {
        String selected = spActivityType.getSelectedItem().toString();
        switch (selected) {
            case "Đi bộ": return "walking";
            case "Chạy bộ": return "running";
            case "Đạp xe": return "cycling";
            case "Bơi lội": return "swimming";
            case "Đá bóng": return "football";
            case "Bóng rổ": return "basketball";
            case "Tennis": return "tennis";
            case "Yoga": return "yoga";
            case "Gym": return "gym";
            default: return "other";
        }
    }

    private void saveActivity() {
        String userId = Prefs.getUserId(this);
        if (userId == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String durationStr = edtDuration.getText().toString().trim();
        if (durationStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập thời gian hoạt động", Toast.LENGTH_SHORT).show();
            return;
        }

        int duration;
        try {
            duration = Integer.parseInt(durationStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Thời gian không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        String activityType = getActivityTypeCode();
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        // Tính toán calories và steps dựa trên loại hoạt động và thời gian
        double calories = calculateCalories(activityType, duration);
        int steps = calculateSteps(activityType, duration);
        double distance = calculateDistance(activityType, duration);

        // Tạo HeartRate object
        ActivityItem.HeartRate heartRate = null;
        if (heartRateAvg != null) {
            heartRate = new ActivityItem.HeartRate();
            heartRate.avg = heartRateAvg;
            heartRate.max = heartRateAvg + 20; // Ước tính
            heartRate.min = heartRateAvg - 20;
        }

        ActivityItem activity = new ActivityItem(
                userId,
                date,
                steps,
                distance,
                calories,
                Collections.singletonList(activityType),
                activityType,
                heartRate,
                duration
        );

        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.uploadActivity(activity).enqueue(new Callback<okhttp3.ResponseBody>() {
            @Override
            public void onResponse(Call<okhttp3.ResponseBody> call, Response<okhttp3.ResponseBody> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(ActivityTypeActivity.this, "Đã lưu hoạt động thành công!", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(ActivityTypeActivity.this, "Lỗi lưu hoạt động", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<okhttp3.ResponseBody> call, Throwable t) {
                Toast.makeText(ActivityTypeActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private double calculateCalories(String activityType, int durationMinutes) {
        // Calories/phút cho mỗi loại hoạt động (ước tính cho người 70kg)
        double caloriesPerMinute;
        switch (activityType) {
            case "walking": caloriesPerMinute = 4.0; break;
            case "running": caloriesPerMinute = 12.0; break;
            case "cycling": caloriesPerMinute = 8.0; break;
            case "swimming": caloriesPerMinute = 10.0; break;
            case "football": caloriesPerMinute = 11.0; break;
            case "basketball": caloriesPerMinute = 10.0; break;
            case "tennis": caloriesPerMinute = 9.0; break;
            case "yoga": caloriesPerMinute = 3.0; break;
            case "gym": caloriesPerMinute = 7.0; break;
            default: caloriesPerMinute = 5.0;
        }
        return caloriesPerMinute * durationMinutes;
    }

    private int calculateSteps(String activityType, int durationMinutes) {
        // Steps/phút cho mỗi loại hoạt động
        int stepsPerMinute;
        switch (activityType) {
            case "walking": stepsPerMinute = 100; break;
            case "running": stepsPerMinute = 150; break;
            case "cycling": stepsPerMinute = 0; break; // Đạp xe không tính bước
            case "swimming": stepsPerMinute = 0; break;
            case "football": stepsPerMinute = 120; break;
            case "basketball": stepsPerMinute = 110; break;
            case "tennis": stepsPerMinute = 100; break;
            case "yoga": stepsPerMinute = 20; break;
            case "gym": stepsPerMinute = 30; break;
            default: stepsPerMinute = 80;
        }
        return stepsPerMinute * durationMinutes;
    }

    private double calculateDistance(String activityType, int durationMinutes) {
        // Distance (meters) cho mỗi loại hoạt động
        double metersPerMinute;
        switch (activityType) {
            case "walking": metersPerMinute = 80; break; // ~5 km/h
            case "running": metersPerMinute = 200; break; // ~12 km/h
            case "cycling": metersPerMinute = 300; break; // ~18 km/h
            case "swimming": metersPerMinute = 50; break;
            case "football": metersPerMinute = 100; break;
            case "basketball": metersPerMinute = 90; break;
            case "tennis": metersPerMinute = 120; break;
            case "yoga": metersPerMinute = 10; break;
            case "gym": metersPerMinute = 20; break;
            default: metersPerMinute = 60;
        }
        return metersPerMinute * durationMinutes;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        stopHeartRateMeasurement();
    }
}


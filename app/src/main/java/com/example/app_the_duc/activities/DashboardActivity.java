package com.example.app_the_duc.activities;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.animation.DecelerateInterpolator;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.ActivityItem;
import com.example.app_the_duc.models.Goal;
import com.example.app_the_duc.models.InsightResponse;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;
import com.google.android.gms.auth.api.signin.*;
import com.google.android.gms.fitness.*;
import com.google.android.gms.fitness.data.*;
import com.google.android.gms.fitness.request.DataReadRequest;
import com.google.android.gms.tasks.*;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.TimeUnit;

public class DashboardActivity extends AppCompatActivity implements SensorEventListener {

    // ---------------- Google Fit ----------------
    private static final int GOOGLE_FIT_PERMISSIONS_REQUEST = 1001;

    // ---------------- Sensors fallback ----------------
    private SensorManager sensorManager;
    private int videoStepsOffset = 0;
    private double videoCaloriesOffset = 0;
    private double videoDistanceOffset = 0;
    private Sensor stepSensor;
    private Sensor accelSensor;
    private FitnessOptions fitnessOptions;
    private boolean useAccelerometer = false;
    private double previousMagnitude = 0;
    private int stepCount = 0;
    private float previousTotalSteps = 0f;
    private boolean resetBaselinePending = false;
    private final SimpleDateFormat dayFmt = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    // ---------------- UI ----------------
    private TextView txtSteps, txtDistance, txtRingCenter;
    private ProgressBar progressCircle;
    private BarChart barChart;
    private TextView txtGoalCal, txtGoalPeriod;

    private String lastDate = "";
    private String userId;

    private static final int UPLOAD_THRESHOLD = 20;
    private int targetSteps = 10000;

    // ---------------- onCreate ----------------
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_dashboard);

        txtSteps = findViewById(R.id.txtSteps);
        txtDistance = findViewById(R.id.txtDistance);
        txtRingCenter = findViewById(R.id.txtRingCenter);
        progressCircle = findViewById(R.id.progressCircle);
        barChart = findViewById(R.id.barChart);
        txtGoalCal = findViewById(R.id.txtGoalCal);
        txtGoalPeriod = findViewById(R.id.txtGoalPeriod);

        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            Prefs.clear(this);
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
        });

        findViewById(R.id.btnWorkoutAI).setOnClickListener(v -> {
            startActivity(new Intent(this, WorkoutActivity.class));
        });

        findViewById(R.id.btnGoal).setOnClickListener(v -> startActivity(new Intent(this, GoalActivity.class)));
        findViewById(R.id.btnChat).setOnClickListener(v -> startActivity(new Intent(this, ChatActivity.class)));
        
        // Nút ghi nhận hoạt động mới
        View btnActivityType = findViewById(R.id.btnActivityType);
        if (btnActivityType != null) {
            btnActivityType.setOnClickListener(v -> startActivity(new Intent(this, com.example.app_the_duc.activities.ActivityTypeActivity.class)));
        }
        
        // Nút xu hướng - nếu có trong layout
        View btnTrend = findViewById(R.id.btnTrend);
        if (btnTrend != null) {
            btnTrend.setOnClickListener(v -> startActivity(new Intent(this, TrendActivity.class)));
        }
        
        // Nút profile - nếu có trong layout
        View btnProfile = findViewById(R.id.btnProfile);
        if (btnProfile != null) {
            btnProfile.setOnClickListener(v -> startActivity(new Intent(this, ProfileActivity.class)));
        }

        userId = Prefs.getUserId(this);
        if (userId == null) userId = "anonymous";

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (stepSensor == null) {
            useAccelerometer = true;
            Toast.makeText(this, "Thiết bị không có Step Counter -> dùng Accelerometer", Toast.LENGTH_LONG).show();
        }

        loadData();
        checkNewDay();

        // Check permission
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACTIVITY_RECOGNITION},
                    999
            );
        }

        setupChart();
        fetchLast7DaysAndDraw();
        fetchGoalAndApply();
        fetchAnalyticsAndShow();

        // 🔥 REQUEST GOOGLE FIT PERMISSION
        requestGoogleFitPermissions();
        IntentFilter filter = new IntentFilter("UPDATE_ACTIVITY_FROM_VIDEO");

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            registerReceiver(videoUpdateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            registerReceiver(videoUpdateReceiver, filter);
        }
    }

    private final BroadcastReceiver videoUpdateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            try {
                int addSteps = intent.getIntExtra("stepsFromVideo", 0);
                double addCalories = intent.getDoubleExtra("caloriesFromVideo", 0);

                if (addSteps <= 0 && addCalories <= 0) {
                    return; // Không có dữ liệu hợp lệ
                }

                Log.d("Dashboard", "Received broadcast: " + addSteps + " steps, " + addCalories + " calories");

                // tăng bước
                stepCount += addSteps;

                videoStepsOffset += addSteps;
                videoCaloriesOffset += addCalories;
                // 1 step ~ 0.8m -> cộng dồn quãng đường video để upload đúng
                videoDistanceOffset += addSteps * 0.8;

                // Đánh dấu ngày hiện tại để không bị reset khi mở lại app
                lastDate = dayFmt.format(new Date());

                // LƯU lại ngay để Google Fit không ghi đè
                saveData();

                // Cập nhật UI (kiểm tra null)
                if (txtSteps != null) {
                    txtSteps.setText(String.valueOf(stepCount));
                }

                if (txtDistance != null) {
                    double km = stepCount * 0.0008;
                    txtDistance.setText(String.format(Locale.getDefault(), "%.2f km", km));
                }

                if (txtRingCenter != null) {
                    // Tính calories: sensor steps + video calories
                    int sensorSteps = Math.max(0, stepCount - videoStepsOffset);
                    double totalCalories = sensorSteps * 0.04 + videoCaloriesOffset;
                    txtRingCenter.setText(String.format(Locale.getDefault(), "%d CAL", (int) totalCalories));
                }

                if (progressCircle != null && targetSteps > 0) {
                    int percent = (int) Math.min(100, stepCount * 100.0 / targetSteps);
                    progressCircle.setProgress(percent);
                }

                fetchLast7DaysAndDraw();
            } catch (Exception e) {
                Log.e("Dashboard", "Error in broadcast receiver: " + e.getMessage());
                e.printStackTrace();
            }
        }
    };

    // ---------------- Google Fit: Request Permission ----------------
    private void requestGoogleFitPermissions() {

        fitnessOptions = FitnessOptions.builder()
                .addDataType(DataType.TYPE_STEP_COUNT_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_STEP_COUNT_CUMULATIVE, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_DISTANCE_DELTA, FitnessOptions.ACCESS_READ)
                .addDataType(DataType.TYPE_CALORIES_EXPENDED, FitnessOptions.ACCESS_READ)
                .build();

        GoogleSignInAccount account =
                GoogleSignIn.getAccountForExtension(this, fitnessOptions);

        if (!GoogleSignIn.hasPermissions(account, fitnessOptions)) {

            GoogleSignIn.requestPermissions(
                    this,
                    GOOGLE_FIT_PERMISSIONS_REQUEST,
                    account,
                    fitnessOptions
            );

        } else {
            readGoogleFitToday();   // quyền đã OK
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == GOOGLE_FIT_PERMISSIONS_REQUEST) {

            if (resultCode == RESULT_OK) {

                GoogleSignInAccount account =
                        GoogleSignIn.getAccountForExtension(this, fitnessOptions);

                if (GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                    readGoogleFitToday();
                } else {
                    Toast.makeText(this, "Không cấp quyền Google Fit", Toast.LENGTH_SHORT).show();
                }

            } else {
                Toast.makeText(this, "Bạn đã hủy quyền Google Fit", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ---------------- Google Fit: Read today's activity ----------------
    private void readGoogleFitToday() {
        try {
            if (fitnessOptions == null) {
                return; // Chưa khởi tạo fitnessOptions
            }

            GoogleSignInAccount account =
                    GoogleSignIn.getAccountForExtension(this, fitnessOptions);

            if (account == null || !GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                // Không hiển thị toast để tránh spam, chỉ log
                Log.d("FIT", "Google Fit chưa đăng nhập hoặc chưa có quyền");
                return;
            }

        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        long start = cal.getTimeInMillis();
        long end = start + 24 * 60 * 60 * 1000;

        DataReadRequest request = new DataReadRequest.Builder()
                .aggregate(DataType.TYPE_STEP_COUNT_DELTA, DataType.AGGREGATE_STEP_COUNT_DELTA)
                .aggregate(DataType.TYPE_DISTANCE_DELTA, DataType.AGGREGATE_DISTANCE_DELTA)
                .aggregate(DataType.TYPE_CALORIES_EXPENDED, DataType.AGGREGATE_CALORIES_EXPENDED)
                .setTimeRange(start, end, TimeUnit.MILLISECONDS)
                .bucketByTime(1, TimeUnit.DAYS)
                .build();

        Fitness.getHistoryClient(this, account)
                .readData(request)
                .addOnSuccessListener(response -> {
                    long steps = 0;
                    float distance = 0f;
                    float calories = 0f;

                    for (Bucket bucket : response.getBuckets()) {

                        for (DataSet ds : bucket.getDataSets()) {
                            for (DataPoint dp : ds.getDataPoints()) {
                                for (Field f : dp.getDataType().getFields()) {

                                    if (f.equals(Field.FIELD_STEPS)) {
                                        steps += dp.getValue(f).asInt();
                                    }

                                    if (f.equals(Field.FIELD_DISTANCE)) {
                                        distance += dp.getValue(f).asFloat();
                                    }

                                    if (f.equals(Field.FIELD_CALORIES)) {
                                        calories += dp.getValue(f).asFloat();
                                    }
                                }
                            }
                        }
                    }

                    updateUIWithGoogleFit(steps, distance, calories);

                    uploadToServer((int) steps, distance, calories);
                })
                .addOnFailureListener(e -> {
                    Log.e("FIT", "Google Fit read fail: " + e.getMessage());
                    // Không crash app, chỉ log lỗi và tiếp tục dùng sensor fallback
                });
        } catch (Exception e) {
            Log.e("FIT", "Error reading Google Fit: " + e.getMessage());
            e.printStackTrace();
        }
    }


    private void updateUIWithGoogleFit(long steps, float distance, float calories) {
        try {
            // Kiểm tra null
            if (txtSteps == null || txtDistance == null || txtRingCenter == null || progressCircle == null) {
                return;
            }

            Log.d("Dashboard", "updateUIWithGoogleFit - Google Fit steps: " + steps + ", videoStepsOffset: " + videoStepsOffset);
            
            // tổng bước = bước google + bước video lưu
            this.stepCount = (int) steps + videoStepsOffset;
            
            Log.d("Dashboard", "updateUIWithGoogleFit - Total stepCount after: " + stepCount);

            txtSteps.setText(String.valueOf(stepCount));
            double totalDistanceKm = (distance + videoDistanceOffset) / 1000f;
            txtDistance.setText(String.format(Locale.getDefault(), "%.2f km", totalDistanceKm));
            double totalCalories = calories + videoCaloriesOffset;
            txtRingCenter.setText(String.format(Locale.getDefault(), "%d CAL", (int) totalCalories));

            int percent = targetSteps > 0 ? (int) Math.min(100, stepCount * 100 / targetSteps) : 0;
            progressCircle.setProgress(percent);

            saveData();
        } catch (Exception e) {
            Log.e("Dashboard", "Error updating UI with Google Fit: " + e.getMessage());
            e.printStackTrace();
        }
    }


    // ---------------- Sensor Fallback ----------------
    @Override
    protected void onResume() {
        super.onResume();
        loadData();
        
        // Log để debug
        Log.d("Dashboard", "onResume - stepCount: " + stepCount + ", videoStepsOffset: " + videoStepsOffset + ", videoCaloriesOffset: " + videoCaloriesOffset);
        
        // Hiển thị ngay dữ liệu đang lưu (bao gồm offset video) trước khi gọi Google Fit
        renderFromSavedData();
        
        if (useAccelerometer)
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        else if (stepSensor != null)
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);

        fetchGoalAndApply();
        fetchLast7DaysAndDraw();
        
        // Chỉ gọi Google Fit nếu có quyền, không ghi đè dữ liệu đã lưu
        if (fitnessOptions != null) {
            GoogleSignInAccount account = GoogleSignIn.getAccountForExtension(this, fitnessOptions);
            if (account != null && GoogleSignIn.hasPermissions(account, fitnessOptions)) {
                readGoogleFitToday();
            }
        }
    }

    private void renderFromSavedData() {
        try {
            // Kiểm tra null để tránh crash
            if (txtSteps == null || txtDistance == null || txtRingCenter == null || progressCircle == null) {
                return;
            }

            // QUAN TRỌNG: Đảm bảo stepCount bao gồm cả videoStepsOffset
            // Nếu stepCount < videoStepsOffset, có nghĩa là stepCount chưa bao gồm video
            // (có thể do broadcast không được nhận hoặc ngày mới reset)
            int totalSteps = stepCount;
            if (videoStepsOffset > 0 && stepCount < videoStepsOffset) {
                // stepCount chưa bao gồm video, cộng thêm
                totalSteps = stepCount + videoStepsOffset;
                Log.d("Dashboard", "renderFromSavedData - stepCount < videoStepsOffset, cộng thêm: " + totalSteps);
            }
            
            txtSteps.setText(String.valueOf(totalSteps));

            // Tính distance: totalSteps * 0.0008 km
            double totalDistanceKm = totalSteps * 0.0008;
            txtDistance.setText(String.format(Locale.getDefault(), "%.2f km", totalDistanceKm));

            // Calories: tránh double count
            // Nếu stepCount đã bao gồm video: totalSteps = stepCount (đã có video)
            // Calories = (totalSteps - videoStepsOffset) * 0.04 + videoCaloriesOffset
            // Nếu stepCount chưa bao gồm video: totalSteps = stepCount + videoStepsOffset
            // Calories = stepCount * 0.04 + videoCaloriesOffset
            double totalCalories;
            if (videoStepsOffset > 0 && videoCaloriesOffset > 0) {
                if (stepCount >= videoStepsOffset) {
                    // stepCount đã bao gồm video
                    int sensorSteps = stepCount - videoStepsOffset;
                    totalCalories = sensorSteps * 0.04 + videoCaloriesOffset;
                } else {
                    // stepCount chưa bao gồm video
                    totalCalories = stepCount * 0.04 + videoCaloriesOffset;
                }
            } else {
                // Fallback: tính từ totalSteps nếu chưa có video data
                totalCalories = totalSteps * 0.04;
            }
            txtRingCenter.setText(String.format(Locale.getDefault(), "%d CAL", (int) totalCalories));

            int percent = targetSteps > 0 ? (int) Math.min(100, totalSteps * 100.0 / targetSteps) : 0;
            progressCircle.setProgress(percent);
            
            Log.d("Dashboard", "renderFromSavedData - totalSteps: " + totalSteps + ", calories: " + totalCalories);
        } catch (Exception e) {
            Log.e("Dashboard", "Error rendering saved data: " + e.getMessage());
            e.printStackTrace();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        saveData();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(videoUpdateReceiver);
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (!useAccelerometer && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            if (resetBaselinePending) {
                previousTotalSteps = event.values[0];
                stepCount = 0;
                resetBaselinePending = false;
                saveData();
            }
            if (previousTotalSteps == 0) {
                previousTotalSteps = event.values[0];
                saveData();
            }

            stepCount = (int) (event.values[0] - previousTotalSteps);
            onStepUpdatedFallback();
        }
        else if (useAccelerometer && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double mag = Math.sqrt(x * x + y * y + z * z);
            double delta = mag - previousMagnitude;
            previousMagnitude = mag;

            if (delta > 4.0) {
                stepCount++;
                onStepUpdatedFallback();
            }
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void onStepUpdatedFallback() {
        checkNewDay();

        double distanceKm = stepCount * 0.0008;
        double calories = stepCount * 0.04;

        txtSteps.setText(String.valueOf(stepCount));
        txtDistance.setText(String.format(Locale.getDefault(), "%.2f km", distanceKm));
        txtRingCenter.setText(String.format(Locale.getDefault(), "%d CAL", (int) calories));

        int percent = (int) Math.min(100, (stepCount * 100.0 / targetSteps));
        progressCircle.setProgress(percent);

        saveData();

        if (stepCount > 0 && stepCount % UPLOAD_THRESHOLD == 0) {
            uploadToServer(stepCount, distanceKm * 1000, calories);
        }
    }

    // ---------------- Save State ----------------
    private void checkNewDay() {
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        if (!today.equals(lastDate)) {
            lastDate = today;
            resetBaselinePending = true;
            stepCount = 0;
            saveData();
        }
    }

    private void loadData() {
        SharedPreferences prefs = getSharedPreferences("myPrefs", MODE_PRIVATE);
        videoStepsOffset = prefs.getInt("videoStepsOffset", 0);
        videoCaloriesOffset = Double.longBitsToDouble(prefs.getLong("videoCaloriesOffset", 0));
        videoDistanceOffset = Double.longBitsToDouble(prefs.getLong("videoDistanceOffset", 0));
        previousTotalSteps = prefs.getFloat("previousTotalSteps", 0f);
        lastDate = prefs.getString("lastDate", "");
        stepCount = prefs.getInt("stepCount", 0);
        resetBaselinePending = prefs.getBoolean("resetBaselinePending", false);

        // Nếu chưa có ngày lưu, gán ngày hôm nay để tránh bị reset mất bước video
        if (lastDate == null || lastDate.isEmpty()) {
            lastDate = dayFmt.format(new Date());
            saveData();
        }
        
        // Log để debug
        Log.d("Dashboard", "loadData - stepCount: " + stepCount + ", videoStepsOffset: " + videoStepsOffset + ", videoCaloriesOffset: " + videoCaloriesOffset);
        
        // QUAN TRỌNG: Đảm bảo stepCount bao gồm cả videoStepsOffset nếu chưa có
        // Nếu stepCount < videoStepsOffset, có nghĩa là stepCount chưa bao gồm video
        // Trong trường hợp này, không tự động cộng vì có thể stepCount là từ sensor/Google Fit
        // Chỉ cộng khi chắc chắn stepCount không bao gồm video (ví dụ: stepCount = 0 và videoStepsOffset > 0)
        if (stepCount == 0 && videoStepsOffset > 0) {
            // Nếu stepCount = 0 nhưng có video steps, có thể là ngày mới hoặc chưa có sensor data
            // Không tự động cộng vì có thể gây double count
            Log.d("Dashboard", "Warning: stepCount=0 but videoStepsOffset=" + videoStepsOffset);
        }
    }


    private void saveData() {
        SharedPreferences.Editor editor = getSharedPreferences("myPrefs", MODE_PRIVATE).edit();
        editor.putFloat("previousTotalSteps", previousTotalSteps);
        editor.putString("lastDate", lastDate);
        editor.putInt("stepCount", stepCount);
        editor.putBoolean("resetBaselinePending", resetBaselinePending);
        editor.putInt("videoStepsOffset", videoStepsOffset);
        editor.putLong("videoCaloriesOffset", Double.doubleToRawLongBits(videoCaloriesOffset));
        editor.putLong("videoDistanceOffset", Double.doubleToRawLongBits(videoDistanceOffset));
        editor.apply();
    }

    // ---------------- Upload to server ----------------
    private void uploadToServer(int steps, double distanceM, double calories) {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());

        ActivityItem item = new ActivityItem(
                userId,
                date,
                steps + videoStepsOffset,
                distanceM + videoDistanceOffset,
                calories + videoCaloriesOffset,
                Collections.singletonList("google_fit")
        );

        api.uploadActivity(item).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> res) {
                if (res.isSuccessful()) {
                    Log.d("API", "Uploaded!");
                    fetchLast7DaysAndDraw();
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("API", "Upload fail: " + t.getMessage());
            }
        });
    }

    // ---------------- Charts (unchanged) ----------------
    private void setupChart() {
        barChart.getDescription().setEnabled(false);
        barChart.setDrawGridBackground(false);
        XAxis x = barChart.getXAxis();
        x.setPosition(XAxis.XAxisPosition.BOTTOM);
        x.setDrawGridLines(false);
        x.setGranularity(1f);
        barChart.getAxisRight().setEnabled(false);
    }

    private void fetchLast7DaysAndDraw() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getActivities(userId).enqueue(new Callback<List<ActivityItem>>() {
            @Override
            public void onResponse(Call<List<ActivityItem>> call, Response<List<ActivityItem>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ActivityItem> activities = response.body();
                    drawBarChart(activities);
                    calculateWeeklyStats(activities);
                }
            }
            @Override
            public void onFailure(Call<List<ActivityItem>> call, Throwable t) {}
        });
    }
    
    private void calculateWeeklyStats(List<ActivityItem> activities) {
        if (activities == null || activities.isEmpty()) return;
        
        // Tính tổng bước và calo trong 7 ngày gần nhất
        int weeklySteps = 0;
        double weeklyCalories = 0;
        int activeDays = 0;
        int streakDays = 0;
        double totalDistance = 0;
        
        // Lấy 7 ngày gần nhất
        Map<String, ActivityItem> dailyMap = new LinkedHashMap<>();
        for (ActivityItem item : activities) {
            if (item.date != null) {
                ActivityItem existing = dailyMap.get(item.date);
                if (existing == null || item.steps > existing.steps) {
                    dailyMap.put(item.date, item);
                }
            }
        }
        
        List<String> sortedDates = new ArrayList<>(dailyMap.keySet());
        Collections.sort(sortedDates, Collections.reverseOrder());
        List<String> last7Days = sortedDates.subList(0, Math.min(7, sortedDates.size()));
        
        // Thống kê các loại hoạt động
        Map<String, Integer> activityTypeCount = new HashMap<>();
        int totalHeartRate = 0;
        int heartRateCount = 0;
        
        for (String date : last7Days) {
            ActivityItem item = dailyMap.get(date);
            if (item != null) {
                weeklySteps += item.steps;
                weeklyCalories += item.calories;
                totalDistance += item.distance_m; // distance_m là double primitive, không thể null
                
                if (item.steps > 0) activeDays++;
                
                // Đếm loại hoạt động
                if (item.activityType != null && !item.activityType.isEmpty()) {
                    activityTypeCount.put(item.activityType, 
                        activityTypeCount.getOrDefault(item.activityType, 0) + 1);
                }
                
                // Tính nhịp tim trung bình
                if (item.heartRate != null && item.heartRate.avg != null) {
                    totalHeartRate += item.heartRate.avg;
                    heartRateCount++;
                }
            }
        }
        
        // Tính streak (ngày liên tiếp đạt mục tiêu ≥8000 bước)
        for (String date : last7Days) {
            ActivityItem item = dailyMap.get(date);
            if (item != null && item.steps >= 8000) {
                streakDays++;
            } else {
                break; // streak bị ngắt
            }
        }
        
        // Tính tốc độ trung bình (giả sử mỗi ngày hoạt động 1 giờ)
        double avgSpeed = 0;
        if (activeDays > 0 && totalDistance > 0) {
            double totalHours = activeDays * 1.0;
            avgSpeed = (totalDistance / 1000.0) / totalHours; // km/h
        }
        
        // Tính thời gian hoạt động (ước tính từ steps: 100 bước/phút)
        int activeMinutes = (int) (weeklySteps / 100.0);
        
        // Cập nhật UI
        TextView txtWeeklySteps = findViewById(R.id.txtWeeklySteps);
        if (txtWeeklySteps != null) {
            txtWeeklySteps.setText(String.format(Locale.getDefault(), "%,d", weeklySteps));
        }
        
        TextView txtWeeklyCalories = findViewById(R.id.txtWeeklyCalories);
        if (txtWeeklyCalories != null) {
            txtWeeklyCalories.setText(String.format(Locale.getDefault(), "%,.0f", weeklyCalories));
        }
        
        TextView txtStreakDays = findViewById(R.id.txtStreakDays);
        if (txtStreakDays != null) {
            txtStreakDays.setText("🔥 Chuỗi ngày: " + streakDays + " ngày");
        }
        
        TextView txtAvgSpeed = findViewById(R.id.txtAvgSpeed);
        if (txtAvgSpeed != null) {
            txtAvgSpeed.setText(String.format(Locale.getDefault(), "%.1f km/h", avgSpeed));
        }
        
        TextView txtActiveTime = findViewById(R.id.txtActiveTime);
        if (txtActiveTime != null) {
            txtActiveTime.setText(activeMinutes + " phút");
        }
        
        // Hiển thị nhịp tim trung bình
        TextView txtHeartRateAvg = findViewById(R.id.txtHeartRateAvg);
        if (txtHeartRateAvg != null) {
            if (heartRateCount > 0) {
                int avgHeartRate = totalHeartRate / heartRateCount;
                txtHeartRateAvg.setText("❤️ Nhịp tim TB: " + avgHeartRate + " bpm");
            } else {
                txtHeartRateAvg.setText("❤️ Nhịp tim TB: Chưa có dữ liệu");
            }
        }
        
        // Hiển thị các loại hoạt động
        TextView txtActivityTypes = findViewById(R.id.txtActivityTypes);
        if (txtActivityTypes != null) {
            if (!activityTypeCount.isEmpty()) {
                StringBuilder sb = new StringBuilder("📊 Hoạt động: ");
                List<Map.Entry<String, Integer>> sorted = new ArrayList<>(activityTypeCount.entrySet());
                sorted.sort((a, b) -> b.getValue().compareTo(a.getValue()));
                
                for (int i = 0; i < Math.min(3, sorted.size()); i++) {
                    if (i > 0) sb.append(", ");
                    String type = sorted.get(i).getKey();
                    sb.append(getActivityName(type)).append(" (").append(sorted.get(i).getValue()).append(")");
                }
                txtActivityTypes.setText(sb.toString());
            } else {
                txtActivityTypes.setText("📊 Hoạt động: Chưa có dữ liệu");
            }
        }
    }
    
    private String getActivityName(String activityType) {
        switch (activityType) {
            case "walking": return "Đi bộ";
            case "running": return "Chạy bộ";
            case "cycling": return "Đạp xe";
            case "swimming": return "Bơi lội";
            case "football": return "Đá bóng";
            case "basketball": return "Bóng rổ";
            case "tennis": return "Tennis";
            case "yoga": return "Yoga";
            case "gym": return "Gym";
            default: return "Khác";
        }
    }

    private void drawBarChart(List<ActivityItem> items) {
        Map<String, Integer> grouped = new LinkedHashMap<>();
        for (ActivityItem it : items) {
            if (it.date == null) continue;
            Integer cur = grouped.get(it.date);
            if (cur == null || it.steps > cur) grouped.put(it.date, it.steps);
        }

        List<String> allDates = new ArrayList<>(grouped.keySet());
        Collections.sort(allDates, (a, b) -> b.compareTo(a));
        List<String> last7 = allDates.subList(0, Math.min(7, allDates.size()));
        Collections.reverse(last7);

        List<BarEntry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < last7.size(); i++) {
            String d = last7.get(i);
            entries.add(new BarEntry(i, grouped.get(d)));
            labels.add(d.substring(5));
        }

        BarDataSet set = new BarDataSet(entries, "Bước/ngày");
        set.setColor(getResources().getColor(R.color.accent_red));

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        barChart.setData(data);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        barChart.invalidate();
    }

    // ---------------- Goals ---------------- 
    private void fetchGoalAndApply() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getGoals().enqueue(new Callback<List<Goal>>() {
            @Override
            public void onResponse(Call<List<Goal>> call, Response<List<Goal>> res) {
                if (!res.isSuccessful() || res.body() == null) return;

                Goal last = null;
                for (Goal g : res.body()) if (userId.equals(g.userId)) last = g;

                if (last != null && last.stepGoal != null) {
                    targetSteps = last.stepGoal;
                    
                    // Hiển thị mục tiêu bước
                    TextView sub = findViewById(R.id.txtRingSubtitle);
                    if (sub != null) {
                        sub.setText("Mục tiêu: " + targetSteps + " bước/ngày");
                    }
                    
                    // Hiển thị mục tiêu calo
                    TextView txtGoalCal = findViewById(R.id.txtGoalCal);
                    if (txtGoalCal != null && last.calGoal != null) {
                        txtGoalCal.setText("🔥 Calo: " + last.calGoal + " kcal/ngày");
                    } else if (txtGoalCal != null) {
                        txtGoalCal.setText("🔥 Calo: Chưa đặt mục tiêu");
                    }
                    
                    // Hiển thị thời gian kế hoạch
                    TextView txtGoalPeriod = findViewById(R.id.txtGoalPeriod);
                    if (txtGoalPeriod != null) {
                        StringBuilder periodText = new StringBuilder("📅 ");
                        if (last.planStart != null && !last.planStart.isEmpty()) {
                            periodText.append("Bắt đầu: ").append(formatDate(last.planStart));
                        }
                        if (last.planEnd != null && !last.planEnd.isEmpty()) {
                            if (periodText.length() > 3) periodText.append(" | ");
                            periodText.append("Kết thúc: ").append(formatDate(last.planEnd));
                        }
                        if (periodText.length() == 3) {
                            periodText.append("Chưa đặt thời gian");
                        }
                        txtGoalPeriod.setText(periodText.toString());
                    }
                } else {
                    // Không có goal
                    TextView txtGoalCal = findViewById(R.id.txtGoalCal);
                    if (txtGoalCal != null) {
                        txtGoalCal.setText("🔥 Calo: Chưa đặt mục tiêu");
                    }
                    TextView txtGoalPeriod = findViewById(R.id.txtGoalPeriod);
                    if (txtGoalPeriod != null) {
                        txtGoalPeriod.setText("📅 Chưa đặt thời gian kế hoạch");
                    }
                }
            }
            @Override
            public void onFailure(Call<List<Goal>> call, Throwable t) {}
        });
    }
    
    private String formatDate(String dateStr) {
        try {
            // Format từ "2025-01-15" thành "15/01/2025"
            if (dateStr != null && dateStr.length() >= 10) {
                return dateStr.substring(8, 10) + "/" + dateStr.substring(5, 7) + "/" + dateStr.substring(0, 4);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return dateStr;
    }

    // ---------------- Analytics ----------------
    private void fetchAnalyticsAndShow() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("userProfile", new HashMap<>());

        api.analyze(body).enqueue(new Callback<InsightResponse>() {
            @Override
            public void onResponse(Call<InsightResponse> call, Response<InsightResponse> res) {
                if (res.isSuccessful() && res.body() != null) {

                    TextView tv = findViewById(R.id.txtTrendContent);
                    if (tv != null) {

                        StringBuilder sb = new StringBuilder();

                        // 🔥 lấy trendSummary
                        if (res.body().trendSummary != null) {
                            sb.append(res.body().trendSummary).append("\n\n");
                        }

                        // AI overview
                        if (res.body().insight != null && res.body().insight.overview != null) {
                            sb.append(res.body().insight.overview).append("\n\n");
                        }

                        // AI suggestions
                        if (res.body().insight != null && res.body().insight.suggestions != null) {
                            for (String s : res.body().insight.suggestions)
                                sb.append("• ").append(s).append("\n");
                        }

                        tv.setText(sb.toString());
                    }
                }
            }

            @Override
            public void onFailure(Call<InsightResponse> call, Throwable t) {}
        });
    }
}

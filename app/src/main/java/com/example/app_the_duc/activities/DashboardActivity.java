package com.example.app_the_duc.activities;

import android.Manifest;
import android.animation.ValueAnimator;
import android.content.SharedPreferences;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
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
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;
import com.github.mikephil.charting.charts.BarChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.BarData;
import com.github.mikephil.charting.data.BarDataSet;
import com.github.mikephil.charting.data.BarEntry;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class DashboardActivity extends AppCompatActivity implements SensorEventListener {
    private SensorManager sensorManager;
    private Sensor stepSensor;
    private Sensor accelSensor;
    private boolean useAccelerometer = false;

    private double previousMagnitude = 0;
    private int stepCount = 0;
    private float previousTotalSteps = 0f;
    private boolean resetBaselinePending = false;

    private TextView txtSteps, txtDistance, txtRingCenter;
    private TextView txtGoalCal, txtGoalPeriod;
    private ProgressBar progressCircle;
    private BarChart barChart;
    private String lastDate = "";
    private String userId;

    private static final int UPLOAD_THRESHOLD = 20; // gửi mỗi 20 bước (giảm để test nhanh)
    private int targetSteps = 10000;

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
        findViewById(R.id.btnGoal).setOnClickListener(v -> {
            startActivity(new Intent(this, GoalActivity.class));
        });
        findViewById(R.id.btnChat).setOnClickListener(v -> {
            startActivity(new Intent(this, ChatActivity.class));
        });

        userId = Prefs.getUserId(this);
        if (userId == null) userId = "anonymous";

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        stepSensor = sensorManager.getDefaultSensor(Sensor.TYPE_STEP_COUNTER);
        accelSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

        if (stepSensor == null) {
            useAccelerometer = true;
            Toast.makeText(this, "Không có Step Counter trên thiết bị — dùng Accelerometer", Toast.LENGTH_LONG).show();
        }

        loadData();
        checkNewDay();

        // permission (activity recognition)
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACTIVITY_RECOGNITION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACTIVITY_RECOGNITION}, 100);
        }

        // setup chart initially
        setupChart();
        fetchLast7DaysAndDraw();
        fetchGoalAndApply();
        fetchAnalyticsAndShow();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (useAccelerometer && accelSensor != null) {
            sensorManager.registerListener(this, accelSensor, SensorManager.SENSOR_DELAY_NORMAL);
        } else if (!useAccelerometer && stepSensor != null) {
            sensorManager.registerListener(this, stepSensor, SensorManager.SENSOR_DELAY_UI);
        }
        // Refresh goal whenever returning to this screen
        fetchGoalAndApply();
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
        saveData();
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (useAccelerometer && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];
            double magnitude = Math.sqrt(x * x + y * y + z * z);
            double delta = magnitude - previousMagnitude;
            previousMagnitude = magnitude;

            // ngưỡng có thể điều chỉnh
            if (delta > 4.0) {
                stepCount++;
                onStepUpdated();
            }
        } else if (!useAccelerometer && event.sensor.getType() == Sensor.TYPE_STEP_COUNTER) {
            // step sensor trả event.values[0] là total steps since boot
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
            onStepUpdated();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {}

    private void onStepUpdated() {
        checkNewDay();

        double distanceKm = stepCount * 0.0008; // 0.8m/step in km
        double calories = stepCount * 0.04;

        txtSteps.setText(String.valueOf(stepCount));
        txtDistance.setText(String.format(Locale.getDefault(), "%.2f km", distanceKm));
        txtRingCenter.setText(String.format(Locale.getDefault(), "%d CAL", (int) Math.round(calories)));

        // animate ring progress (progress in 0..100)
        int percent = (int) Math.min(100, (stepCount * 100.0 / Math.max(1, targetSteps)));
        animateProgress(progressCircle, percent);

        // Lưu ngay để tránh mất dữ liệu nếu app bị đóng đột ngột
        saveData();

        // upload periodically
        if (stepCount > 0 && stepCount % UPLOAD_THRESHOLD == 0) {
            uploadToServer(stepCount, distanceKm, calories);
        }
    }

    private void animateProgress(ProgressBar pb, int targetProgress) {
        int start = pb.getProgress();
        ValueAnimator anim = ValueAnimator.ofInt(start, targetProgress);
        anim.setDuration(700);
        anim.setInterpolator(new DecelerateInterpolator());
        anim.addUpdateListener(animation -> {
            int val = (int) animation.getAnimatedValue();
            pb.setProgress(val);
        });
        anim.start();
    }

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
        previousTotalSteps = prefs.getFloat("previousTotalSteps", 0f);
        lastDate = prefs.getString("lastDate", "");
        stepCount = prefs.getInt("stepCount", 0);
        resetBaselinePending = prefs.getBoolean("resetBaselinePending", false);
    }

    private void saveData() {
        SharedPreferences prefs = getSharedPreferences("myPrefs", MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putFloat("previousTotalSteps", previousTotalSteps);
        editor.putString("lastDate", lastDate);
        editor.putInt("stepCount", stepCount);
        editor.putBoolean("resetBaselinePending", resetBaselinePending);
        editor.apply();
    }

    private void uploadToServer(int steps, double distanceKm, double calories) {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        String date = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date());
        ActivityItem item = new ActivityItem(userId, date, steps, distanceKm * 1000, calories, Collections.singletonList(useAccelerometer ? "accelerometer" : "step_counter"));

        api.uploadActivity(item).enqueue(new Callback<ResponseBody>() {
            @Override
            public void onResponse(Call<ResponseBody> call, Response<ResponseBody> response) {
                if (response.isSuccessful()) {
                    Log.d("API", "✅ Activity uploaded!");
                    // refresh 7-day chart after upload
                    fetchLast7DaysAndDraw();
                } else {
                    Log.e("API", "❌ Upload error: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<ResponseBody> call, Throwable t) {
                Log.e("API", "⚠️ Upload failed: " + t.getMessage());
            }
        });
    }

    // ----------------- Chart functions -----------------
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
        Log.d("API", "Fetching activities for userId: " + userId);
        api.getActivities(userId).enqueue(new Callback<List<ActivityItem>>() {
            @Override
            public void onResponse(Call<List<ActivityItem>> call, Response<List<ActivityItem>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<ActivityItem> list = response.body();
                    Log.d("API", "Received " + list.size() + " activity records");
                    // Gom theo ngày TRƯỚC KHI lấy 7 ngày, để không bỏ sót ngày nào
                    drawBarChart(list);
                } else {
                    Log.e("API", "Failed fetch activities: " + response.code());
                }
            }

            @Override
            public void onFailure(Call<List<ActivityItem>> call, Throwable t) {
                Log.e("API", "Fetch activities failed: " + t.getMessage());
            }
        });
    }

    private void drawBarChart(List<ActivityItem> items) {
        // Gom theo ngày, lấy giá trị lớn nhất trong ngày (vì app upload số bước lũy kế trong ngày)
        java.util.Map<String, Integer> dateToStepsMax = new java.util.LinkedHashMap<>();
        for (ActivityItem it : items) {
            if (it.date == null) continue;
            Integer cur = dateToStepsMax.get(it.date);
            if (cur == null || it.steps > cur) {
                dateToStepsMax.put(it.date, it.steps);
            }
        }
        Log.d("API", "Grouped by date: " + dateToStepsMax.keySet());

        // Lấy 7 ngày gần nhất (sắp xếp theo thời gian giảm dần rồi lấy 7 ngày đầu)
        java.util.List<String> allDates = new java.util.ArrayList<>(dateToStepsMax.keySet());
        java.util.Collections.sort(allDates, (a, b) -> b.compareTo(a)); // sort desc (mới nhất trước)
        java.util.List<String> last7Dates = new java.util.ArrayList<>();
        for (int i = 0; i < Math.min(7, allDates.size()); i++) {
            last7Dates.add(allDates.get(i));
        }
        // Đảo ngược để hiển thị theo thứ tự thời gian tăng dần trên trục X (cũ nhất → mới nhất)
        java.util.Collections.reverse(last7Dates);

        List<BarEntry> entries = new ArrayList<>();
        final List<String> labels = new ArrayList<>();
        for (int i = 0; i < last7Dates.size(); i++) {
            String d = last7Dates.get(i);
            entries.add(new BarEntry(i, dateToStepsMax.get(d)));
            labels.add(d.length() >= 5 ? d.substring(5) : d); // MM-DD
        }

        BarDataSet set = new BarDataSet(entries, "Bước (mỗi ngày)");
        set.setColor(getResources().getColor(R.color.accent_red));
        set.setDrawValues(true); // hiện số bước trên cột
        set.setValueTextColor(getResources().getColor(R.color.text_secondary));
        set.setValueTextSize(10f);

        BarData data = new BarData(set);
        data.setBarWidth(0.6f);
        barChart.setData(data);

        XAxis xAxis = barChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(labels.size());
        xAxis.setGranularity(1f);

        barChart.invalidate();
    }

    private void fetchGoalAndApply() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.getGoals().enqueue(new Callback<List<Goal>>() {
            @Override
            public void onResponse(Call<List<Goal>> call, Response<List<Goal>> response) {
                if (response.isSuccessful() && response.body() != null) {
                    List<Goal> goals = response.body();
                    Goal latestForUser = null;
                    for (Goal g : goals) {
                        if (g.userId != null && g.userId.equals(userId)) {
                            latestForUser = g; // lấy bản ghi cuối cùng gặp (backend chưa sort/filter)
                        }
                    }
                    if (latestForUser != null) {
                        if (latestForUser.stepGoal != null && latestForUser.stepGoal > 0) {
                            targetSteps = latestForUser.stepGoal;
                            TextView subtitle = findViewById(R.id.txtRingSubtitle);
                            if (subtitle != null) {
                                subtitle.setText("Mục tiêu: " + targetSteps + " bước/ngày");
                            }
                        }
                        if (txtGoalCal != null) {
                            if (latestForUser.calGoal != null && latestForUser.calGoal > 0) {
                                txtGoalCal.setText("Mục tiêu calo: " + latestForUser.calGoal + " cal/ngày");
                            } else {
                                txtGoalCal.setText("");
                            }
                        }
                        if (txtGoalPeriod != null) {
                            String start = latestForUser.planStart != null ? latestForUser.planStart : "";
                            String end = latestForUser.planEnd != null ? latestForUser.planEnd : "";
                            if (!start.isEmpty() || !end.isEmpty()) {
                                txtGoalPeriod.setText("Thời gian: " + start + (end.isEmpty() ? "" : " → " + end));
                            } else {
                                txtGoalPeriod.setText("");
                            }
                        }
                        onStepUpdated();
                    }
                }
            }

            @Override
            public void onFailure(Call<List<Goal>> call, Throwable t) {
                Log.e("API", "Fetch goals failed: " + t.getMessage());
            }
        });
    }

    private void fetchAnalyticsAndShow() {
        ApiService api = ApiClient.getClient().create(ApiService.class);
        TextView trendLoading = findViewById(R.id.txtTrendContent);
        if (trendLoading != null) {
            trendLoading.setText("Đang phân tích dữ liệu...");
        }
        java.util.Map<String, Object> body = new java.util.HashMap<>();
        body.put("userId", userId);
        body.put("userProfile", new java.util.HashMap<>());
        api.analyze(body).enqueue(new Callback<com.example.app_the_duc.models.InsightResponse>() {
            @Override
            public void onResponse(Call<com.example.app_the_duc.models.InsightResponse> call, Response<com.example.app_the_duc.models.InsightResponse> response) {
                if (response.isSuccessful() && response.body() != null && response.body().insight != null) {
                    com.example.app_the_duc.models.Insight ins = response.body().insight;
                    TextView trend = findViewById(R.id.txtTrendContent);
                    if (trend != null) {
                        StringBuilder sb = new StringBuilder();
                        if (ins.overview != null) sb.append(ins.overview).append("\n\n");
                        if (ins.suggestions != null && !ins.suggestions.isEmpty()) {
                            for (String s : ins.suggestions) {
                                sb.append("• ").append(s).append("\n");
                            }
                        }
                        if (ins.target != null && !ins.target.isEmpty()) {
                            sb.append("\nKhuyến nghị: ").append(ins.target);
                        }
                        if (sb.length() == 0) {
                            trend.setText("Chưa có dữ liệu đủ để phân tích. Hãy đi bộ và đồng bộ dữ liệu nhé!");
                        } else {
                            trend.setText(sb.toString());
                        }
                    }
                } else {
                    TextView trend = findViewById(R.id.txtTrendContent);
                    if (trend != null) trend.setText("Chưa có dữ liệu hoạt động 7 ngày để phân tích.");
                }
            }

            @Override
            public void onFailure(Call<com.example.app_the_duc.models.InsightResponse> call, Throwable t) {
                Log.e("API", "Analyze failed: " + t.getMessage());
                TextView trend = findViewById(R.id.txtTrendContent);
                if (trend != null) trend.setText("Không thể phân tích lúc này. Kiểm tra kết nối mạng hoặc thử lại sau.");
            }
        });
    }
}

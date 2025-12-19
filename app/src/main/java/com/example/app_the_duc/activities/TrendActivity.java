package com.example.app_the_duc.activities;

import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.TrendResponse;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;
import com.github.mikephil.charting.charts.LineChart;
import com.github.mikephil.charting.components.XAxis;
import com.github.mikephil.charting.data.Entry;
import com.github.mikephil.charting.data.LineData;
import com.github.mikephil.charting.data.LineDataSet;
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class TrendActivity extends AppCompatActivity {

    private LineChart lineChart;
    private TextView txtTrendSummary, txtConsistency, txtVolatility, txtStreak, txtPercentChange;
    private String userId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_trend);

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        lineChart = findViewById(R.id.lineChart);
        txtTrendSummary = findViewById(R.id.txtTrendSummary);
        txtConsistency = findViewById(R.id.txtConsistency);
        txtVolatility = findViewById(R.id.txtVolatility);
        txtStreak = findViewById(R.id.txtStreak);
        txtPercentChange = findViewById(R.id.txtPercentChange);

        userId = Prefs.getUserId(this);
        if (userId == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        setupChart();
        loadTrendData();
    }

    private void setupChart() {
        lineChart.getDescription().setEnabled(false);
        lineChart.setDrawGridBackground(false);
        XAxis xAxis = lineChart.getXAxis();
        xAxis.setPosition(XAxis.XAxisPosition.BOTTOM);
        xAxis.setDrawGridLines(false);
        lineChart.getAxisRight().setEnabled(false);
        lineChart.getLegend().setEnabled(false);
    }

    private void loadTrendData() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", userId);
        body.put("days", 90);
        body.put("userProfile", new HashMap<>());

        api.getTrendAnalysis(body).enqueue(new Callback<TrendResponse>() {
            @Override
            public void onResponse(Call<TrendResponse> call, Response<TrendResponse> response) {
                if (response.isSuccessful() && response.body() != null) {
                    TrendResponse data = response.body();
                    displayTrendData(data);
                } else {
                    String err = "Không lấy được dữ liệu xu hướng";
                    try {
                        if (response.errorBody() != null) {
                            err += ": " + response.errorBody().string();
                        } else {
                            err += " (code " + response.code() + ")";
                        }
                    } catch (Exception ignored) {}
                    Toast.makeText(TrendActivity.this, err, Toast.LENGTH_SHORT).show();
                    Log.e("Trend", err);
                }
            }

            @Override
            public void onFailure(Call<TrendResponse> call, Throwable t) {
                Log.e("Trend", "Error: " + t.getMessage());
                Toast.makeText(TrendActivity.this, "Lỗi kết nối: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void displayTrendData(TrendResponse data) {
        // Hiển thị trend summary
        if (data.trendSummary != null) {
            txtTrendSummary.setText(data.trendSummary);
        }

        if (data.trend != null) {
            TrendResponse.Trend trend = data.trend;

            // Hiển thị các chỉ số
            txtConsistency.setText(String.format(Locale.getDefault(), "Độ ổn định: %.1f/100", trend.consistency));
            txtVolatility.setText(String.format(Locale.getDefault(), "Dao động: %d bước/ngày", trend.volatility));
            txtStreak.setText(String.format(Locale.getDefault(), "Chuỗi ngày ≥8000 bước: %d ngày", trend.streak));
            
            String changeText = trend.percentChange > 0 ? "+" : "";
            txtPercentChange.setText(String.format(Locale.getDefault(), "Thay đổi: %s%.1f%%", changeText, trend.percentChange));

            // Vẽ biểu đồ
            if (trend.movingAvg != null && !trend.movingAvg.isEmpty()) {
                drawLineChart(trend.movingAvg);
            }
        }

        // Hiển thị AI insights nếu có
        if (data.insight != null && data.insight.overview != null) {
            TextView txtAIInsight = findViewById(R.id.txtAIInsight);
            if (txtAIInsight != null) {
                StringBuilder sb = new StringBuilder();
                sb.append("💡 AI Phân tích:\n\n");
                sb.append(data.insight.overview).append("\n\n");
                if (data.insight.suggestions != null) {
                    for (String s : data.insight.suggestions) {
                        sb.append("• ").append(s).append("\n");
                    }
                }
                txtAIInsight.setText(sb.toString());
            }
        }
    }

    private void drawLineChart(List<TrendResponse.MovingAvg> movingAvg) {
        List<Entry> entries = new ArrayList<>();
        List<String> labels = new ArrayList<>();

        for (int i = 0; i < movingAvg.size(); i++) {
            TrendResponse.MovingAvg item = movingAvg.get(i);
            entries.add(new Entry(i, item.avg));
            // Chỉ hiển thị một số label để không quá dày
            if (i % (movingAvg.size() / 7) == 0 || i == movingAvg.size() - 1) {
                labels.add(item.date.substring(5)); // MM-DD
            } else {
                labels.add("");
            }
        }

        LineDataSet dataSet = new LineDataSet(entries, "Trung bình 7 ngày");
        dataSet.setColor(getResources().getColor(R.color.accent_red));
        dataSet.setLineWidth(2f);
        dataSet.setCircleColor(getResources().getColor(R.color.accent_red));
        dataSet.setCircleRadius(4f);
        dataSet.setDrawValues(false);

        LineData lineData = new LineData(dataSet);
        lineChart.setData(lineData);

        XAxis xAxis = lineChart.getXAxis();
        xAxis.setValueFormatter(new IndexAxisValueFormatter(labels));
        xAxis.setLabelCount(Math.min(7, labels.size()), true);

        lineChart.invalidate();
    }
}


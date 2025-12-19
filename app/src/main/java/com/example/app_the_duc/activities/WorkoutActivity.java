package com.example.app_the_duc.activities;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.Workout;
import com.example.app_the_duc.models.WorkoutItem;
import com.example.app_the_duc.models.WorkoutResponse;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;

import java.util.HashMap;
import java.util.Map;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class WorkoutActivity extends AppCompatActivity {

    LinearLayout layout;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_workout);

        findViewById(R.id.btnUploadVideo).setOnClickListener(v -> {
            Intent i = new Intent(this, VideoCalorieActivity.class);
            startActivity(i);
        });

        layout = findViewById(R.id.layoutContent);

        loadWorkoutFromAI();
    }

    private void loadWorkoutFromAI() {
        ApiService api = ApiClient.getClient().create(ApiService.class);

        Map<String, Object> body = new HashMap<>();
        body.put("userId", Prefs.getUserId(this));
        body.put("availableMinutes", 20);

        api.generateWorkout(body).enqueue(new Callback<WorkoutResponse>() {
            @Override
            public void onResponse(Call<WorkoutResponse> call, Response<WorkoutResponse> res) {
                if (res.isSuccessful() && res.body() != null) {
                    Workout workout = res.body().workout;
                    showWorkout(workout);
                } else {
                    Toast.makeText(WorkoutActivity.this, "Không tạo được bài tập", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<WorkoutResponse> call, Throwable t) {
                Toast.makeText(WorkoutActivity.this, "Lỗi mạng: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void showWorkout(Workout workout) {
        layout.addView(createTitle("🔥 Bài tập cá nhân hoá"));

        layout.addView(createTitle("👉 Khởi động (Warmup):"));
        for (WorkoutItem w : workout.warmup) {
            layout.addView(createItem(w));
        }

        layout.addView(createTitle("👉 Bài tập chính (Main):"));
        for (WorkoutItem w : workout.main) {
            layout.addView(createItem(w));
        }

        layout.addView(createTitle("👉 Hạ nhiệt (Cooldown):"));
        for (WorkoutItem w : workout.cooldown) {
            layout.addView(createItem(w));
        }

        layout.addView(createTitle("⏱ Tổng thời gian: " + workout.totalMinutes + " phút"));
        layout.addView(createTitle("💡 Ghi chú: " + workout.note));
    }

    private TextView createTitle(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(18);
        tv.setPadding(0, 20, 0, 10);
        return tv;
    }

    private TextView createItem(WorkoutItem w) {
        TextView tv = new TextView(this);
        tv.setText("• " + w.name + " (" + w.duration + " phút)\n  " + w.instruction);
        tv.setTextSize(16);
        tv.setPadding(0, 10, 0, 10);
        return tv;
    }
}

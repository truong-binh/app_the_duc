package com.example.app_the_duc.activities;

import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.Goal;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;

import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class GoalActivity extends AppCompatActivity {

    private EditText edtStep, edtCal, edtStart, edtEnd;
    private Button btnSave, btnBack;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_goal);

        // Ánh xạ view
        edtStep = findViewById(R.id.edtStepGoal);
        edtCal = findViewById(R.id.edtCalGoal);
        edtStart = findViewById(R.id.edtStart);
        edtEnd = findViewById(R.id.edtEnd);
        btnSave = findViewById(R.id.btnSaveGoal);
        btnBack = findViewById(R.id.btnBackActivity);

        // Chức năng nút quay lại
        btnBack.setOnClickListener(v -> finish());

        // Chức năng lưu mục tiêu
        btnSave.setOnClickListener(v -> saveGoal());
    }

    private void saveGoal() {
        String userId = Prefs.getUserId(this);
        if (userId == null) {
            Toast.makeText(this, "Bạn chưa đăng nhập", Toast.LENGTH_SHORT).show();
            return;
        }

        String stepStr = edtStep.getText().toString().trim();
        String calStr = edtCal.getText().toString().trim();
        String start = edtStart.getText().toString().trim();
        String end = edtEnd.getText().toString().trim();

        if (stepStr.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập bước/ngày", Toast.LENGTH_SHORT).show();
            return;
        }

        Goal goal = new Goal();
        goal.userId = userId;

        try {
            goal.stepGoal = Integer.parseInt(stepStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Bước/ngày không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            goal.calGoal = calStr.isEmpty() ? null : Integer.parseInt(calStr);
        } catch (NumberFormatException e) {
            Toast.makeText(this, "Calories không hợp lệ", Toast.LENGTH_SHORT).show();
            return;
        }

        goal.planStart = start;
        goal.planEnd = end;

        // Gọi API để lưu mục tiêu
        ApiService api = ApiClient.getClient().create(ApiService.class);
        api.createGoal(goal).enqueue(new Callback<Goal>() {
            @Override
            public void onResponse(Call<Goal> call, Response<Goal> response) {
                if (response.isSuccessful()) {
                    Toast.makeText(GoalActivity.this, "Đã lưu mục tiêu", Toast.LENGTH_SHORT).show();
                    finish();
                } else {
                    Toast.makeText(GoalActivity.this, "Lỗi lưu mục tiêu: " + response.code(), Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Call<Goal> call, Throwable t) {
                Toast.makeText(GoalActivity.this, "Kết nối thất bại: " + t.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }
}

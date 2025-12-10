package com.example.app_the_duc.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.example.app_the_duc.R;
import com.example.app_the_duc.models.CalorieResponse;
import com.example.app_the_duc.network.ApiClient;
import com.example.app_the_duc.network.ApiService;
import com.example.app_the_duc.util.Prefs;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.RequestBody;
import retrofit2.Call;
import retrofit2.Callback;
import retrofit2.Response;

public class VideoCalorieActivity extends AppCompatActivity {

    private static final int PICK_VIDEO = 100;
    private Uri selectedUri;

    private Button btnPick, btnUpload;
    private TextView txtResult;
    private ProgressBar loading;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_video_calorie);

        btnPick = findViewById(R.id.btnPickVideo);
        btnUpload = findViewById(R.id.btnUploadVideo);
        txtResult = findViewById(R.id.txtResult);
        loading = findViewById(R.id.progress);

        btnPick.setOnClickListener(v -> pickVideo());
        btnUpload.setOnClickListener(v -> uploadVideo());
    }

    private void pickVideo() {
        Intent intent = new Intent(Intent.ACTION_PICK);
        intent.setType("video/*");
        startActivityForResult(intent, PICK_VIDEO);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (requestCode == PICK_VIDEO && resultCode == RESULT_OK && data != null) {
            selectedUri = data.getData();
            txtResult.setText("🎥 Đã chọn video!");
        }
    }

    // ================================
    //  CHUYỂN URI → MultipartBody
    // ================================
    private MultipartBody.Part prepareVideoPart(Uri uri) {
        try {
            InputStream inputStream = getContentResolver().openInputStream(uri);

            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] temp = new byte[4096];
            int bytesRead;

            while ((bytesRead = inputStream.read(temp)) != -1) {
                buffer.write(temp, 0, bytesRead);
            }

            byte[] videoBytes = buffer.toByteArray();

            RequestBody requestFile = RequestBody.create(
                    MediaType.parse("video/mp4"),
                    videoBytes
            );

            return MultipartBody.Part.createFormData(
                    "video", "upload.mp4", requestFile
            );

        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    // ================================
    //  UPLOAD VIDEO + GỬI BROADCAST
    // ================================
    private void uploadVideo() {
        if (selectedUri == null) {
            Toast.makeText(this, "Bạn chưa chọn video", Toast.LENGTH_SHORT).show();
            return;
        }

        loading.setVisibility(ProgressBar.VISIBLE);
        txtResult.setText("⏳ Đang xử lý video...");

        MultipartBody.Part videoPart = prepareVideoPart(selectedUri);

        if (videoPart == null) {
            txtResult.setText("Lỗi đọc video!");
            loading.setVisibility(ProgressBar.GONE);
            return;
        }

        String userId = Prefs.getUserId(this);
        RequestBody userBody = RequestBody.create(
                MediaType.parse("text/plain"), userId
        );

        ApiService api = ApiClient.getClient().create(ApiService.class);

        api.uploadVideoCalorie(videoPart, userBody)
                .enqueue(new Callback<CalorieResponse>() {
                    @Override
                    public void onResponse(Call<CalorieResponse> call, Response<CalorieResponse> response) {
                        loading.setVisibility(ProgressBar.GONE);

                        if (response.isSuccessful() && response.body() != null) {

                            double calories = response.body().getCalories();
                            int stepsFromVideo = (int) (calories / 0.04); // 1 step = 0.04 kcal

                            txtResult.setText("🔥 Bạn đã đốt được " + (int) calories + " kcal!");

                            // Gửi broadcast cho Dashboard cập nhật ngay lập tức
                            Intent intent = new Intent("UPDATE_ACTIVITY_FROM_VIDEO");
                            intent.setPackage("com.example.app_the_duc"); // Đảm bảo broadcast tới đúng app
                            intent.putExtra("stepsFromVideo", stepsFromVideo);
                            intent.putExtra("caloriesFromVideo", calories);
                            sendBroadcast(intent);
                            
                            android.util.Log.d("VideoCalorie", "Broadcast sent: " + stepsFromVideo + " steps, " + calories + " calories");

                            // Gọi API cập nhật lên server
                            ApiService api2 = ApiClient.getClient().create(ApiService.class);
                            api2.updateActivity(userId, stepsFromVideo, calories)
                                    .enqueue(new Callback<Void>() {
                                        @Override
                                        public void onResponse(Call<Void> call, Response<Void> response) {
                                            txtResult.append("\n✔ Đã cộng " + stepsFromVideo + " bước vào hôm nay!");
                                        }

                                        @Override
                                        public void onFailure(Call<Void> call, Throwable t) {
                                            txtResult.append("\n⚠ Không cập nhật được lên server.");
                                        }
                                    });

                        } else {
                            txtResult.setText("❌ Lỗi từ server!");
                        }
                    }

                    @Override
                    public void onFailure(Call<CalorieResponse> call, Throwable t) {
                        loading.setVisibility(ProgressBar.GONE);
                        txtResult.setText("⚠ Lỗi kết nối: " + t.getMessage());
                    }
                });
    }
}

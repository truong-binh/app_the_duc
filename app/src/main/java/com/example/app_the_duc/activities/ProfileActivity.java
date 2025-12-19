package com.example.app_the_duc.activities;

import android.content.SharedPreferences;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.example.app_the_duc.R;
import com.example.app_the_duc.util.Prefs;
import com.google.gson.Gson;

import java.util.HashMap;
import java.util.Map;

public class ProfileActivity extends AppCompatActivity {

    private EditText edtName, edtEmail, edtAge, edtHeight, edtWeight;
    private TextView txtUserId;
    private Button btnSave, btnBack;
    private Gson gson = new Gson();
    private static final String PROFILE_PREFS = "profile_prefs";
    private static final String PROFILE_KEY = "user_profile";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_profile);

        edtName = findViewById(R.id.edtName);
        edtEmail = findViewById(R.id.edtEmail);
        edtAge = findViewById(R.id.edtAge);
        edtHeight = findViewById(R.id.edtHeight);
        edtWeight = findViewById(R.id.edtWeight);
        txtUserId = findViewById(R.id.txtUserId);
        btnSave = findViewById(R.id.btnSaveProfile);
        btnBack = findViewById(R.id.btnBackProfile);

        String userId = Prefs.getUserId(this);
        if (userId != null) {
            txtUserId.setText("User ID: " + userId);
        }

        loadProfile();

        btnBack.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> saveProfile());
    }

    private void loadProfile() {
        SharedPreferences prefs = getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE);
        String json = prefs.getString(PROFILE_KEY, null);
        
        if (json != null) {
            try {
                Map<String, Object> profile = gson.fromJson(json, Map.class);
                if (profile.get("name") != null) edtName.setText(profile.get("name").toString());
                if (profile.get("email") != null) edtEmail.setText(profile.get("email").toString());
                if (profile.get("age") != null) edtAge.setText(profile.get("age").toString());
                if (profile.get("height") != null) edtHeight.setText(profile.get("height").toString());
                if (profile.get("weight") != null) edtWeight.setText(profile.get("weight").toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    private void saveProfile() {
        String name = edtName.getText().toString().trim();
        String email = edtEmail.getText().toString().trim();
        String ageStr = edtAge.getText().toString().trim();
        String heightStr = edtHeight.getText().toString().trim();
        String weightStr = edtWeight.getText().toString().trim();

        if (name.isEmpty() || email.isEmpty()) {
            Toast.makeText(this, "Vui lòng nhập tên và email", Toast.LENGTH_SHORT).show();
            return;
        }

        Map<String, Object> profile = new HashMap<>();
        profile.put("name", name);
        profile.put("email", email);
        if (!ageStr.isEmpty()) {
            try {
                profile.put("age", Integer.parseInt(ageStr));
            } catch (Exception ignored) {}
        }
        if (!heightStr.isEmpty()) {
            try {
                profile.put("height", Integer.parseInt(heightStr));
            } catch (Exception ignored) {}
        }
        if (!weightStr.isEmpty()) {
            try {
                profile.put("weight", Integer.parseInt(weightStr));
            } catch (Exception ignored) {}
        }

        String json = gson.toJson(profile);
        getSharedPreferences(PROFILE_PREFS, MODE_PRIVATE)
                .edit()
                .putString(PROFILE_KEY, json)
                .apply();

        Toast.makeText(this, "Đã lưu thông tin cá nhân", Toast.LENGTH_SHORT).show();
        
        // TODO: Gửi lên server khi backend có API update profile
        // ApiService api = ApiClient.getClient().create(ApiService.class);
        // api.updateProfile(userId, profile).enqueue(...);
    }
}


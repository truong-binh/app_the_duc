package com.example.app_the_duc.network;

import com.example.app_the_duc.models.ActivityItem;
import com.example.app_the_duc.models.RegisterResponse;
import com.example.app_the_duc.models.Goal;
import com.example.app_the_duc.models.Insight;
import com.example.app_the_duc.models.ChatResponse;
import com.example.app_the_duc.models.InsightResponse;

import java.util.List;
import java.util.Map;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.*;

public interface ApiService {
    @POST("register")
    Call<RegisterResponse> registerUser(@Body Map<String, Object> body);

    @POST("users/login")
    Call<Map<String, Object>> loginUser(@Body Map<String, Object> body);

    @GET("/api/v1/activity/{userId}")
    Call<List<ActivityItem>> getActivities(@Path("userId") String userId);

    @POST("/api/v1/activity")
    Call<ResponseBody> uploadActivity(@Body ActivityItem activity);

    @GET("/api/v1/goals")
    Call<List<Goal>> getGoals();

    @POST("/api/v1/goals")
    Call<Goal> createGoal(@Body Goal goal);

    @POST("/api/v1/analytics")
    Call<InsightResponse> analyze(@Body Object body);

    @POST("/api/v1/chat")
    Call<ChatResponse> chat(@Body Object body);

}

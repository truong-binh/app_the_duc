package com.example.app_the_duc.network;

import com.example.app_the_duc.models.ActivityItem;
import com.example.app_the_duc.models.CalorieResponse;
import com.example.app_the_duc.models.RegisterResponse;
import com.example.app_the_duc.models.Goal;
import com.example.app_the_duc.models.Insight;
import com.example.app_the_duc.models.ChatResponse;
import com.example.app_the_duc.models.InsightResponse;
import com.example.app_the_duc.models.WorkoutResponse;

import java.util.List;
import java.util.Map;

import okhttp3.MultipartBody;
import okhttp3.RequestBody;
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

    @POST("workout/generate")
    Call<WorkoutResponse> generateWorkout(@Body Map<String, Object> body);

    @Multipart
    @POST("video/calc-calories")
    Call<Double> uploadWorkoutVideo(@Part MultipartBody.Part video);

    @Multipart
    @POST("video/calc-calories")
    Call<CalorieResponse> uploadVideoCalorie(
            @Part MultipartBody.Part video,
            @Part("userId") RequestBody userId
    );

    @FormUrlEncoded
    @POST("activity/update")
    Call<Void> updateActivity(
            @Field("userId") String userId,
            @Field("steps") int steps,
            @Field("calories") double calories
    );

    @POST("/api/v1/trend/long")
    Call<com.example.app_the_duc.models.TrendResponse> getTrendAnalysis(@Body Map<String, Object> body);
}

package com.example.app_the_duc.models;

import java.util.List;

public class ActivityItem {
    public String _id; // optional if returned by server
    public String userId;
    public String date;
    public int steps;
    public double distance_m;
    public double calories;
    public List<String> source;
    
    // Các trường mới cho sức khỏe và loại hoạt động
    public String activityType; // "walking", "running", "swimming", "football", etc.
    public HeartRate heartRate;
    public Integer duration; // Thời gian hoạt động (phút)
    public String intensity; // "low", "moderate", "high"
    public String notes;

    // Nested class cho nhịp tim
    public static class HeartRate {
        public Integer avg;  // Nhịp tim trung bình
        public Integer max;   // Nhịp tim tối đa
        public Integer min;   // Nhịp tim tối thiểu
    }

    // default constructor required for Gson
    public ActivityItem() {}

    public ActivityItem(String userId, String date, int steps, double distance_m, double calories, List<String> source) {
        this.userId = userId;
        this.date = date;
        this.steps = steps;
        this.distance_m = distance_m;
        this.calories = calories;
        this.source = source;
        this.activityType = "walking";
    }
    
    public ActivityItem(String userId, String date, int steps, double distance_m, double calories, 
                       List<String> source, String activityType, HeartRate heartRate, Integer duration) {
        this.userId = userId;
        this.date = date;
        this.steps = steps;
        this.distance_m = distance_m;
        this.calories = calories;
        this.source = source;
        this.activityType = activityType != null ? activityType : "walking";
        this.heartRate = heartRate;
        this.duration = duration;
    }
}

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

    // default constructor required for Gson
    public ActivityItem() {}

    public ActivityItem(String userId, String date, int steps, double distance_m, double calories, List<String> source) {
        this.userId = userId;
        this.date = date;
        this.steps = steps;
        this.distance_m = distance_m;
        this.calories = calories;
        this.source = source;
    }
}

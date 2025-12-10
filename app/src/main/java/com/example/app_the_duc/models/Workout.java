package com.example.app_the_duc.models;

import java.util.List;

public class Workout {
    public List<WorkoutItem> warmup;
    public List<WorkoutItem> main;
    public List<WorkoutItem> cooldown;
    public int totalMinutes;
    public String note;
}

package com.example.app_the_duc.models;

import java.util.List;

public class TrendResponse {
    public String message;
    public Trend trend;
    public String trendSummary;
    public Insight insight;
    
    public static class Trend {
        public double slope;
        public String trendLabel; // "increasing", "decreasing", "stable"
        public List<MovingAvg> movingAvg;
        public double percentChange;
        public double consistency;
        public int volatility;
        public int streak;
        public String startDate;
        public String endDate;
    }
    
    public static class MovingAvg {
        public String date;
        public int avg;
    }
}


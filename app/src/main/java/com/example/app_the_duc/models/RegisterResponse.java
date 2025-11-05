package com.example.app_the_duc.models;

public class RegisterResponse {
    private String message;
    private User user;

    public String getMessage() { return message; }
    public User getUser() { return user; }

    public static class User {
        private String name;
        private String email;
        private int age;
        private int height;
        private int weight;
        private String gender;
        private String _id;

        public String getName() { return name; }
        public String getEmail() { return email; }
        public int getAge() { return age; }
        public int getHeight() { return height; }
        public int getWeight() { return weight; }
        public String getGender() { return gender; }
        public String get_id() { return _id; }
    }
}

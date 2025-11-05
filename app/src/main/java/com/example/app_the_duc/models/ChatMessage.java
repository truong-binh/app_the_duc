package com.example.app_the_duc.models;

public class ChatMessage {
    public String sender; // "user" hoặc "ai"
    public String message;
    public long timestamp;
    public ChatMessage(String sender, String message, long timestamp) {
        this.sender = sender;
        this.message = message;
        this.timestamp = timestamp;
    }
}

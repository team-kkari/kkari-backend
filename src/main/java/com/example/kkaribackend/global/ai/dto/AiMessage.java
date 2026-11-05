package com.example.kkaribackend.global.ai.dto;

// role: user | assistant
public record AiMessage(String role, String content) {
}

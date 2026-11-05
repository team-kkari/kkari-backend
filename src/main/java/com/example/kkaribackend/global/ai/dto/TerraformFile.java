package com.example.kkaribackend.global.ai.dto;

import org.springframework.http.MediaType;

public record TerraformFile(String filename, MediaType contentType, byte[] content) {
}

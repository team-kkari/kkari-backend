package com.example.kkaribackend.global.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsClient;

@Configuration
public class AwsConfig {

    // 자격증명은 SDK 기본 방식(환경변수, ~/.aws, 인스턴스 Role)을 따른다
    @Bean
    public StsClient stsClient(@Value("${aws.region}") String region) {
        return StsClient.builder()
            .region(Region.of(region))
            .build();
    }
}

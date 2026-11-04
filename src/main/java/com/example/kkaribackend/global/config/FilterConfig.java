package com.example.kkaribackend.global.config;

import com.example.kkaribackend.global.error.GlobalExceptionFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class FilterConfig {

    @Bean
    public FilterRegistrationBean<GlobalExceptionFilter> globalExceptionFilter(
        ObjectMapper objectMapper) {

        FilterRegistrationBean<GlobalExceptionFilter> bean =
            new FilterRegistrationBean<>(new GlobalExceptionFilter(objectMapper));
        bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.addUrlPatterns("/*");
        return bean;
    }
}
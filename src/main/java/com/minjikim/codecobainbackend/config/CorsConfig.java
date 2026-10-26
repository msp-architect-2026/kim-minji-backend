package com.minjikim.codecobainbackend.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(
                        "http://localhost:5173",
                        "http://app.wafer.local:32088",
                        "http://app.wafer.local",
                        // Ingress HTTPS 리다이렉트 이후 접속 주소 (LoadBalancer IP / NodePort)
                        "https://app.wafer.local",
                        "https://app.wafer.local:32443"
                )
                .allowedMethods("*")
                .allowedHeaders("*")
                .allowCredentials(true);
    }
}
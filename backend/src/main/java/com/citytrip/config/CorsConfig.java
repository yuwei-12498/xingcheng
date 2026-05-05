package com.citytrip.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Value("${app.cors.allowed-origin-patterns:http://localhost:*,https://localhost:*,http://127.0.0.1:*,https://127.0.0.1:*,http://10.0.2.2:*,http://10.*:*,https://10.*:*,http://192.168.*:*,http://172.16.*:*,http://172.17.*:*,http://172.18.*:*,http://172.19.*:*,http://172.20.*:*,http://172.21.*:*,http://172.22.*:*,http://172.23.*:*,http://172.24.*:*,http://172.25.*:*,http://172.26.*:*,http://172.27.*:*,http://172.28.*:*,http://172.29.*:*,http://172.30.*:*,http://172.31.*:*}")
    private String allowedOriginPatterns;

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(parseAllowedOriginPatterns())
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .exposedHeaders("Authorization")
                .allowCredentials(true)
                .maxAge(3600);
    }

    private String[] parseAllowedOriginPatterns() {
        return java.util.Arrays.stream(allowedOriginPatterns.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toArray(String[]::new);
    }
}

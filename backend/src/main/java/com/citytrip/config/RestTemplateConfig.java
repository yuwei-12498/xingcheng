package com.citytrip.config;

import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate llmRestTemplate(RestTemplateBuilder builder, LlmProperties llmProperties) {
        return builder
                .setConnectTimeout(Duration.ofSeconds(llmProperties.resolveConnectTimeoutSeconds()))
                .setReadTimeout(Duration.ofSeconds(llmProperties.resolveReadTimeoutSeconds()))
                .build();
    }

    @Bean(name = "geoRestTemplate")
    public RestTemplate geoRestTemplate(RestTemplateBuilder builder, GeoSearchProperties geoSearchProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(Math.max(geoSearchProperties.getConnectTimeoutMs(), 100)))
                .setReadTimeout(Duration.ofMillis(Math.max(geoSearchProperties.getReadTimeoutMs(), 100)))
                .build();
    }

    @Bean(name = "amapRestTemplate")
    public RestTemplate amapRestTemplate(RestTemplateBuilder builder, AmapProperties amapProperties) {
        return builder
                .setConnectTimeout(Duration.ofMillis(Math.max(amapProperties.getConnectTimeoutMs(), 100)))
                .setReadTimeout(Duration.ofMillis(Math.max(amapProperties.getReadTimeoutMs(), 100)))
                .build();
    }
}

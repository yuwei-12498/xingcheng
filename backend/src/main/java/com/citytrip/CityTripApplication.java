package com.citytrip;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.config.AmapProperties;
import com.citytrip.config.JwtProperties;
import com.citytrip.config.LlmProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

@SpringBootApplication
@MapperScan("com.citytrip.mapper")
@EnableConfigurationProperties({LlmProperties.class, JwtProperties.class, AlgorithmWeightsProperties.class, AmapProperties.class})
public class CityTripApplication {
    public static void main(String[] args) {
        SpringApplication.run(CityTripApplication.class, args);
    }
}

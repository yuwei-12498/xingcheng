package com.citytrip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

@Data
@ConfigurationProperties(prefix = "app.amap")
public class AmapProperties {

    private boolean enabled = false;
    private String baseUrl = "https://restapi.amap.com";
    private String apiKey = "";
    private String securityKey = "";
    private int connectTimeoutMs = 1500;
    private int readTimeoutMs = 5000;
    private String walkingPath = "/v3/direction/walking";
    private String bicyclingPath = "/v4/direction/bicycling";
    private String drivingPath = "/v3/direction/driving";
    private String transitPath = "/v3/direction/transit/integrated";
    private List<String> travelModes = new ArrayList<>(List.of("walking", "bicycling", "transit", "driving"));
    private double walkingMaxDistanceKm = 1.0D;
    private double transitMaxDistanceKm = 12.0D;
    private String defaultCityName = "成都";
    private boolean signRequests = false;
}

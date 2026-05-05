package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class RouteFeatureVectorVO {
    private String signature;
    private Integer stopCount;
    private BigDecimal totalCostEstimated;
    private Integer totalDurationMinutes;
    private Integer totalTravelTimeMinutes;
    private Integer totalWaitTimeMinutes;
    private BigDecimal totalWalkingDistanceEstimatedKm;
    private Integer themeMatchCount;
    private Integer companionMatchCount;
    private Integer nightFriendlyCount;
    private Integer indoorFriendlyCount;
    private Integer businessRiskScore;
    private Integer uniqueDistrictCount;
    private Double routeUtility;
    private Map<String, Object> scoreBreakdown;
}

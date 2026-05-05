package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ItineraryOptionVO {
    private String optionKey;
    private String title;
    private String subtitle;
    private String signature;
    private Integer totalDuration;
    private BigDecimal totalCost;
    private Integer stopCount;
    private Integer totalTravelTime;
    private Integer businessRiskScore;
    private Integer themeMatchCount;
    private Double routeUtility;
    private Double criticScore;
    private RouteFeatureVectorVO featureVector;
    private String summary;
    private String recommendReason;
    private String recommendationSource;
    private Boolean aiDecorated;
    private String notRecommendReason;
    private List<String> highlights;
    private List<String> tradeoffs;
    private List<String> alerts;
    private List<ItineraryNodeVO> nodes;
}

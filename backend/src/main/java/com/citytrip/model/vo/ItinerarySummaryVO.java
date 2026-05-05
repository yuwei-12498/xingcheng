package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
public class ItinerarySummaryVO {
    private Long id;
    private String title;
    private String cityName;
    private String routeSummary;
    private String coverImageUrl;
    private String tripDate;
    private String startTime;
    private String endTime;
    private Integer nodeCount;
    private Integer totalDuration;
    private BigDecimal totalCost;
    private String firstPoiName;
    private String lastPoiName;
    private String budgetLevel;
    private String companionType;
    private Boolean rainy;
    private Boolean night;
    private Boolean favorited;
    private LocalDateTime favoriteTime;
    private Boolean isPublic;
    private LocalDateTime updatedAt;
    private List<String> themes;
}

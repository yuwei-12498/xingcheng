package com.citytrip.service.geo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class GeoPoiCandidate {
    private String name;
    private String address;
    private String category;
    private String district;
    private String cityName;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private String source;
    private Double score;
    private String externalId;
    /**
     * Provider-returned business-hours text, for example "10:00-22:00".
     */
    private String openingHours;
    /**
     * Provider-returned explicit opening time when available.
     */
    private String openTime;
    /**
     * Provider-returned explicit closing time when available.
     */
    private String closeTime;
    /**
     * Provider-returned per-capita cost estimate.
     */
    private BigDecimal avgCost;
    /**
     * Provider-returned recommended stay duration in minutes.
     */
    private Integer stayDurationMinutes;
    /**
     * 与查询中心点的距离（米），仅在 nearby 场景下可能存在
     */
    private Double distanceMeters;

    public GeoPoint toPoint() {
        return new GeoPoint(latitude, longitude);
    }
}




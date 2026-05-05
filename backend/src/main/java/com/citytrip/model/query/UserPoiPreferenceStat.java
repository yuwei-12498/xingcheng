package com.citytrip.model.query;

import lombok.Data;

@Data
public class UserPoiPreferenceStat {
    private Long userId;
    private Long poiId;
    private Double preferenceScore;
}

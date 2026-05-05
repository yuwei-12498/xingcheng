package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class DepartureLegEstimateVO {
    /**
     * 建议交通方式，例如：步行、地铁+步行、打车
     */
    private String transportMode;

    /**
     * 预计通行时长（分钟）
     */
    private Integer estimatedMinutes;

    /**
     * 预计通行里程（公里）
     */
    private BigDecimal estimatedDistanceKm;
}

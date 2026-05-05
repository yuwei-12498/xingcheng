package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class RoutePathPointVO {
    private BigDecimal latitude;
    private BigDecimal longitude;
}

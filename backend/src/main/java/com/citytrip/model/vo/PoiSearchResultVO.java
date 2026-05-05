package com.citytrip.model.vo;

import java.math.BigDecimal;

public record PoiSearchResultVO(
        String name,
        String address,
        String category,
        BigDecimal latitude,
        BigDecimal longitude,
        String cityName,
        String cityCode,
        String source
) {
}

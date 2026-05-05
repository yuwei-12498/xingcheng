package com.citytrip.service.geo;

import java.math.BigDecimal;

public record GeoPoint(BigDecimal latitude, BigDecimal longitude) {

    public boolean valid() {
        if (latitude == null || longitude == null) {
            return false;
        }
        double lat = latitude.doubleValue();
        double lng = longitude.doubleValue();
        return Math.abs(lat) <= 90D && Math.abs(lng) <= 180D;
    }
}


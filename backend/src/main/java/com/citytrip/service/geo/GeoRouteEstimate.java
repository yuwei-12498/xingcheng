package com.citytrip.service.geo;

import java.math.BigDecimal;
import java.util.List;

public record GeoRouteEstimate(Integer durationMinutes,
                               BigDecimal distanceKm,
                               String transportMode,
                               List<GeoPoint> pathPoints,
                               List<GeoRouteStep> steps) {

    public GeoRouteEstimate(Integer durationMinutes,
                            BigDecimal distanceKm,
                            String transportMode) {
        this(durationMinutes, distanceKm, transportMode, List.of(), List.of());
    }

    public GeoRouteEstimate(Integer durationMinutes,
                            BigDecimal distanceKm,
                            String transportMode,
                            List<GeoPoint> pathPoints) {
        this(durationMinutes, distanceKm, transportMode, pathPoints, List.of());
    }

    public GeoRouteEstimate {
        pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}

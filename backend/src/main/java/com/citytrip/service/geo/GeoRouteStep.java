package com.citytrip.service.geo;

import java.util.List;

public record GeoRouteStep(String instruction,
                           String type,
                           Integer distanceMeters,
                           Integer durationMinutes,
                           String lineName,
                           String fromStation,
                           String toStation,
                           String entranceName,
                           String exitName,
                           Integer stopCount,
                           List<GeoPoint> pathPoints) {

    public GeoRouteStep {
        pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
    }
}

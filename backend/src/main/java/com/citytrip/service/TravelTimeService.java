package com.citytrip.service;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;

import java.math.BigDecimal;
import java.util.List;

/**
 * ????????
 */
public interface TravelTimeService {

    /**
     * ???????? from ? to ????????????
     */
    int estimateTravelTimeMinutes(Poi from, Poi to);

    default TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
        return new TravelLegEstimate(estimateTravelTimeMinutes(from, to), null, null);
    }

    record TravelLegEstimate(int estimatedMinutes,
                             BigDecimal estimatedDistanceKm,
                             String transportMode,
                             List<GeoPoint> pathPoints,
                             GeoRouteEstimate detailedRoute) {

        public TravelLegEstimate(int estimatedMinutes,
                                 BigDecimal estimatedDistanceKm,
                                 String transportMode) {
            this(estimatedMinutes, estimatedDistanceKm, transportMode, List.of(), null);
        }

        public TravelLegEstimate(int estimatedMinutes,
                                 BigDecimal estimatedDistanceKm,
                                 String transportMode,
                                 List<GeoPoint> pathPoints) {
            this(estimatedMinutes, estimatedDistanceKm, transportMode, pathPoints, null);
        }

        public TravelLegEstimate {
            pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
        }
    }
}

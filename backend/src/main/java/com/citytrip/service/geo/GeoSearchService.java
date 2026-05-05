package com.citytrip.service.geo;

import java.util.List;
import java.util.Optional;

public interface GeoSearchService {

    Optional<GeoPoint> geocode(String keyword, String cityName);

    List<GeoPoiCandidate> searchByKeyword(String keyword, String cityName, int limit);

    List<GeoPoiCandidate> searchNearby(GeoPoint center, String cityName, String category, int radiusMeters, int limit);

    default Optional<GeoRouteEstimate> estimateTravel(GeoPoint from,
                                                      GeoPoint to,
                                                      String cityName,
                                                      String preferredMode) {
        return Optional.empty();
    }
}

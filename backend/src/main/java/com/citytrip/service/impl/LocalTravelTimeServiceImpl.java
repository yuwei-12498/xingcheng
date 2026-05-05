package com.citytrip.service.impl;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.geo.GeoPoint;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
@Primary
public class LocalTravelTimeServiceImpl implements TravelTimeService {

    private static final double EARTH_RADIUS_KM = 6371.0;
    private static final double WALKING_SPEED_KM_PER_MIN = 4.8 / 60.0;
    private static final double TRANSIT_SPEED_KM_PER_MIN = 22.0 / 60.0;
    private static final double TAXI_SPEED_KM_PER_MIN = 28.0 / 60.0;

    @Override
    public int estimateTravelTimeMinutes(Poi from, Poi to) {
        return estimateTravelLeg(from, to).estimatedMinutes();
    }

    @Override
    public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
        if (from == null || to == null || samePoi(from, to)) {
            return new TravelLegEstimate(0, BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP), "步行");
        }
        if (from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return new TravelLegEstimate(30, null, "公交+步行");
        }

        double straightDistance = haversineDistanceKm(from, to);
        double roadDistance = straightDistance * roadFactor(straightDistance, from, to);
        boolean crossDistrict = from.getDistrict() != null
                && to.getDistrict() != null
                && !from.getDistrict().equals(to.getDistrict());

        int minutes;
        String mode;
        if (roadDistance <= 0.8) {
            minutes = Math.max(5, (int) Math.ceil(roadDistance / WALKING_SPEED_KM_PER_MIN) + 3);
            mode = "步行";
        } else if (roadDistance <= 2.5) {
            int transferBuffer = roadDistance > 1.8 ? 7 : 5;
            int districtBuffer = crossDistrict ? 2 : 0;
            minutes = Math.max(10, (int) Math.ceil(roadDistance / TRANSIT_SPEED_KM_PER_MIN) + transferBuffer + districtBuffer);
            mode = roadDistance <= 1.5 ? "骑行" : "公交+步行";
        } else if (roadDistance <= 8.0) {
            int districtBuffer = crossDistrict ? 4 : 2;
            minutes = Math.max(16, (int) Math.ceil(roadDistance / TRANSIT_SPEED_KM_PER_MIN) + 9 + districtBuffer);
            mode = "公交+步行";
        } else {
            int longDistanceBuffer = roadDistance > 12 ? (int) Math.ceil((roadDistance - 12) * 0.8) : 0;
            int districtBuffer = crossDistrict ? 6 : 3;
            minutes = Math.max(24, (int) Math.ceil(roadDistance / TAXI_SPEED_KM_PER_MIN) + 12 + districtBuffer + longDistanceBuffer);
            mode = "打车";
        }

        BigDecimal distanceKm = BigDecimal.valueOf(Math.max(roadDistance, 0D)).setScale(1, RoundingMode.HALF_UP);
        return new TravelLegEstimate(minutes, distanceKm, mode, buildRenderablePathPoints(from, to, straightDistance));
    }

    private boolean samePoi(Poi from, Poi to) {
        return from.getId() != null && from.getId().equals(to.getId());
    }

    private double haversineDistanceKm(Poi from, Poi to) {
        double lat1 = Math.toRadians(from.getLatitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue());
        double deltaLat = lat1 - lat2;
        double deltaLon = Math.toRadians(from.getLongitude().doubleValue() - to.getLongitude().doubleValue());
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * EARTH_RADIUS_KM * Math.asin(Math.sqrt(a));
    }

    private double roadFactor(double straightDistance, Poi from, Poi to) {
        double factor;
        if (straightDistance <= 1.0) {
            factor = 1.18;
        } else if (straightDistance <= 4.0) {
            factor = 1.28;
        } else if (straightDistance <= 10.0) {
            factor = 1.38;
        } else {
            factor = 1.48;
        }

        if (from.getDistrict() != null && to.getDistrict() != null && !from.getDistrict().equals(to.getDistrict())) {
            factor += 0.08;
        }

        return factor;
    }

    private List<GeoPoint> buildRenderablePathPoints(Poi from, Poi to, double straightDistanceKm) {
        if (from == null || to == null
                || from.getLatitude() == null || from.getLongitude() == null
                || to.getLatitude() == null || to.getLongitude() == null) {
            return List.of();
        }

        double fromLat = from.getLatitude().doubleValue();
        double fromLng = from.getLongitude().doubleValue();
        double toLat = to.getLatitude().doubleValue();
        double toLng = to.getLongitude().doubleValue();
        if (Double.compare(fromLat, toLat) == 0 && Double.compare(fromLng, toLng) == 0) {
            return List.of(new GeoPoint(from.getLatitude(), from.getLongitude()));
        }

        double deltaLat = toLat - fromLat;
        double deltaLng = toLng - fromLng;
        double vectorLength = Math.sqrt(deltaLat * deltaLat + deltaLng * deltaLng);
        if (vectorLength <= 0D) {
            return List.of(
                    new GeoPoint(from.getLatitude(), from.getLongitude()),
                    new GeoPoint(to.getLatitude(), to.getLongitude())
            );
        }

        double perpendicularLat = -deltaLng / vectorLength;
        double perpendicularLng = deltaLat / vectorLength;
        double curveStrength = resolveCurveStrength(straightDistanceKm);
        double signedCurve = ((from.getDistrict() == null ? 0 : from.getDistrict().hashCode())
                + (to.getDistrict() == null ? 0 : to.getDistrict().hashCode())) % 2 == 0
                ? curveStrength
                : -curveStrength;

        List<GeoPoint> points = new ArrayList<>();
        points.add(new GeoPoint(from.getLatitude(), from.getLongitude()));
        points.add(interpolatePoint(fromLat, fromLng, deltaLat, deltaLng, perpendicularLat, perpendicularLng, 0.32D, signedCurve));
        if (straightDistanceKm >= 2.5D) {
            points.add(interpolatePoint(fromLat, fromLng, deltaLat, deltaLng, perpendicularLat, perpendicularLng, 0.58D, -signedCurve * 0.42D));
        }
        points.add(interpolatePoint(fromLat, fromLng, deltaLat, deltaLng, perpendicularLat, perpendicularLng, 0.78D, signedCurve * 0.28D));
        points.add(new GeoPoint(to.getLatitude(), to.getLongitude()));
        return dedupeConsecutive(points);
    }

    private GeoPoint interpolatePoint(double fromLat,
                                      double fromLng,
                                      double deltaLat,
                                      double deltaLng,
                                      double perpendicularLat,
                                      double perpendicularLng,
                                      double ratio,
                                      double curveOffset) {
        double lat = fromLat + deltaLat * ratio + perpendicularLat * curveOffset;
        double lng = fromLng + deltaLng * ratio + perpendicularLng * curveOffset;
        return new GeoPoint(
                BigDecimal.valueOf(lat).setScale(6, RoundingMode.HALF_UP),
                BigDecimal.valueOf(lng).setScale(6, RoundingMode.HALF_UP)
        );
    }

    private double resolveCurveStrength(double straightDistanceKm) {
        double baseStrength = Math.max(0.0012D, Math.min(0.018D, straightDistanceKm * 0.0038D));
        return straightDistanceKm >= 8D ? baseStrength * 1.15D : baseStrength;
    }

    private List<GeoPoint> dedupeConsecutive(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        List<GeoPoint> deduped = new ArrayList<>();
        GeoPoint previous = null;
        for (GeoPoint point : points) {
            if (point == null || !point.valid()) {
                continue;
            }
            if (previous == null
                    || previous.latitude().compareTo(point.latitude()) != 0
                    || previous.longitude().compareTo(point.longitude()) != 0) {
                deduped.add(point);
                previous = point;
            }
        }
        return deduped;
    }
}

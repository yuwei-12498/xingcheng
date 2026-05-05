package com.citytrip.service.impl;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.Objects;

@Service
public class GeoEnhancedTravelTimeServiceImpl implements TravelTimeService {

    private final GeoSearchService geoSearchService;
    private final LocalTravelTimeServiceImpl localTravelTimeService;

    public GeoEnhancedTravelTimeServiceImpl(GeoSearchService geoSearchService,
                                            LocalTravelTimeServiceImpl localTravelTimeService) {
        this.geoSearchService = geoSearchService;
        this.localTravelTimeService = localTravelTimeService;
    }

    @Override
    public int estimateTravelTimeMinutes(Poi from, Poi to) {
        if (from == null || to == null) {
            return localTravelTimeService.estimateTravelTimeMinutes(from, to);
        }
        if (from.getId() != null && Objects.equals(from.getId(), to.getId())) {
            return 0;
        }

        try {
            Poi resolvedFrom = resolveCoordinateIfMissing(from);
            Poi resolvedTo = resolveCoordinateIfMissing(to);
            Integer precise = estimateByRouteApi(resolvedFrom, resolvedTo);
            if (precise != null && precise > 0) {
                return precise;
            }
            return localTravelTimeService.estimateTravelTimeMinutes(resolvedFrom, resolvedTo);
        } catch (Exception ex) {
            return localTravelTimeService.estimateTravelTimeMinutes(from, to);
        }
    }

    @Override
    public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
        if (from == null || to == null) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
        if (from.getId() != null && Objects.equals(from.getId(), to.getId())) {
            return new TravelLegEstimate(0, BigDecimal.ZERO, "步行");
        }

        try {
            Poi resolvedFrom = resolveCoordinateIfMissing(from);
            Poi resolvedTo = resolveCoordinateIfMissing(to);
            GeoRouteEstimate precise = estimateByRouteApiForAnyLeg(resolvedFrom, resolvedTo);
            if (precise != null && precise.durationMinutes() != null && precise.durationMinutes() > 0) {
                BigDecimal distanceKm = precise.distanceKm() == null
                        ? null
                        : precise.distanceKm();
                return new TravelLegEstimate(
                        precise.durationMinutes(),
                        distanceKm,
                        StringUtils.hasText(precise.transportMode()) ? precise.transportMode().trim() : null,
                        precise.pathPoints(),
                        precise
                );
            }
            return localTravelTimeService.estimateTravelLeg(resolvedFrom, resolvedTo);
        } catch (Exception ex) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
    }

    private Integer estimateByRouteApi(Poi from, Poi to) {
        if (!shouldUsePreciseDepartureRouting(from, to) || !hasCoordinate(from) || !hasCoordinate(to)) {
            return null;
        }
        GeoPoint fromPoint = new GeoPoint(from.getLatitude(), from.getLongitude());
        GeoPoint toPoint = new GeoPoint(to.getLatitude(), to.getLongitude());
        String cityName = StringUtils.hasText(from.getCityName()) ? from.getCityName() : to.getCityName();
        return geoSearchService.estimateTravel(fromPoint, toPoint, cityName, null)
                .map(estimate -> estimate.durationMinutes())
                .filter(minutes -> minutes != null && minutes > 0)
                .orElse(null);
    }

    private GeoRouteEstimate estimateByRouteApiForAnyLeg(Poi from, Poi to) {
        if (!hasCoordinate(from) || !hasCoordinate(to)) {
            return null;
        }
        GeoPoint fromPoint = new GeoPoint(from.getLatitude(), from.getLongitude());
        GeoPoint toPoint = new GeoPoint(to.getLatitude(), to.getLongitude());
        String cityName = StringUtils.hasText(from.getCityName()) ? from.getCityName() : to.getCityName();
        return geoSearchService.estimateTravel(fromPoint, toPoint, cityName, null).orElse(null);
    }

    private boolean shouldUsePreciseDepartureRouting(Poi from, Poi to) {
        return isDeparturePoi(from) || isDeparturePoi(to);
    }

    private boolean isDeparturePoi(Poi poi) {
        return poi != null && poi.getId() != null && poi.getId() < 0;
    }

    private Poi resolveCoordinateIfMissing(Poi poi) {
        if (poi == null || hasCoordinate(poi)) {
            return poi;
        }
        if (!StringUtils.hasText(poi.getName())) {
            return poi;
        }

        String keyword = buildGeocodeKeyword(poi);
        GeoPoint point = geoSearchService.geocode(keyword, poi.getCityName()).orElse(null);
        if (point == null || !point.valid()) {
            return poi;
        }

        Poi copied = copyPoi(poi);
        copied.setLatitude(point.latitude());
        copied.setLongitude(point.longitude());
        return copied;
    }

    private boolean hasCoordinate(Poi poi) {
        return poi != null
                && poi.getLatitude() != null
                && poi.getLongitude() != null
                && Math.abs(poi.getLatitude().doubleValue()) <= 90D
                && Math.abs(poi.getLongitude().doubleValue()) <= 180D;
    }

    private String buildGeocodeKeyword(Poi poi) {
        StringBuilder builder = new StringBuilder();
        if (StringUtils.hasText(poi.getCityName())) {
            builder.append(poi.getCityName().trim());
        }
        if (StringUtils.hasText(poi.getDistrict())) {
            if (builder.length() > 0) {
                builder.append(' ');
            }
            builder.append(poi.getDistrict().trim());
        }
        if (builder.length() > 0) {
            builder.append(' ');
        }
        builder.append(poi.getName().trim());
        return builder.toString();
    }

    private Poi copyPoi(Poi source) {
        Poi target = new Poi();
        target.setId(source.getId());
        target.setName(source.getName());
        target.setCategory(source.getCategory());
        target.setDistrict(source.getDistrict());
        target.setAddress(source.getAddress());
        target.setLatitude(source.getLatitude());
        target.setLongitude(source.getLongitude());
        target.setOpenTime(source.getOpenTime());
        target.setCloseTime(source.getCloseTime());
        target.setClosedWeekdays(source.getClosedWeekdays());
        target.setTemporarilyClosed(source.getTemporarilyClosed());
        target.setStatusNote(source.getStatusNote());
        target.setStatusSource(source.getStatusSource());
        target.setStatusUpdatedAt(source.getStatusUpdatedAt());
        target.setAvgCost(source.getAvgCost());
        target.setStayDuration(source.getStayDuration());
        target.setIndoor(source.getIndoor());
        target.setNightAvailable(source.getNightAvailable());
        target.setRainFriendly(source.getRainFriendly());
        target.setWalkingLevel(source.getWalkingLevel());
        target.setTags(source.getTags());
        target.setSuitableFor(source.getSuitableFor());
        target.setDescription(source.getDescription());
        target.setPriorityScore(source.getPriorityScore());
        target.setCrowdPenalty(source.getCrowdPenalty());
        target.setCityCode(source.getCityCode());
        target.setCityName(source.getCityName());
        target.setSourceType(source.getSourceType());
        target.setExternalId(source.getExternalId());
        target.setTempScore(source.getTempScore());
        target.setOperatingStatus(source.getOperatingStatus());
        target.setAvailableOnTripDate(source.getAvailableOnTripDate());
        target.setStatusStale(source.getStatusStale());
        target.setAvailabilityNote(source.getAvailabilityNote());
        return target;
    }
}

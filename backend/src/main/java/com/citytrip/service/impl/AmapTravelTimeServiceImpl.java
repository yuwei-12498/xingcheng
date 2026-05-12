package com.citytrip.service.impl;

import com.citytrip.config.AmapProperties;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.TravelModeRequest;
import com.citytrip.service.ai.model.TravelModeDecisionService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoRouteStep;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;

@Service
public class AmapTravelTimeServiceImpl implements TravelTimeService {

    private static final Logger log = LoggerFactory.getLogger(AmapTravelTimeServiceImpl.class);
    private static final BigDecimal METERS_PER_KM = BigDecimal.valueOf(1000L);

    private final AmapProperties amapProperties;
    private final RestTemplate amapRestTemplate;
    private final ObjectMapper objectMapper;
    private final LocalTravelTimeServiceImpl localTravelTimeService;
    private final TravelModeDecisionService travelModeDecisionService;

    public AmapTravelTimeServiceImpl(AmapProperties amapProperties,
                                     @Qualifier("amapRestTemplate") RestTemplate amapRestTemplate,
                                     ObjectMapper objectMapper,
                                     LocalTravelTimeServiceImpl localTravelTimeService) {
        this(amapProperties, amapRestTemplate, objectMapper, localTravelTimeService, new TravelModeDecisionService());
    }

    @Autowired
    public AmapTravelTimeServiceImpl(AmapProperties amapProperties,
                                     @Qualifier("amapRestTemplate") RestTemplate amapRestTemplate,
                                     ObjectMapper objectMapper,
                                     LocalTravelTimeServiceImpl localTravelTimeService,
                                     TravelModeDecisionService travelModeDecisionService) {
        this.amapProperties = amapProperties;
        this.amapRestTemplate = amapRestTemplate;
        this.objectMapper = objectMapper;
        this.localTravelTimeService = localTravelTimeService;
        this.travelModeDecisionService = travelModeDecisionService;
    }

    @Override
    public int estimateTravelTimeMinutes(Poi from, Poi to) {
        return estimateTravelLeg(from, to).estimatedMinutes();
    }

    @Override
    public TravelLegEstimate estimateTravelLeg(Poi from, Poi to, TravelModeRequest requestedMode) {
        TravelModeRequest effectiveMode = requestedMode == null ? TravelModeRequest.AUTO : requestedMode;
        if (effectiveMode.isAuto()) {
            return estimateTravelLeg(from, to);
        }
        if (from == null || to == null || samePoi(from, to)) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
        if (!isReady() || !hasCoordinate(from) || !hasCoordinate(to)) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }

        try {
            return queryRequestedMode(from, to, effectiveMode)
                    .filter(estimate -> estimate.estimatedMinutes() > 0)
                    .orElseGet(() -> localTravelTimeService.estimateTravelLeg(from, to));
        } catch (RestClientException ex) {
            log.warn("Amap explicit route request failed, fallback to local estimator. mode={}, from={}, to={}, reason={}",
                    effectiveMode.code(), safeName(from), safeName(to), ex.getMessage());
            return localTravelTimeService.estimateTravelLeg(from, to);
        } catch (Exception ex) {
            log.warn("Amap explicit route parsing failed, fallback to local estimator. mode={}, from={}, to={}, reason={}",
                    effectiveMode.code(), safeName(from), safeName(to), ex.getMessage());
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
    }

    @Override
    public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
        if (from == null || to == null || samePoi(from, to)) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
        if (!isReady() || !hasCoordinate(from) || !hasCoordinate(to)) {
            return localTravelTimeService.estimateTravelLeg(from, to);
        }

        try {
            List<AmapMode> modes = resolveAttemptModes(from, to);
            List<TravelLegEstimate> estimates = new ArrayList<>();
            for (AmapMode mode : modes) {
                queryRoute(mode, from, to).ifPresent(estimates::add);
            }
            List<TravelLegEstimate> candidates = estimates.stream()
                    .filter(estimate -> estimate.estimatedMinutes() > 0)
                    .toList();
            if (candidates.isEmpty()) {
                return localTravelTimeService.estimateTravelLeg(from, to);
            }
            return travelModeDecisionService.pickBest(candidates);
        } catch (RestClientException ex) {
            log.warn("Amap route request failed, fallback to local estimator. from={}, to={}, reason={}",
                    safeName(from), safeName(to), ex.getMessage());
            return localTravelTimeService.estimateTravelLeg(from, to);
        } catch (Exception ex) {
            log.warn("Amap route parsing failed, fallback to local estimator. from={}, to={}, reason={}",
                    safeName(from), safeName(to), ex.getMessage());
            return localTravelTimeService.estimateTravelLeg(from, to);
        }
    }

    private Optional<TravelLegEstimate> queryRequestedMode(Poi from, Poi to, TravelModeRequest requestedMode) {
        return switch (requestedMode) {
            case AUTO -> Optional.empty();
            case WALK -> queryRoute(AmapMode.WALKING, from, to);
            case BIKE -> queryRoute(AmapMode.BICYCLING, from, to);
            case TRANSIT -> queryRoute(AmapMode.TRANSIT, from, to);
            case TAXI -> queryRoute(AmapMode.DRIVING, from, to);
        };
    }

    private boolean isReady() {
        return amapProperties != null
                && amapProperties.isEnabled()
                && StringUtils.hasText(amapProperties.getBaseUrl())
                && StringUtils.hasText(amapProperties.getApiKey());
    }

    private List<AmapMode> resolveAttemptModes(Poi from, Poi to) {
        double distanceKm = straightDistanceKm(from, to);
        List<String> configured = amapProperties.getTravelModes() == null || amapProperties.getTravelModes().isEmpty()
                ? List.of("walking", "bicycling", "transit", "driving")
                : amapProperties.getTravelModes();
        List<AmapMode> ordered = new ArrayList<>();
        if (distanceKm <= amapProperties.getWalkingMaxDistanceKm()) {
            addModeIfConfigured(ordered, configured, AmapMode.WALKING);
            addModeIfConfigured(ordered, configured, AmapMode.BICYCLING);
            addModeIfConfigured(ordered, configured, AmapMode.TRANSIT);
            addModeIfConfigured(ordered, configured, AmapMode.DRIVING);
        } else if (distanceKm <= amapProperties.getTransitMaxDistanceKm()) {
            addModeIfConfigured(ordered, configured, AmapMode.BICYCLING);
            addModeIfConfigured(ordered, configured, AmapMode.TRANSIT);
            addModeIfConfigured(ordered, configured, AmapMode.DRIVING);
            addModeIfConfigured(ordered, configured, AmapMode.WALKING);
        } else {
            addModeIfConfigured(ordered, configured, AmapMode.DRIVING);
            addModeIfConfigured(ordered, configured, AmapMode.TRANSIT);
            addModeIfConfigured(ordered, configured, AmapMode.BICYCLING);
        }
        return ordered.isEmpty() ? List.of(AmapMode.DRIVING) : ordered;
    }

    private void addModeIfConfigured(List<AmapMode> ordered, List<String> configured, AmapMode mode) {
        boolean enabled = configured.stream()
                .filter(StringUtils::hasText)
                .map(item -> item.trim().toLowerCase(Locale.ROOT))
                .anyMatch(item -> item.equals(mode.configName));
        if (enabled && !ordered.contains(mode)) {
            ordered.add(mode);
        }
    }

    private Optional<TravelLegEstimate> queryRoute(AmapMode mode, Poi from, Poi to) {
        MultiValueMap<String, String> query = buildBaseQuery(from, to);
        String path = switch (mode) {
            case WALKING -> amapProperties.getWalkingPath();
            case BICYCLING -> amapProperties.getBicyclingPath();
            case TRANSIT -> {
                query.add("city", resolveCityName(from, to));
                query.add("cityd", resolveCityName(to, from));
                yield amapProperties.getTransitPath();
            }
            case DRIVING -> amapProperties.getDrivingPath();
        };
        addSignatureIfNecessary(query);
        JsonNode root = callJson(path, query);
        if (mode == AmapMode.BICYCLING) {
            if (root.path("errcode").asInt(-1) != 0) {
                String errCode = root.path("errcode").asText("");
                String errMsg = root.path("errmsg").asText("");
                log.warn("Amap route returned non-success bicycling response. mode={}, errcode={}, errmsg={}",
                        mode.configName, errCode, errMsg);
                return Optional.empty();
            }
        } else if (!"1".equals(root.path("status").asText())) {
            String infoCode = root.path("infocode").asText("");
            String info = root.path("info").asText("");
            log.warn("Amap route returned non-success status. mode={}, infocode={}, info={}", mode.configName, infoCode, info);
            return Optional.empty();
        }
        return parseEstimate(mode, root);
    }

    private MultiValueMap<String, String> buildBaseQuery(Poi from, Poi to) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        query.add("origin", formatLocation(from));
        query.add("destination", formatLocation(to));
        query.add("key", amapProperties.getApiKey().trim());
        return query;
    }

    private void addSignatureIfNecessary(MultiValueMap<String, String> query) {
        if (amapProperties.isSignRequests() && StringUtils.hasText(amapProperties.getSecurityKey())) {
            query.add("sig", sign(query));
        }
    }

    private JsonNode callJson(String path, MultiValueMap<String, String> query) {
        URI uri = buildUri(path, query);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "citytrip-backend/1.0");
        ResponseEntity<String> response = amapRestTemplate.exchange(uri, HttpMethod.GET, new HttpEntity<>(headers), String.class);
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            log.warn("Failed to parse Amap response json. uri={}, reason={}", uri, ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private URI buildUri(String path, MultiValueMap<String, String> query) {
        String baseUrl = amapProperties.getBaseUrl().trim();
        String resolvedPath = StringUtils.hasText(path) ? path.trim() : "";
        String url;
        if (resolvedPath.toLowerCase(Locale.ROOT).startsWith("http://")
                || resolvedPath.toLowerCase(Locale.ROOT).startsWith("https://")) {
            url = resolvedPath;
        } else if (baseUrl.endsWith("/") && resolvedPath.startsWith("/")) {
            url = baseUrl + resolvedPath.substring(1);
        } else if (!baseUrl.endsWith("/") && !resolvedPath.startsWith("/")) {
            url = baseUrl + "/" + resolvedPath;
        } else {
            url = baseUrl + resolvedPath;
        }
        UriComponentsBuilder builder = UriComponentsBuilder.fromHttpUrl(url);
        query.forEach((key, values) -> values.forEach(value -> builder.queryParam(key, value)));
        return builder.build().encode().toUri();
    }

    private Optional<TravelLegEstimate> parseEstimate(AmapMode mode, JsonNode root) {
        return switch (mode) {
            case WALKING, DRIVING -> parsePathEstimate(mode, root.path("route").path("paths").path(0));
            case BICYCLING -> parsePathEstimate(mode, root.path("data").path("paths").path(0));
            case TRANSIT -> parseTransitEstimate(root.path("route").path("transits").path(0));
        };
    }

    private Optional<TravelLegEstimate> parsePathEstimate(AmapMode mode, JsonNode path) {
        if (path.isMissingNode() || path.isNull()) {
            return Optional.empty();
        }
        Integer seconds = parsePositiveInt(path.path("duration"));
        Integer distanceMeters = parsePositiveInt(path.path("distance"));
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }
        List<GeoPoint> points = new ArrayList<>();
        List<GeoRouteStep> routeSteps = new ArrayList<>();
        JsonNode steps = path.path("steps");
        if (steps.isArray()) {
            for (JsonNode step : steps) {
                List<GeoPoint> stepPoints = parsePolyline(step.path("polyline").asText(""));
                points.addAll(stepPoints);
                routeSteps.add(new GeoRouteStep(
                        normalizeText(step.path("instruction").asText("")),
                        stepTypeFor(mode),
                        parsePositiveInt(step.path("distance")),
                        secondsToMinutes(parsePositiveInt(step.path("duration"))),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        stepPoints
                ));
            }
        }
        BigDecimal distanceKm = distanceMeters == null ? null : metersToKm(distanceMeters);
        GeoRouteEstimate detailed = new GeoRouteEstimate(toMinutes(seconds), distanceKm, mode.label, points, routeSteps);
        return Optional.of(new TravelLegEstimate(toMinutes(seconds), distanceKm, mode.label, points, detailed));
    }

    private String stepTypeFor(AmapMode mode) {
        return switch (mode) {
            case WALKING -> "walk";
            case BICYCLING -> "bike";
            case DRIVING -> "taxi";
            case TRANSIT -> "transit";
        };
    }

    private Optional<TravelLegEstimate> parseTransitEstimate(JsonNode transit) {
        if (transit.isMissingNode() || transit.isNull()) {
            return Optional.empty();
        }
        Integer seconds = parsePositiveInt(transit.path("duration"));
        if (seconds == null || seconds <= 0) {
            return Optional.empty();
        }
        Integer routeDistanceMeters = parsePositiveInt(transit.path("distance"));
        int segmentDistanceMeters = 0;
        List<GeoPoint> points = new ArrayList<>();
        List<GeoRouteStep> routeSteps = new ArrayList<>();
        JsonNode segments = transit.path("segments");
        if (segments.isArray()) {
            for (JsonNode segment : segments) {
                segmentDistanceMeters += parseSegmentDistance(segment);
                points.addAll(parseTransitSegmentPolyline(segment));
                routeSteps.addAll(parseTransitSegmentSteps(segment));
            }
        }
        int distanceMeters = routeDistanceMeters != null && routeDistanceMeters > 0
                ? routeDistanceMeters
                : segmentDistanceMeters;
        BigDecimal distanceKm = distanceMeters <= 0 ? null : metersToKm(distanceMeters);
        GeoRouteEstimate detailed = new GeoRouteEstimate(toMinutes(seconds), distanceKm, AmapMode.TRANSIT.label, points, routeSteps);
        return Optional.of(new TravelLegEstimate(toMinutes(seconds), distanceKm, AmapMode.TRANSIT.label, points, detailed));
    }

    private int parseSegmentDistance(JsonNode segment) {
        int meters = 0;
        Integer walking = parsePositiveInt(segment.path("walking").path("distance"));
        if (walking != null) {
            meters += walking;
        }
        JsonNode buslines = segment.path("bus").path("buslines");
        if (buslines.isArray()) {
            for (JsonNode busline : buslines) {
                Integer distance = parsePositiveInt(busline.path("distance"));
                if (distance != null) {
                    meters += distance;
                }
            }
        }
        return meters;
    }

    private List<GeoPoint> parseTransitSegmentPolyline(JsonNode segment) {
        List<GeoPoint> points = new ArrayList<>();
        JsonNode walkingSteps = segment.path("walking").path("steps");
        if (walkingSteps.isArray()) {
            for (JsonNode step : walkingSteps) {
                points.addAll(parsePolyline(step.path("polyline").asText("")));
            }
        }
        JsonNode buslines = segment.path("bus").path("buslines");
        if (buslines.isArray()) {
            for (JsonNode busline : buslines) {
                points.addAll(parsePolyline(busline.path("polyline").asText("")));
            }
        }
        return dedupeConsecutive(points);
    }

    private List<GeoRouteStep> parseTransitSegmentSteps(JsonNode segment) {
        List<GeoRouteStep> steps = new ArrayList<>();
        JsonNode walkingSteps = segment.path("walking").path("steps");
        if (walkingSteps.isArray()) {
            for (JsonNode step : walkingSteps) {
                List<GeoPoint> stepPoints = parsePolyline(step.path("polyline").asText(""));
                steps.add(new GeoRouteStep(
                        normalizeText(step.path("instruction").asText("")),
                        "walk",
                        parsePositiveInt(step.path("distance")),
                        secondsToMinutes(parsePositiveInt(step.path("duration"))),
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        stepPoints
                ));
            }
        }
        JsonNode buslines = segment.path("bus").path("buslines");
        if (buslines.isArray()) {
            for (JsonNode busline : buslines) {
                List<GeoPoint> busPoints = parsePolyline(busline.path("polyline").asText(""));
                steps.add(new GeoRouteStep(
                        normalizeText(busline.path("name").asText("")),
                        "bus",
                        parsePositiveInt(busline.path("distance")),
                        secondsToMinutes(parsePositiveInt(busline.path("duration"))),
                        normalizeText(busline.path("name").asText("")),
                        normalizeText(busline.path("departure_stop").path("name").asText("")),
                        normalizeText(busline.path("arrival_stop").path("name").asText("")),
                        null,
                        null,
                        parsePositiveInt(busline.path("via_num")),
                        busPoints
                ));
            }
        }
        return steps;
    }

    private List<GeoPoint> parsePolyline(String polyline) {
        if (!StringUtils.hasText(polyline)) {
            return List.of();
        }
        List<GeoPoint> points = new ArrayList<>();
        for (String pair : polyline.split(";")) {
            if (!StringUtils.hasText(pair) || !pair.contains(",")) {
                continue;
            }
            String[] parts = pair.split(",", 2);
            try {
                BigDecimal lng = new BigDecimal(parts[0].trim());
                BigDecimal lat = new BigDecimal(parts[1].trim());
                GeoPoint point = new GeoPoint(lat, lng);
                if (point.valid()) {
                    points.add(point);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed points from provider payloads.
            }
        }
        return dedupeConsecutive(points);
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

    private Integer parsePositiveInt(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        if (node.isNumber()) {
            return Math.max(0, node.asInt());
        }
        String text = node.asText("");
        if (!StringUtils.hasText(text)) {
            return null;
        }
        try {
            return Math.max(0, new BigDecimal(text.trim()).setScale(0, RoundingMode.HALF_UP).intValue());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private BigDecimal metersToKm(int meters) {
        return BigDecimal.valueOf(Math.max(0, meters))
                .divide(METERS_PER_KM, 1, RoundingMode.HALF_UP);
    }

    private int toMinutes(int seconds) {
        return Math.max(1, (int) Math.ceil(Math.max(0, seconds) / 60.0D));
    }

    private Integer secondsToMinutes(Integer seconds) {
        return seconds == null ? null : toMinutes(seconds);
    }

    private String normalizeText(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String formatLocation(Poi poi) {
        return poi.getLongitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString()
                + ","
                + poi.getLatitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }

    private String resolveCityName(Poi primary, Poi secondary) {
        if (primary != null && StringUtils.hasText(primary.getCityName())) {
            return primary.getCityName().trim();
        }
        if (secondary != null && StringUtils.hasText(secondary.getCityName())) {
            return secondary.getCityName().trim();
        }
        return StringUtils.hasText(amapProperties.getDefaultCityName())
                ? amapProperties.getDefaultCityName().trim()
                : "鎴愰兘";
    }

    private boolean samePoi(Poi from, Poi to) {
        return from.getId() != null && Objects.equals(from.getId(), to.getId());
    }

    private boolean hasCoordinate(Poi poi) {
        return poi != null
                && poi.getLatitude() != null
                && poi.getLongitude() != null
                && Math.abs(poi.getLatitude().doubleValue()) <= 90D
                && Math.abs(poi.getLongitude().doubleValue()) <= 180D;
    }

    private double straightDistanceKm(Poi from, Poi to) {
        if (!hasCoordinate(from) || !hasCoordinate(to)) {
            return Double.MAX_VALUE;
        }
        double lat1 = Math.toRadians(from.getLatitude().doubleValue());
        double lat2 = Math.toRadians(to.getLatitude().doubleValue());
        double deltaLat = lat1 - lat2;
        double deltaLon = Math.toRadians(from.getLongitude().doubleValue() - to.getLongitude().doubleValue());
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * 6371.0D * Math.asin(Math.sqrt(a));
    }

    private String sign(MultiValueMap<String, String> query) {
        TreeMap<String, String> sorted = new TreeMap<>();
        query.forEach((key, values) -> {
            if (!"sig".equals(key) && values != null && !values.isEmpty()) {
                sorted.put(key, values.get(0));
            }
        });
        StringBuilder raw = new StringBuilder();
        sorted.forEach((key, value) -> raw.append(key).append('=').append(value));
        raw.append(amapProperties.getSecurityKey().trim());
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            return HexFormat.of().formatHex(md5.digest(raw.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to sign Amap request", ex);
        }
    }

    private String safeName(Poi poi) {
        return poi == null ? null : poi.getName();
    }

    private enum AmapMode {
        WALKING("walking", "\u6B65\u884C"),
        BICYCLING("bicycling", "\u9A91\u884C"),
        TRANSIT("transit", "\u516C\u4EA4+\u6B65\u884C"),
        DRIVING("driving", "\u6253\u8F66");

        private final String configName;
        private final String label;

        AmapMode(String configName, String label) {
            this.configName = configName;
            this.label = label;
        }
    }
}


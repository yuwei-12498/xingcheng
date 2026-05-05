package com.citytrip.service.geo.impl;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.service.impl.vivo.VivoRequestIdFactory;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoRouteStep;
import com.citytrip.service.geo.GeoSearchService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;

@Service
public class GeoSearchServiceImpl implements GeoSearchService {

    private static final Logger log = LoggerFactory.getLogger(GeoSearchServiceImpl.class);
    private static final String VIVO_UNIFIED_GEO_ENDPOINT = "/search/geo";
    private static final double NEARBY_RADIUS_TOLERANCE = 1.15D;
    private static final int ROUTE_MIN_MINUTES = 1;
    private static final int ROUTE_MAX_MINUTES = 240;
    private static final BigDecimal INT_MAX_DECIMAL = BigDecimal.valueOf(Integer.MAX_VALUE);
    private static final BigDecimal INT_MIN_DECIMAL = BigDecimal.valueOf(Integer.MIN_VALUE);

    private final GeoSearchProperties geoSearchProperties;
    private final RestTemplate geoRestTemplate;
    private final ObjectMapper objectMapper;
    private final VivoRequestIdFactory vivoRequestIdFactory;

    public GeoSearchServiceImpl(GeoSearchProperties geoSearchProperties,
                                @Qualifier("geoRestTemplate") RestTemplate geoRestTemplate,
                                ObjectMapper objectMapper) {
        this.geoSearchProperties = geoSearchProperties;
        this.geoRestTemplate = geoRestTemplate;
        this.objectMapper = objectMapper;
        this.vivoRequestIdFactory = new VivoRequestIdFactory();
    }

    @Override
    public Optional<GeoPoint> geocode(String keyword, String cityName) {
        if (!isReady() || !StringUtils.hasText(keyword)) {
            return Optional.empty();
        }
        String normalizedKeyword = keyword.trim();

        if (!isUnifiedGeoEndpointBaseUrl()) {
            try {
                MultiValueMap<String, String> query = buildBaseQuery(cityName, 1);
                attachKeywordQuery(query, normalizedKeyword);
                query.add("address", normalizedKeyword);
                JsonNode root = callJson(resolveEndpointPath(geoSearchProperties.getGeocodePath()), query);
                Optional<GeoPoint> direct = extractGeoPoints(root).stream()
                        .filter(GeoPoint::valid)
                        .findFirst();
                if (direct.isPresent()) {
                    return direct;
                }
            } catch (Exception ex) {
                log.debug("Geocode direct call failed, fallback to keyword search. keyword={}, city={}, reason={}",
                        normalizedKeyword, cityName, ex.getMessage());
            }
        }

        List<GeoPoiCandidate> fromKeyword = searchByKeyword(normalizedKeyword, cityName, 1);
        return fromKeyword.stream()
                .map(GeoPoiCandidate::toPoint)
                .filter(GeoPoint::valid)
                .findFirst();
    }

    @Override
    public List<GeoPoiCandidate> searchByKeyword(String keyword, String cityName, int limit) {
        if (!isReady() || !StringUtils.hasText(keyword)) {
            return Collections.emptyList();
        }
        String normalizedKeyword = keyword.trim();
        int boundedLimit = normalizeLimit(limit);
        String keywordPath = resolveEndpointPath(geoSearchProperties.getKeywordSearchPath());

        try {
            MultiValueMap<String, String> query = buildBaseQuery(cityName, boundedLimit);
            attachKeywordQuery(query, normalizedKeyword);
            List<GeoPoiCandidate> primary = extractCandidates(callJson(keywordPath, query), boundedLimit, false, 0);
            if (!primary.isEmpty()) {
                return primary;
            }

            if (StringUtils.hasText(cityName)) {
                MultiValueMap<String, String> fallback = buildBaseQuery(null, boundedLimit);
                attachKeywordQuery(fallback, normalizedKeyword);
                List<GeoPoiCandidate> retried = extractCandidates(callJson(keywordPath, fallback), boundedLimit, false, 0);
                if (!retried.isEmpty()) {
                    return retried;
                }
            }
            return Collections.emptyList();
        } catch (Exception ex) {
            log.warn("Keyword GEO search failed. keyword={}, city={}, reason={}", normalizedKeyword, cityName, ex.getMessage());
            return Collections.emptyList();
        }
    }

    @Override
    public List<GeoPoiCandidate> searchNearby(GeoPoint center,
                                              String cityName,
                                              String category,
                                              int radiusMeters,
                                              int limit) {
        if (!isReady() || center == null || !center.valid()) {
            return Collections.emptyList();
        }
        int boundedLimit = normalizeLimit(limit);
        int boundedRadius = Math.max(200, radiusMeters <= 0 ? geoSearchProperties.getNearbyRadiusMeters() : radiusMeters);
        String nearbyPath = resolveEndpointPath(geoSearchProperties.getNearbySearchPath());
        String normalizedCategory = normalizeNearbyKeyword(category);
        List<String> attemptKeywords = buildNearbyAttemptKeywords(category, normalizedCategory);
        int attemptLimit = Math.max(8, boundedLimit);
        List<GeoPoiCandidate> merged = new ArrayList<>();

        for (String attemptKeyword : attemptKeywords) {
            try {
                MultiValueMap<String, String> query = buildBaseQuery(cityName, boundedLimit);
                addLocationParams(query, center, boundedRadius);
                attachKeywordQuery(query, attemptKeyword);
                if (StringUtils.hasText(category)) {
                    query.add("category", category.trim());
                    query.add("type", category.trim());
                }

                merged.addAll(extractCandidates(
                        callJson(nearbyPath, query),
                        attemptLimit,
                        true,
                        boundedRadius
                ));
                if (countWithinRadius(merged, boundedRadius) >= Math.min(2, boundedLimit)) {
                    break;
                }
            } catch (Exception ex) {
                log.debug("Nearby GEO search attempt failed. city={}, category={}, keyword={}, reason={}",
                        cityName, category, attemptKeyword, ex.getMessage());
            }
        }

        if (merged.isEmpty()) {
            return Collections.emptyList();
        }
        List<GeoPoiCandidate> ranked = rankNearbyCandidates(dedupeCandidates(merged), boundedRadius);
        return ranked.size() > boundedLimit ? ranked.subList(0, boundedLimit) : ranked;
    }

    @Override
    public Optional<GeoRouteEstimate> estimateTravel(GeoPoint from,
                                                     GeoPoint to,
                                                     String cityName,
                                                     String preferredMode) {
        if (!isReady()
                || from == null || !from.valid()
                || to == null || !to.valid()
                || !StringUtils.hasText(geoSearchProperties.getRoutePath())) {
            return Optional.empty();
        }
        try {
            MultiValueMap<String, String> query = buildBaseQuery(cityName, 1);
            addRouteParams(query, from, to, preferredMode);
            JsonNode root = callJson(geoSearchProperties.getRoutePath(), query);
            return parseRouteEstimate(root);
        } catch (Exception ex) {
            log.debug("Route GEO estimate failed. city={}, reason={}", cityName, ex.getMessage());
            return Optional.empty();
        }
    }

    private boolean isReady() {
        return geoSearchProperties.isEnabled()
                && StringUtils.hasText(geoSearchProperties.getBaseUrl());
    }

    private int normalizeLimit(int limit) {
        int configured = geoSearchProperties.getDefaultLimit();
        int fallback = configured <= 0 ? 8 : configured;
        return Math.max(1, Math.min(limit <= 0 ? fallback : limit, 20));
    }

    private MultiValueMap<String, String> buildBaseQuery(String cityName, int limit) {
        MultiValueMap<String, String> query = new LinkedMultiValueMap<>();
        String resolvedCity = StringUtils.hasText(cityName)
                ? cityName.trim()
                : (StringUtils.hasText(geoSearchProperties.getDefaultCityName()) ? geoSearchProperties.getDefaultCityName().trim() : null);
        if (isUnifiedGeoEndpointBaseUrl()) {
            if (StringUtils.hasText(resolvedCity)) {
                query.add("city", resolvedCity);
            }
            query.add("page_num", "1");
            query.add("page_size", String.valueOf(limit));
            query.add("requestId", vivoRequestIdFactory.create());
            return query;
        }
        if (StringUtils.hasText(cityName)) {
            String city = cityName.trim();
            query.add("city", city);
            query.add("cityName", city);
        } else if (StringUtils.hasText(geoSearchProperties.getDefaultCityName())) {
            String city = geoSearchProperties.getDefaultCityName().trim();
            query.add("city", city);
            query.add("cityName", city);
        }
        query.add("limit", String.valueOf(limit));
        query.add("size", String.valueOf(limit));
        attachApiKeyQuery(query);
        return query;
    }

    private void addLocationParams(MultiValueMap<String, String> query, GeoPoint center, int radiusMeters) {
        String lat = center.latitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String lng = center.longitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        query.add("latitude", lat);
        query.add("longitude", lng);
        query.add("lat", lat);
        query.add("lng", lng);
        query.add("location", lng + "," + lat);
        query.add("radius", String.valueOf(radiusMeters));
        query.add("distance", String.valueOf(radiusMeters));
    }

    private void addRouteParams(MultiValueMap<String, String> query,
                                GeoPoint from,
                                GeoPoint to,
                                String preferredMode) {
        String fromLat = from.latitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String fromLng = from.longitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String toLat = to.latitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
        String toLng = to.longitude().setScale(6, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();

        String origin = fromLng + "," + fromLat;
        String destination = toLng + "," + toLat;

        query.add("origin", origin);
        query.add("destination", destination);
        query.add("from", origin);
        query.add("to", destination);

        query.add("originLat", fromLat);
        query.add("originLng", fromLng);
        query.add("fromLat", fromLat);
        query.add("fromLng", fromLng);
        query.add("startLat", fromLat);
        query.add("startLng", fromLng);

        query.add("destinationLat", toLat);
        query.add("destinationLng", toLng);
        query.add("toLat", toLat);
        query.add("toLng", toLng);
        query.add("endLat", toLat);
        query.add("endLng", toLng);

        if (StringUtils.hasText(preferredMode)) {
            String mode = preferredMode.trim();
            query.add("mode", mode);
            query.add("strategy", mode);
            query.add("travelMode", mode);
        }
    }

    private String normalizeNearbyKeyword(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String normalized = category.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("hotel") || normalized.contains("住宿") || normalized.contains("旅馆")
                || normalized.contains("宾馆") || normalized.contains("酒店")) {
            return "酒店";
        }
        if (normalized.contains("food") || normalized.contains("餐饮") || normalized.contains("美食")
                || normalized.contains("餐厅") || normalized.contains("小吃")) {
            return "美食";
        }
        if (normalized.contains("shop") || normalized.contains("购物") || normalized.contains("商铺")
                || normalized.contains("商场") || normalized.contains("超市")) {
            return "商场";
        }
        if (normalized.contains("scenic") || normalized.contains("景点") || normalized.contains("attraction")) {
            return "景点";
        }
        return category.trim();
    }

    private List<String> buildNearbyAttemptKeywords(String category, String normalizedCategory) {
        Set<String> keywords = new LinkedHashSet<>();
        addKeyword(keywords, normalizedCategory);
        addKeyword(keywords, category);

        if ("酒店".equals(normalizedCategory)) {
            addKeyword(keywords, "宾馆");
            addKeyword(keywords, "住宿");
        } else if ("美食".equals(normalizedCategory)) {
            addKeyword(keywords, "餐厅");
            addKeyword(keywords, "火锅");
            addKeyword(keywords, "小吃");
        } else if ("商场".equals(normalizedCategory)) {
            addKeyword(keywords, "购物中心");
            addKeyword(keywords, "超市");
        } else if ("景点".equals(normalizedCategory)) {
            addKeyword(keywords, "旅游景点");
            addKeyword(keywords, "公园");
        }

        if (keywords.isEmpty()) {
            keywords.add("周边");
        }
        return new ArrayList<>(keywords);
    }

    private void addKeyword(Set<String> container, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        container.add(keyword.trim());
    }

    private void attachKeywordQuery(MultiValueMap<String, String> query, String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return;
        }
        String value = keyword.trim();
        query.add("keywords", value);
        query.add("keyword", value);
        query.add("query", value);
        query.add("q", value);
        query.add("name", value);
    }

    private void attachApiKeyQuery(MultiValueMap<String, String> query) {
        if (!StringUtils.hasText(geoSearchProperties.getApiKey())) {
            return;
        }
        String apiKey = geoSearchProperties.getApiKey().trim();
        String queryName = StringUtils.hasText(geoSearchProperties.getApiKeyQueryName())
                ? geoSearchProperties.getApiKeyQueryName().trim()
                : "key";
        query.add(queryName, apiKey);
        if (!"apiKey".equalsIgnoreCase(queryName)) {
            query.add("apiKey", apiKey);
        }
        if (!"key".equalsIgnoreCase(queryName)) {
            query.add("key", apiKey);
        }
    }

    private JsonNode callJson(String path, MultiValueMap<String, String> query) {
        URI uri = buildUri(path, query);
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.USER_AGENT, "citytrip-backend/1.0");
        if (isUnifiedGeoEndpointBaseUrl() && StringUtils.hasText(geoSearchProperties.getApiKey())) {
            headers.setBearerAuth(geoSearchProperties.getApiKey().trim());
        } else if (StringUtils.hasText(geoSearchProperties.getApiKeyHeaderName())
                && StringUtils.hasText(geoSearchProperties.getApiKey())) {
            headers.add(geoSearchProperties.getApiKeyHeaderName().trim(), geoSearchProperties.getApiKey().trim());
        }
        HttpEntity<Void> entity = new HttpEntity<>(headers);
        ResponseEntity<String> response = geoRestTemplate.exchange(uri, HttpMethod.GET, entity, String.class);
        String body = response.getBody();
        if (!StringUtils.hasText(body)) {
            return objectMapper.createObjectNode();
        }
        try {
            return objectMapper.readTree(body);
        } catch (Exception ex) {
            log.warn("Failed to parse GEO response json. uri={}, reason={}", uri, ex.getMessage());
            return objectMapper.createObjectNode();
        }
    }

    private URI buildUri(String path, MultiValueMap<String, String> query) {
        String baseUrl = geoSearchProperties.getBaseUrl().trim();
        String resolvedPath = StringUtils.hasText(path) ? path.trim() : "";
        String url;
        if (!StringUtils.hasText(resolvedPath)) {
            url = baseUrl;
        } else if (resolvedPath.toLowerCase(Locale.ROOT).startsWith("http://")
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
        if (query != null) {
            query.forEach((key, values) -> {
                if (!StringUtils.hasText(key) || values == null) {
                    return;
                }
                for (String value : values) {
                    if (StringUtils.hasText(value)) {
                        builder.queryParam(key, value);
                    }
                }
            });
        }
        return builder.build().encode().toUri();
    }

    private String resolveEndpointPath(String configuredPath) {
        if (isUnifiedGeoEndpointBaseUrl()) {
            return "";
        }
        return configuredPath;
    }

    private boolean isUnifiedGeoEndpointBaseUrl() {
        if (!StringUtils.hasText(geoSearchProperties.getBaseUrl())) {
            return false;
        }
        String lower = geoSearchProperties.getBaseUrl().trim().toLowerCase(Locale.ROOT);
        return lower.contains(VIVO_UNIFIED_GEO_ENDPOINT);
    }

    private List<GeoPoiCandidate> extractCandidates(JsonNode root,
                                                    int limit,
                                                    boolean nearbyMode,
                                                    int radiusMeters) {
        List<GeoPoiCandidate> candidates = new ArrayList<>();
        for (JsonNode node : extractCandidateNodes(root)) {
            GeoPoiCandidate candidate = parseCandidate(node);
            if (candidate == null || !candidate.toPoint().valid() || !StringUtils.hasText(candidate.getName())) {
                continue;
            }
            candidates.add(candidate);
        }
        if (candidates.isEmpty()) {
            return Collections.emptyList();
        }

        List<GeoPoiCandidate> deduped = dedupeCandidates(candidates);

        if (nearbyMode) {
            deduped = rankNearbyCandidates(deduped, radiusMeters);
        } else {
            deduped.sort(Comparator
                    .comparing((GeoPoiCandidate item) -> item.getScore() == null ? 0D : item.getScore(), Comparator.reverseOrder())
                    .thenComparing(item -> item.getName(), Comparator.nullsLast(String::compareTo)));
        }

        return deduped.size() > limit ? deduped.subList(0, limit) : deduped;
    }

    private List<GeoPoiCandidate> dedupeCandidates(List<GeoPoiCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> dedupe = new LinkedHashSet<>();
        List<GeoPoiCandidate> deduped = new ArrayList<>();
        for (GeoPoiCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getName())
                    || candidate.getLatitude() == null || candidate.getLongitude() == null) {
                continue;
            }
            String key = (candidate.getName() + "|" + candidate.getLatitude() + "|" + candidate.getLongitude())
                    .toLowerCase(Locale.ROOT);
            if (dedupe.add(key)) {
                deduped.add(candidate);
            }
        }
        return deduped;
    }

    private List<GeoPoiCandidate> rankNearbyCandidates(List<GeoPoiCandidate> candidates, int radiusMeters) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<GeoPoiCandidate> withDistance = candidates.stream()
                .filter(item -> item.getDistanceMeters() != null && item.getDistanceMeters() >= 0D)
                .sorted(Comparator
                        .comparing(GeoPoiCandidate::getDistanceMeters)
                        .thenComparing(item -> item.getScore() == null ? 0D : -item.getScore()))
                .toList();

        if (withDistance.isEmpty()) {
            candidates.sort(Comparator
                    .comparing((GeoPoiCandidate item) -> item.getScore() == null ? 0D : item.getScore(), Comparator.reverseOrder())
                    .thenComparing(item -> item.getName(), Comparator.nullsLast(String::compareTo)));
            return candidates;
        }

        if (radiusMeters <= 0) {
            return new ArrayList<>(withDistance);
        }

        double threshold = radiusMeters * NEARBY_RADIUS_TOLERANCE;
        List<GeoPoiCandidate> inRadius = withDistance.stream()
                .filter(item -> item.getDistanceMeters() <= threshold)
                .toList();
        if (inRadius.size() >= 2) {
            return new ArrayList<>(inRadius);
        }
        return new ArrayList<>(withDistance);
    }

    private long countWithinRadius(List<GeoPoiCandidate> candidates, int radiusMeters) {
        if (candidates == null || candidates.isEmpty() || radiusMeters <= 0) {
            return 0;
        }
        double threshold = radiusMeters * NEARBY_RADIUS_TOLERANCE;
        return candidates.stream()
                .filter(item -> item != null && item.getDistanceMeters() != null && item.getDistanceMeters() <= threshold)
                .count();
    }

    private Optional<GeoRouteEstimate> parseRouteEstimate(JsonNode root) {
        JsonNode route = resolveRouteNode(root);
        if (route == null || route.isMissingNode() || route.isNull()) {
            return Optional.empty();
        }

        Integer minutes = extractDurationMinutes(route);
        BigDecimal distanceKm = extractDistanceKm(route);
        String mode = extractTransportMode(route);
        List<GeoPoint> pathPoints = extractTopLevelPathPoints(route);
        List<GeoRouteStep> steps = extractRouteSteps(route);

        if (minutes == null) {
            minutes = extractDurationMinutes(root);
        }
        if (distanceKm == null) {
            distanceKm = extractDistanceKm(root);
        }
        if (!StringUtils.hasText(mode)) {
            mode = extractTransportMode(root);
        }
        if (pathPoints.isEmpty()) {
            pathPoints = extractTopLevelPathPoints(root);
        }
        if (steps.isEmpty()) {
            steps = extractRouteSteps(root);
        }
        if (pathPoints.isEmpty() && !steps.isEmpty()) {
            pathPoints = mergeStepPathPoints(steps);
        }

        if (minutes == null && distanceKm == null) {
            return Optional.empty();
        }

        if (minutes == null && distanceKm != null) {
            minutes = inferMinutesFromDistance(distanceKm);
        }
        if (distanceKm == null && minutes != null) {
            distanceKm = inferDistanceFromMinutes(minutes);
        }
        if (!StringUtils.hasText(mode)) {
            mode = inferTransportMode(distanceKm, minutes);
        }
        if (minutes == null || minutes <= 0) {
            return Optional.empty();
        }

        int boundedMinutes = Math.max(ROUTE_MIN_MINUTES, Math.min(minutes, ROUTE_MAX_MINUTES));
        BigDecimal normalizedDistance = distanceKm == null
                ? null
                : distanceKm.setScale(1, RoundingMode.HALF_UP);
        return Optional.of(new GeoRouteEstimate(boundedMinutes, normalizedDistance, mode, pathPoints, steps));
    }

    private JsonNode resolveRouteNode(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return null;
        }
        if (root.isArray() && root.size() > 0) {
            return root.get(0);
        }

        JsonNode route = root.path("route");
        JsonNode fromRoute = resolveRouteArrayCandidate(route);
        if (fromRoute != null) {
            return fromRoute;
        }
        if (isDirectRoutePayload(route)) {
            return route;
        }

        JsonNode data = root.path("data");
        JsonNode fromData = resolveRouteArrayCandidate(data);
        if (fromData != null) {
            return fromData;
        }

        JsonNode result = root.path("result");
        JsonNode fromResult = resolveRouteArrayCandidate(result);
        if (fromResult != null) {
            return fromResult;
        }

        JsonNode direct = resolveRouteArrayCandidate(root);
        if (direct != null) {
            return direct;
        }
        return root;
    }

    private boolean isDirectRoutePayload(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isObject()) {
            return false;
        }
        if (extractDurationMinutes(node) != null
                || extractDistanceKm(node) != null
                || StringUtils.hasText(extractTransportMode(node))) {
            return true;
        }
        if (!extractTopLevelPathPoints(node).isEmpty()) {
            return true;
        }
        return !extractRouteStepNodes(node).isEmpty();
    }

    private JsonNode resolveRouteArrayCandidate(JsonNode container) {
        if (container == null || container.isMissingNode() || container.isNull()) {
            return null;
        }
        JsonNode routes = container.path("routes");
        if (routes.isArray() && routes.size() > 0) {
            return routes.get(0);
        }
        JsonNode paths = container.path("paths");
        if (paths.isArray() && paths.size() > 0) {
            return paths.get(0);
        }
        JsonNode items = container.path("items");
        if (items.isArray() && items.size() > 0) {
            return items.get(0);
        }
        JsonNode list = container.path("list");
        if (list.isArray() && list.size() > 0) {
            return list.get(0);
        }
        return null;
    }

    private Integer extractDurationMinutes(JsonNode node) {
        Integer minutes = firstInteger(node, "durationMinutes", "estimatedMinutes", "timeMinutes", "etaMinutes", "costTimeMinutes");
        if (minutes != null && minutes > 0) {
            return minutes;
        }

        Integer seconds = firstInteger(node, "durationSeconds", "durationSec", "timeSeconds", "costTimeSeconds");
        if (seconds != null && seconds > 0) {
            return Math.max(1, (int) Math.ceil(seconds / 60D));
        }

        Integer textMinutes = parseDurationText(firstText(node, "durationText", "timeText", "costTimeText", "etaText"));
        if (textMinutes != null && textMinutes > 0) {
            return textMinutes;
        }

        return null;
    }

    private BigDecimal extractDistanceKm(JsonNode node) {
        BigDecimal kilometer = firstDecimal(node, "distanceKm", "estimatedDistanceKm", "lengthKm");
        if (kilometer != null && kilometer.compareTo(BigDecimal.ZERO) > 0) {
            return kilometer;
        }

        BigDecimal meter = firstDecimal(node, "distanceMeters", "distanceMeter", "distanceM", "length", "distanceValue");
        if (meter != null && meter.compareTo(BigDecimal.ZERO) > 0) {
            return meter.divide(BigDecimal.valueOf(1000D), 4, RoundingMode.HALF_UP);
        }

        return null;
    }

    private String extractTransportMode(JsonNode node) {
        return firstText(node, "transportMode", "mode", "travelMode", "strategy", "vehicle");
    }

    private List<GeoPoint> extractTopLevelPathPoints(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }

        List<GeoPoint> direct = parseGeometryNode(node);
        if (!direct.isEmpty()) {
            return dedupeConsecutivePathPoints(direct);
        }
        return List.of();
    }

    private List<GeoRouteStep> extractRouteSteps(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }
        List<JsonNode> stepNodes = extractRouteStepNodes(node);
        if (stepNodes.isEmpty()) {
            return List.of();
        }

        List<GeoRouteStep> steps = new ArrayList<>();
        for (JsonNode stepNode : stepNodes) {
            GeoRouteStep step = parseRouteStep(stepNode);
            if (step != null) {
                steps.add(step);
            }
        }
        return steps.isEmpty() ? List.of() : List.copyOf(steps);
    }

    private List<JsonNode> extractRouteStepNodes(JsonNode node) {
        List<JsonNode> topLevelNodes = new ArrayList<>();
        for (String field : List.of("steps", "segments")) {
            JsonNode items = node.path(field);
            if (!items.isArray() || items.isEmpty()) {
                continue;
            }
            for (JsonNode item : items) {
                if (item != null && !item.isNull() && !item.isMissingNode()) {
                    topLevelNodes.add(item);
                }
            }
        }
        if (!topLevelNodes.isEmpty()) {
            return dedupeStepNodes(topLevelNodes);
        }

        List<JsonNode> legNodes = new ArrayList<>();
        JsonNode legs = node.path("legs");
        if (legs.isArray() && !legs.isEmpty()) {
            for (JsonNode leg : legs) {
                JsonNode steps = leg.path("steps");
                if (!steps.isArray() || steps.isEmpty()) {
                    continue;
                }
                for (JsonNode step : steps) {
                    if (step != null && !step.isNull() && !step.isMissingNode()) {
                        legNodes.add(step);
                    }
                }
            }
        }
        return legNodes.isEmpty() ? List.of() : dedupeStepNodes(legNodes);
    }

    private List<JsonNode> dedupeStepNodes(List<JsonNode> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        List<JsonNode> deduped = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();
        for (JsonNode node : nodes) {
            if (node == null || node.isNull() || node.isMissingNode()) {
                continue;
            }
            String signature = node.toString();
            if (signatures.add(signature)) {
                deduped.add(node);
            }
        }
        return deduped.isEmpty() ? List.of() : List.copyOf(deduped);
    }

    private GeoRouteStep parseRouteStep(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String instruction = firstText(node, "instruction", "instructionText", "text", "narrative", "description", "desc");
        String type = normalizeStepType(firstText(node, "type", "stepType", "mode", "transportMode", "travelMode", "vehicle", "action", "kind"));
        Integer distanceMeters = extractDistanceMeters(node);
        Integer durationMinutes = extractDurationMinutes(node);
        String lineName = firstTextOrNodeName(node, "lineName", "line", "routeName", "lineInfo", "lineInfoName", "busLine", "subwayLine");
        String fromStation = firstTextOrNodeName(node, "fromStation", "fromStop", "fromSite", "departureStop", "startStation", "originStation");
        String toStation = firstTextOrNodeName(node, "toStation", "toStop", "toSite", "arrivalStop", "endStation", "destinationStation");
        String entranceName = firstTextOrNodeName(node, "entranceName", "entrance", "entryName", "enterName");
        String exitName = firstTextOrNodeName(node, "exitName", "exit", "outName");
        Integer stopCount = firstInteger(node, "stopCount", "stationCount", "passStationCount", "stops");
        List<GeoPoint> pathPoints = dedupeConsecutivePathPoints(parseGeometryNode(node));

        if (!StringUtils.hasText(instruction)
                && !StringUtils.hasText(type)
                && distanceMeters == null
                && durationMinutes == null
                && !StringUtils.hasText(lineName)
                && !StringUtils.hasText(fromStation)
                && !StringUtils.hasText(toStation)
                && !StringUtils.hasText(entranceName)
                && !StringUtils.hasText(exitName)
                && stopCount == null
                && pathPoints.isEmpty()) {
            return null;
        }

        return new GeoRouteStep(
                instruction,
                type,
                distanceMeters,
                durationMinutes,
                lineName,
                fromStation,
                toStation,
                entranceName,
                exitName,
                stopCount,
                pathPoints
        );
    }

    private Integer extractDistanceMeters(JsonNode node) {
        Integer directMeters = firstInteger(node, "distanceMeters", "distanceMeter", "distanceM", "length", "distanceValue");
        if (directMeters != null && directMeters > 0) {
            return directMeters;
        }

        BigDecimal kilometer = firstDecimal(node, "distanceKm", "lengthKm");
        if (kilometer != null && kilometer.compareTo(BigDecimal.ZERO) > 0) {
            return kilometer.multiply(BigDecimal.valueOf(1000D))
                    .setScale(0, RoundingMode.HALF_UP)
                    .intValue();
        }

        return null;
    }

    private String normalizeStepType(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("walk") || normalized.contains("步行")) {
            return "walk";
        }
        if (normalized.contains("metro") || normalized.contains("subway") || normalized.contains("地铁")) {
            return "metro";
        }
        if (normalized.contains("bus") || normalized.contains("公交")) {
            return "bus";
        }
        if (normalized.contains("taxi") || normalized.contains("cab") || normalized.contains("打车") || normalized.contains("驾车")) {
            return "taxi";
        }
        if (normalized.contains("enter") || normalized.contains("entry") || normalized.contains("入口") || normalized.contains("进站")) {
            return "enter";
        }
        if (normalized.contains("exit") || normalized.contains("out") || normalized.contains("出口") || normalized.contains("出站")) {
            return "exit";
        }
        if (normalized.contains("transfer") || normalized.contains("change") || normalized.contains("interchange") || normalized.contains("换乘")) {
            return "transfer";
        }
        return normalized;
    }

    private String firstTextOrNodeName(JsonNode node, String... fields) {
        String text = firstText(node, fields);
        if (StringUtils.hasText(text)) {
            return text;
        }
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value == null || value.isMissingNode() || value.isNull()) {
                continue;
            }
            if (value.isObject()) {
                String nested = firstText(value, "name", "stationName", "title", "desc", "description");
                if (StringUtils.hasText(nested)) {
                    return nested;
                }
            }
        }
        return null;
    }

    private List<GeoPoint> mergeStepPathPoints(List<GeoRouteStep> steps) {
        if (steps == null || steps.isEmpty()) {
            return List.of();
        }
        List<GeoPoint> merged = new ArrayList<>();
        for (GeoRouteStep step : steps) {
            if (step == null || step.pathPoints() == null || step.pathPoints().isEmpty()) {
                continue;
            }
            merged.addAll(step.pathPoints());
        }
        return dedupeConsecutivePathPoints(merged);
    }

    private List<GeoPoint> parseGeometryNode(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return List.of();
        }

        List<GeoPoint> points = parseCoordinateText(firstText(node, "polyline", "path", "points", "geometry"));
        if (!points.isEmpty()) {
            return points;
        }

        for (String field : List.of("polyline", "coordinates", "path", "points")) {
            List<GeoPoint> parsed = parseCoordinateArray(node.path(field));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        JsonNode geometry = node.path("geometry");
        if (!geometry.isMissingNode() && !geometry.isNull()) {
            points = parseCoordinateText(geometry.isTextual() ? geometry.asText() : null);
            if (!points.isEmpty()) {
                return points;
            }
            List<GeoPoint> parsed = parseCoordinateArray(geometry.path("coordinates"));
            if (!parsed.isEmpty()) {
                return parsed;
            }
            parsed = parseCoordinateArray(geometry.path("points"));
            if (!parsed.isEmpty()) {
                return parsed;
            }
        }

        return List.of();
    }

    private List<GeoPoint> parseCoordinateText(String value) {
        if (!StringUtils.hasText(value)) {
            return List.of();
        }
        String normalized = value.trim();
        if (normalized.indexOf(',') < 0) {
            return List.of();
        }
        String[] pairs = normalized.split("[;|]");
        List<GeoPoint> points = new ArrayList<>();
        for (String pair : pairs) {
            GeoPoint point = parseLocationText(pair);
            if (point != null && point.valid()) {
                points.add(point);
            }
        }
        return points;
    }

    private List<GeoPoint> parseCoordinateArray(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull() || !node.isArray()) {
            return List.of();
        }
        List<GeoPoint> points = new ArrayList<>();
        for (JsonNode item : node) {
            if (item == null || item.isNull() || item.isMissingNode()) {
                continue;
            }
            if (item.isArray() && item.size() >= 2) {
                BigDecimal lng = toDecimal(item.get(0).asText());
                BigDecimal lat = toDecimal(item.get(1).asText());
                if (lat != null && lng != null) {
                    GeoPoint point = new GeoPoint(lat, lng);
                    if (point.valid()) {
                        points.add(point);
                    }
                }
                continue;
            }
            GeoPoint point = parsePoint(item);
            if (point != null && point.valid()) {
                points.add(point);
            }
        }
        return points;
    }

    private List<GeoPoint> dedupeConsecutivePathPoints(List<GeoPoint> points) {
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

    private Integer parseDurationText(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String normalized = text.trim().toLowerCase(Locale.ROOT);
        java.util.regex.Matcher hourMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:h|hr|hour|hours|\\u5C0F\\u65F6)")
                .matcher(normalized);
        java.util.regex.Matcher minuteMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:m|min|mins|minute|minutes|\\u5206\\u949F|\\u5206)")
                .matcher(normalized);
        java.util.regex.Matcher secondMatcher = java.util.regex.Pattern
                .compile("(\\d+(?:\\.\\d+)?)\\s*(?:s|sec|secs|second|seconds|\\u79D2)")
                .matcher(normalized);

        double minutes = 0D;
        if (hourMatcher.find()) {
            minutes += Double.parseDouble(hourMatcher.group(1)) * 60D;
        }
        if (minuteMatcher.find()) {
            minutes += Double.parseDouble(minuteMatcher.group(1));
        }
        if (secondMatcher.find()) {
            minutes += Double.parseDouble(secondMatcher.group(1)) / 60D;
        }
        if (minutes > 0D) {
            return Math.max(1, (int) Math.ceil(minutes));
        }
        return null;
    }

    private Integer inferMinutesFromDistance(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        double km = distanceKm.doubleValue();
        double speedKmPerMinute;
        if (km <= 1.2D) {
            speedKmPerMinute = 4.8D / 60D;
        } else if (km <= 4.5D) {
            speedKmPerMinute = 14D / 60D;
        } else if (km <= 10D) {
            speedKmPerMinute = 22D / 60D;
        } else {
            speedKmPerMinute = 30D / 60D;
        }
        int bufferMinutes = km <= 2D ? 3 : (km <= 8D ? 7 : 10);
        return Math.max(1, (int) Math.ceil(km / speedKmPerMinute) + bufferMinutes);
    }

    private BigDecimal inferDistanceFromMinutes(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return null;
        }
        double km;
        if (minutes <= 12) {
            km = minutes * (4.8D / 60D);
        } else if (minutes <= 30) {
            km = minutes * (14D / 60D);
        } else if (minutes <= 60) {
            km = minutes * (22D / 60D);
        } else {
            km = minutes * (30D / 60D);
        }
        return BigDecimal.valueOf(km);
    }

    private String inferTransportMode(BigDecimal distanceKm, Integer minutes) {
        if (distanceKm != null) {
            double km = distanceKm.doubleValue();
            if (km <= 1.2D) {
                return "步行";
            }
            if (km <= 3.5D) {
                return "骑行";
            }
            if (km <= 12D) {
                return "公交+步行";
            }
            return "打车";
        }
        int duration = minutes == null ? 0 : minutes;
        if (duration <= 12) {
            return "步行";
        }
        if (duration <= 22) {
            return "骑行";
        }
        if (duration <= 45) {
            return "公交+步行";
        }
        return "打车";
    }

    private List<GeoPoint> extractGeoPoints(JsonNode root) {
        List<GeoPoint> points = new ArrayList<>();
        for (JsonNode node : extractCandidateNodes(root)) {
            GeoPoint point = parsePoint(node);
            if (point != null && point.valid()) {
                points.add(point);
            }
        }
        if (!points.isEmpty()) {
            return points;
        }

        GeoPoint direct = parsePoint(root);
        if (direct != null && direct.valid()) {
            points.add(direct);
        }
        return points;
    }

    private List<JsonNode> extractCandidateNodes(JsonNode root) {
        if (root == null || root.isMissingNode() || root.isNull()) {
            return Collections.emptyList();
        }
        if (root.isArray()) {
            return asList(root);
        }

        List<JsonNode> candidates = tryResolveArray(root, "pois", "results", "items", "candidates", "list", "data");
        if (!candidates.isEmpty()) {
            return candidates;
        }

        JsonNode data = root.path("data");
        if (data.isObject()) {
            candidates = tryResolveArray(data, "pois", "results", "items", "candidates", "list", "geocodes");
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }

        JsonNode result = root.path("result");
        if (result.isObject()) {
            candidates = tryResolveArray(result, "pois", "results", "items", "candidates", "list", "geocodes");
            if (!candidates.isEmpty()) {
                return candidates;
            }
        }

        JsonNode geocodes = root.path("geocodes");
        if (geocodes.isArray()) {
            return asList(geocodes);
        }

        return List.of(root);
    }

    private List<JsonNode> tryResolveArray(JsonNode parent, String... fields) {
        if (parent == null || fields == null) {
            return Collections.emptyList();
        }
        for (String field : fields) {
            JsonNode node = parent.path(field);
            if (node.isArray() && node.size() > 0) {
                return asList(node);
            }
        }
        return Collections.emptyList();
    }

    private List<JsonNode> asList(JsonNode arrayNode) {
        List<JsonNode> list = new ArrayList<>();
        arrayNode.forEach(list::add);
        return list;
    }

    private GeoPoiCandidate parseCandidate(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        GeoPoint point = parsePoint(node);
        if (point == null || !point.valid()) {
            return null;
        }

        GeoPoiCandidate candidate = new GeoPoiCandidate();
        candidate.setName(firstText(node, "name", "title", "poiName", "display_name"));
        candidate.setAddress(firstText(node, "address", "addr", "formatted_address", "locationName"));
        candidate.setCategory(firstText(node, "category", "type", "poiType", "typeName", "typeCode", "tag"));
        candidate.setDistrict(firstText(node, "district", "adname", "area"));
        candidate.setCityName(firstText(node, "city", "cityName", "cityname"));
        candidate.setLatitude(point.latitude());
        candidate.setLongitude(point.longitude());
        candidate.setSource(firstText(node, "source", "provider", "dataSource", "src"));
        candidate.setExternalId(firstText(node, "id", "poiId", "uid", "place_id", "mid", "nid", "cpid"));
        candidate.setScore(firstDouble(node, "score", "confidence", "weight", "rank_score"));
        candidate.setOpeningHours(firstText(node,
                "openingHours", "businessHours", "openHours", "open_hours", "opentime", "openTimeText", "hours"));
        candidate.setOpenTime(firstText(node,
                "openTime", "openingTime", "startTime", "businessStartTime", "open_time"));
        candidate.setCloseTime(firstText(node,
                "closeTime", "closingTime", "endTime", "businessEndTime", "close_time"));
        candidate.setAvgCost(firstDecimal(node,
                "avgCost", "averageCost", "avg_price", "avgPrice", "perCapitaCost", "costPerPerson", "price"));
        candidate.setStayDurationMinutes(firstInteger(node,
                "stayDurationMinutes", "recommendedStayMinutes", "visitDurationMinutes", "durationMinutes", "stay_minutes"));
        candidate.setDistanceMeters(firstDouble(node, "distance", "dis"));
        return candidate;
    }

    private GeoPoint parsePoint(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }

        BigDecimal lat = firstDecimal(node, "lat", "latitude");
        BigDecimal lng = firstDecimal(node, "lng", "lon", "longitude");
        if (lat != null && lng != null) {
            return new GeoPoint(lat, lng);
        }

        String location = firstText(node, "location", "loc", "coordinate", "coordinates", "naviLocation");
        GeoPoint fromLocation = parseLocationText(location);
        if (fromLocation != null) {
            return fromLocation;
        }

        JsonNode pointNode = node.path("point");
        if (pointNode.isObject()) {
            lat = firstDecimal(pointNode, "lat", "latitude");
            lng = firstDecimal(pointNode, "lng", "lon", "longitude");
            if (lat != null && lng != null) {
                return new GeoPoint(lat, lng);
            }
        }
        return null;
    }

    private GeoPoint parseLocationText(String location) {
        if (!StringUtils.hasText(location)) {
            return null;
        }
        String normalized = location.trim();
        String[] parts = normalized.split(",");
        if (parts.length == 2) {
            BigDecimal lng = toDecimal(parts[0]);
            BigDecimal lat = toDecimal(parts[1]);
            if (lat != null && lng != null) {
                return new GeoPoint(lat, lng);
            }
        }
        parts = normalized.split("\\s+");
        if (parts.length == 2) {
            BigDecimal lat = toDecimal(parts[0]);
            BigDecimal lng = toDecimal(parts[1]);
            if (lat != null && lng != null) {
                return new GeoPoint(lat, lng);
            }
        }
        return null;
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isTextual()) {
                String text = value.asText().trim();
                if (StringUtils.hasText(text)) {
                    return text;
                }
            }
        }
        return null;
    }

    private BigDecimal firstDecimal(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return null;
        }
        for (String field : fields) {
            JsonNode value = node.path(field);
            if (value.isNumber()) {
                return value.decimalValue();
            }
            if (value.isTextual()) {
                BigDecimal parsed = toDecimal(value.asText());
                if (parsed != null) {
                    return parsed;
                }
            }
        }
        return null;
    }

    private Integer firstInteger(JsonNode node, String... fields) {
        BigDecimal decimal = firstDecimal(node, fields);
        if (decimal == null) {
            return null;
        }
        BigDecimal rounded = decimal.setScale(0, RoundingMode.HALF_UP);
        if (rounded.compareTo(INT_MAX_DECIMAL) > 0) {
            return Integer.MAX_VALUE;
        }
        if (rounded.compareTo(INT_MIN_DECIMAL) < 0) {
            return Integer.MIN_VALUE;
        }
        return rounded.intValue();
    }

    private Double firstDouble(JsonNode node, String... fields) {
        BigDecimal decimal = firstDecimal(node, fields);
        return decimal == null ? null : decimal.doubleValue();
    }

    private BigDecimal toDecimal(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        try {
            return new BigDecimal(value.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

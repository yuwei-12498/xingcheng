package com.citytrip.service.ai.mcp;

import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeoNearbyMcpCapability implements McpCapability {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GeoSearchService geoSearchService;

    public GeoNearbyMcpCapability(GeoSearchService geoSearchService) {
        this.geoSearchService = geoSearchService;
    }

    @Override
    public String capabilityName() {
        return "geo.nearby";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> arguments = normalizeInput(input);
        GeoPoint center = resolveCenter(arguments);
        String city = firstText(arguments, "city", "cityName");
        String category = text(arguments, "category");
        int radiusMeters = clamp(intValue(arguments, "radiusMeters", 1500), 200, 5000);
        int limit = clamp(intValue(arguments, "limit", 5), 1, 10);
        if (center == null || !center.valid()) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "valid center is required"
            );
        }
        if (geoSearchService == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "unavailable",
                    "message", "geo service is not configured"
            );
        }
        List<GeoPoiCandidate> candidates = geoSearchService.searchNearby(
                center,
                city,
                StringUtils.hasText(category) ? category.trim() : null,
                radiusMeters,
                limit
        );
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capabilityName());
        result.put("status", "ok");
        result.put("city", city);
        result.put("category", category);
        result.put("radiusMeters", radiusMeters);
        result.put("results", candidates.stream().limit(limit).map(this::toResult).toList());
        return result;
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Search nearby POIs around the given center point.");
    }

    private Map<String, Object> toResult(GeoPoiCandidate candidate) {
        Map<String, Object> item = new LinkedHashMap<>();
        if (candidate == null) {
            return item;
        }
        item.put("name", candidate.getName());
        item.put("address", candidate.getAddress());
        item.put("category", candidate.getCategory());
        item.put("district", candidate.getDistrict());
        item.put("cityName", candidate.getCityName());
        item.put("latitude", candidate.getLatitude());
        item.put("longitude", candidate.getLongitude());
        item.put("source", candidate.getSource());
        item.put("distanceMeters", candidate.getDistanceMeters());
        item.put("openingHours", candidate.getOpeningHours());
        item.put("avgCost", candidate.getAvgCost());
        return item;
    }

    private GeoPoint resolveCenter(Map<String, Object> arguments) {
        Object center = arguments.get("center");
        if (center instanceof GeoPoint point) {
            return point.valid() ? point : null;
        }
        if (center instanceof Map<?, ?> map) {
            return toPoint(map);
        }
        GeoPoint point = new GeoPoint(
                decimal(firstPresent(arguments, "lat", "latitude", "userLat")),
                decimal(firstPresent(arguments, "lng", "longitude", "userLng"))
        );
        return point.valid() ? point : null;
    }

    private GeoPoint toPoint(Map<?, ?> map) {
        GeoPoint point = new GeoPoint(
                decimal(firstPresent(map, "lat", "latitude", "userLat")),
                decimal(firstPresent(map, "lng", "longitude", "userLng"))
        );
        return point.valid() ? point : null;
    }

    private Map<String, Object> normalizeInput(Object input) {
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        if (input instanceof String text && text.trim().startsWith("{")) {
            try {
                return OBJECT_MAPPER.readValue(text, new TypeReference<>() {
                });
            } catch (Exception ignored) {
                return Map.of();
            }
        }
        return Map.of();
    }

    private Object firstPresent(Map<?, ?> map, String... fields) {
        for (String field : fields) {
            Object value = map.get(field);
            if (value != null) {
                return value;
            }
        }
        return null;
    }

    private String firstText(Map<String, Object> map, String... fields) {
        for (String field : fields) {
            String value = text(map, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String text(Map<String, Object> map, String field) {
        Object value = map.get(field);
        return value == null ? "" : String.valueOf(value).trim();
    }

    private int intValue(Map<String, Object> map, String field, int defaultValue) {
        Object value = map.get(field);
        if (value instanceof Number number) {
            return number.intValue();
        }
        if (value != null) {
            try {
                return Integer.parseInt(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }

    private BigDecimal decimal(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof BigDecimal decimal) {
            return decimal;
        }
        if (value instanceof Number number) {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try {
            return new BigDecimal(String.valueOf(value));
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}

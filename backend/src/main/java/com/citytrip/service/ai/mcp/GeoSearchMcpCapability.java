package com.citytrip.service.ai.mcp;

import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class GeoSearchMcpCapability implements McpCapability {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final GeoSearchService geoSearchService;

    public GeoSearchMcpCapability(GeoSearchService geoSearchService) {
        this.geoSearchService = geoSearchService;
    }

    @Override
    public String capabilityName() {
        return "geo.search";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> arguments = normalizeInput(input);
        String keyword = text(arguments, "keyword");
        String city = firstText(arguments, "city", "cityName");
        int limit = clamp(intValue(arguments, "limit", 5), 1, 10);
        if (!StringUtils.hasText(keyword)) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "keyword is required"
            );
        }
        if (geoSearchService == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "unavailable",
                    "message", "geo service is not configured"
            );
        }
        List<GeoPoiCandidate> candidates = geoSearchService == null
                ? List.of()
                : geoSearchService.searchByKeyword(keyword, city, limit);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capabilityName());
        result.put("status", "ok");
        result.put("keyword", keyword);
        result.put("city", city);
        result.put("results", candidates.stream().limit(limit).map(this::toResult).toList());
        return result;
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Search live city POIs through the configured geo provider.");
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

    private Map<String, Object> normalizeInput(Object input) {
        if (input instanceof Map<?, ?> map) {
            Map<String, Object> normalized = new LinkedHashMap<>();
            map.forEach((key, value) -> normalized.put(String.valueOf(key), value));
            return normalized;
        }
        if (input instanceof String text) {
            String trimmed = text.trim();
            if (trimmed.startsWith("{")) {
                try {
                    return OBJECT_MAPPER.readValue(trimmed, new TypeReference<>() {
                    });
                } catch (Exception ignored) {
                    return Map.of("keyword", trimmed);
                }
            }
            return Map.of("keyword", trimmed);
        }
        return Map.of();
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
}

package com.citytrip.service.ai.mcp;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class AmapRouteMcpCapability implements McpCapability {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final TravelTimeService travelTimeService;

    public AmapRouteMcpCapability(TravelTimeService travelTimeService) {
        this.travelTimeService = travelTimeService;
    }

    @Override
    public String capabilityName() {
        return "route.amap";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> arguments = normalizeInput(input);
        Poi from = toPoi(arguments.get("from"), "from");
        Poi to = toPoi(arguments.get("to"), "to");
        if (from == null || to == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "from and to are required"
            );
        }
        if (travelTimeService == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "unavailable",
                    "message", "travel time service is not configured"
            );
        }
        TravelTimeService.TravelLegEstimate estimate = travelTimeService == null
                ? new TravelTimeService.TravelLegEstimate(0, null, null)
                : travelTimeService.estimateTravelLeg(from, to);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capabilityName());
        result.put("status", "ok");
        result.put("from", from.getName());
        result.put("to", to.getName());
        result.put("estimatedMinutes", estimate.estimatedMinutes());
        result.put("estimatedDistanceKm", estimate.estimatedDistanceKm());
        result.put("transportMode", estimate.transportMode());
        result.put("pathPoints", estimate.pathPoints());
        return result;
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Estimate route travel time, distance and transport mode.");
    }

    private Poi toPoi(Object value, String fallbackName) {
        if (value instanceof Poi poi) {
            return poi;
        }
        Poi poi = new Poi();
        poi.setName(fallbackName);
        if (value instanceof String text) {
            poi.setName(text);
            return poi;
        }
        if (value instanceof Map<?, ?> map) {
            poi.setName(text(map, "name", fallbackName));
            poi.setDistrict(text(map, "district", null));
            poi.setLatitude(decimal(firstPresent(map, "lat", "latitude")));
            poi.setLongitude(decimal(firstPresent(map, "lng", "longitude")));
            return poi;
        }
        return null;
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

    private String text(Map<?, ?> map, String field, String defaultValue) {
        Object value = map.get(field);
        return value == null ? defaultValue : String.valueOf(value);
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

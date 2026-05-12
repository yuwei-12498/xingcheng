package com.citytrip.service.ai.mcp;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ItinerarySnapshotMcpCapability implements McpCapability {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;

    public ItinerarySnapshotMcpCapability(SavedItineraryRepository savedItineraryRepository,
                                          SavedItineraryCodec savedItineraryCodec) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
    }

    @Override
    public String capabilityName() {
        return "itinerary.snapshot";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> arguments = normalizeInput(input);
        Long itineraryId = longValue(arguments, "itineraryId");
        if (itineraryId == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "itineraryId is required"
            );
        }
        if (savedItineraryRepository == null || savedItineraryCodec == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "unavailable",
                    "message", "itinerary snapshot service is not configured"
            );
        }
        try {
            SavedItinerary entity = savedItineraryRepository.reload(itineraryId);
            if (entity == null) {
                return Map.of(
                        "capability", capabilityName(),
                        "status", "empty",
                        "message", "itinerary was not found",
                        "itineraryId", itineraryId
                );
            }
            ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);
            Map<String, Object> result = new LinkedHashMap<>();
            result.put("capability", capabilityName());
            result.put("status", "ok");
            result.put("itineraryId", itineraryId);
            result.put("selectedOptionKey", itinerary == null ? null : itinerary.getSelectedOptionKey());
            result.put("recommendReason", itinerary == null ? null : itinerary.getRecommendReason());
            result.put("summary", buildSummary(entity, itinerary));
            result.put("shareNote", entity.getShareNote());
            result.put("routePath", buildRoutePath(itinerary == null ? null : itinerary.getNodes()));
            result.put("totalDuration", itinerary == null ? null : itinerary.getTotalDuration());
            result.put("totalCost", itinerary == null ? null : itinerary.getTotalCost());
            result.put("nodes", summarizeNodes(itinerary == null ? null : itinerary.getNodes()));
            return result;
        } catch (Exception ex) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "failed to load itinerary snapshot"
            );
        }
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Load the persisted itinerary snapshot for route verification.");
    }

    private List<Map<String, Object>> summarizeNodes(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return List.of();
        }
        return nodes.stream()
                .filter(node -> node != null && StringUtils.hasText(node.getPoiName()))
                .limit(8)
                .map(node -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("name", node.getPoiName());
                    item.put("category", node.getCategory());
                    item.put("district", node.getDistrict());
                    item.put("travelMode", node.getTravelTransportMode());
                    item.put("travelMinutes", node.getTravelTime());
                    return item;
                })
                .toList();
    }

    private String buildSummary(SavedItinerary entity, ItineraryVO itinerary) {
        List<String> parts = new java.util.ArrayList<>();
        if (itinerary != null) {
            append(parts, "selectedOption", itinerary.getSelectedOptionKey());
            append(parts, "recommendReason", itinerary.getRecommendReason());
            append(parts, "routePath", buildRoutePath(itinerary.getNodes()));
        }
        if (entity != null) {
            append(parts, "shareNote", entity.getShareNote());
        }
        return String.join(" | ", parts);
    }

    private String buildRoutePath(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        return nodes.stream()
                .filter(node -> node != null && StringUtils.hasText(node.getPoiName()))
                .limit(8)
                .map(node -> node.getPoiName().trim())
                .collect(Collectors.joining(" -> "));
    }

    private void append(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "=" + value.trim());
        }
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
                    return Map.of("itineraryId", trimmed);
                }
            }
            return Map.of("itineraryId", trimmed);
        }
        return Map.of();
    }

    private Long longValue(Map<String, Object> map, String field) {
        Object value = map.get(field);
        if (value instanceof Number number) {
            return number.longValue();
        }
        if (value != null) {
            try {
                return Long.parseLong(String.valueOf(value));
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }
}

package com.citytrip.service.impl.vivo;

import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import com.citytrip.service.application.community.CommunitySemanticSearchService;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class VivoToolRegistryBootstrap {

    public VivoToolRegistryBootstrap(VivoToolRegistry registry,
                                     ObjectMapper objectMapper,
                                     PoiService poiService,
                                     CommunityItineraryQueryService communityItineraryQueryService) {
        registry.register(new VivoToolDefinition(
                "search_poi",
                "Search live POIs by keyword and city",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}",
                argumentsJson -> writeJson(objectMapper, Map.of(
                        "tool", "search_poi",
                        "status", "ok",
                        "results", poiService.searchLive(
                                textValue(objectMapper, argumentsJson, "keyword"),
                                textValue(objectMapper, argumentsJson, "city"),
                                intValue(objectMapper, argumentsJson, "limit", 5)
                        )
                ))
        ));
        registry.register(new VivoToolDefinition(
                "search_community_posts",
                "Search community itineraries by semantic keyword",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}",
                argumentsJson -> {
                    String keyword = textValue(objectMapper, argumentsJson, "keyword");
                    int limit = intValue(objectMapper, argumentsJson, "limit", 5);
                    CommunityItineraryPageVO page = communityItineraryQueryService.listPublic(1, limit, "latest", keyword, null, null);
                    List<Map<String, Object>> results = page.getRecords().stream()
                            .limit(limit)
                            .map(this::toCommunityResult)
                            .toList();
                    return writeJson(objectMapper, Map.of(
                            "tool", "search_community_posts",
                            "status", "ok",
                            "results", results
                    ));
                }
        ));
        registry.register(new VivoToolDefinition(
                "get_route_context",
                "Summarize route context passed from the current itinerary",
                "{\"type\":\"object\",\"properties\":{\"summary\":{\"type\":\"string\"},\"nodes\":{\"type\":\"array\"}}}",
                argumentsJson -> {
                    JsonNode root = readTree(objectMapper, argumentsJson);
                    Map<String, Object> result = new LinkedHashMap<>();
                    result.put("tool", "get_route_context");
                    result.put("status", "ok");
                    result.put("summary", root.path("summary").asText(""));
                    result.put("nodes", root.path("nodes"));
                    return writeJson(objectMapper, result);
                }
        ));
    }

    private Map<String, Object> toCommunityResult(CommunityItineraryVO item) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", item.getId());
        result.put("title", item.getTitle());
        result.put("shareNote", item.getShareNote());
        result.put("routeSummary", item.getRouteSummary());
        result.put("themes", item.getThemes());
        return result;
    }

    private String textValue(ObjectMapper objectMapper, String argumentsJson, String field) {
        return readTree(objectMapper, argumentsJson).path(field).asText("");
    }

    private int intValue(ObjectMapper objectMapper, String argumentsJson, String field, int defaultValue) {
        JsonNode node = readTree(objectMapper, argumentsJson).path(field);
        return node.canConvertToInt() ? node.asInt() : defaultValue;
    }

    private JsonNode readTree(ObjectMapper objectMapper, String json) {
        try {
            return objectMapper.readTree(json == null ? "{}" : json);
        } catch (Exception ex) {
            return objectMapper.createObjectNode();
        }
    }

    private String writeJson(ObjectMapper objectMapper, Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception ex) {
            return "{\"status\":\"error\",\"message\":\"serialization failed\"}";
        }
    }
}

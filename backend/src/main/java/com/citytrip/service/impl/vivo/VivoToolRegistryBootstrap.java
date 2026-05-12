package com.citytrip.service.impl.vivo;

import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Component
public class VivoToolRegistryBootstrap {

    public VivoToolRegistryBootstrap(VivoToolRegistry registry,
                                     ObjectMapper objectMapper,
                                     PoiService poiService,
                                     CommunityItineraryQueryService communityItineraryQueryService) {
        this(registry, objectMapper, poiService, communityItineraryQueryService, (McpCapabilityRegistry) null);
    }

    @Autowired
    public VivoToolRegistryBootstrap(VivoToolRegistry registry,
                                     ObjectMapper objectMapper,
                                     PoiService poiService,
                                     CommunityItineraryQueryService communityItineraryQueryService,
                                     ObjectProvider<McpCapabilityRegistry> mcpCapabilityRegistryProvider) {
        this(
                registry,
                objectMapper,
                poiService,
                communityItineraryQueryService,
                mcpCapabilityRegistryProvider == null ? null : mcpCapabilityRegistryProvider.getIfAvailable()
        );
    }

    VivoToolRegistryBootstrap(VivoToolRegistry registry,
                              ObjectMapper objectMapper,
                              PoiService poiService,
                              CommunityItineraryQueryService communityItineraryQueryService,
                              McpCapabilityRegistry mcpCapabilityRegistry) {
        registry.register(new VivoToolDefinition(
                "search_poi",
                "Search live POIs by keyword and city",
                "{\"type\":\"object\",\"properties\":{\"keyword\":{\"type\":\"string\"},\"city\":{\"type\":\"string\"},\"limit\":{\"type\":\"integer\"}}}",
                argumentsJson -> searchPoiPayload(objectMapper, poiService, mcpCapabilityRegistry, argumentsJson)
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

    private String searchPoiPayload(ObjectMapper objectMapper,
                                    PoiService poiService,
                                    McpCapabilityRegistry mcpCapabilityRegistry,
                                    String argumentsJson) {
        String keyword = textValue(objectMapper, argumentsJson, "keyword");
        String city = textValue(objectMapper, argumentsJson, "city");
        int limit = intValue(objectMapper, argumentsJson, "limit", 5);
        Optional<Object> mcpResult = executeGeoSearchMcp(mcpCapabilityRegistry, keyword, city, limit);
        if (mcpResult.isPresent()) {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("tool", "search_poi");
            payload.put("status", "ok");
            payload.put("mcpCapability", "geo.search");
            payload.put("results", extractResults(mcpResult.get()));
            return writeJson(objectMapper, payload);
        }
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("tool", "search_poi");
        payload.put("status", "ok");
        payload.put("results", poiService.searchLive(keyword, city, limit));
        return writeJson(objectMapper, payload);
    }

    private Optional<Object> executeGeoSearchMcp(McpCapabilityRegistry mcpCapabilityRegistry,
                                                 String keyword,
                                                 String city,
                                                 int limit) {
        if (mcpCapabilityRegistry == null) {
            return Optional.empty();
        }
        try {
            Optional<Object> result = mcpCapabilityRegistry.find("geo.search")
                    .map(capability -> capability.execute(Map.of(
                            "keyword", keyword == null ? "" : keyword,
                            "city", city == null ? "" : city,
                            "limit", limit
                    )));
            if (result.isEmpty() || !hasSearchResults(result.get())) {
                return Optional.empty();
            }
            return result;
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private boolean hasSearchResults(Object mcpResult) {
        if (mcpResult instanceof Map<?, ?> map) {
            Object status = map.get("status");
            if (status != null && !"ok".equalsIgnoreCase(String.valueOf(status))) {
                return false;
            }
            Object results = map.get("results");
            if (results instanceof List<?> list) {
                return !list.isEmpty();
            }
            return map.containsKey("results");
        }
        if (mcpResult instanceof List<?> list) {
            return !list.isEmpty();
        }
        return mcpResult != null;
    }

    private Object extractResults(Object mcpResult) {
        if (mcpResult instanceof Map<?, ?> map && map.get("results") != null) {
            return map.get("results");
        }
        if (mcpResult instanceof List<?>) {
            return mcpResult;
        }
        List<Object> single = new ArrayList<>();
        single.add(mcpResult);
        return single;
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

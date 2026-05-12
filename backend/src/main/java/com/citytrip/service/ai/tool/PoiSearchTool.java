package com.citytrip.service.ai.tool;

import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.Map;

public class PoiSearchTool {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final McpCapabilityRegistry registry;

    public PoiSearchTool(McpCapabilityRegistry registry) {
        this.registry = registry;
    }

    public String search(String keyword, String cityName) {
        Object result = registry.find("geo.search")
                .orElseThrow(() -> new IllegalStateException("geo.search capability missing"))
                .execute(Map.of(
                        "keyword", keyword == null ? "" : keyword,
                        "city", cityName == null ? "" : cityName,
                        "limit", 5
                ));
        if (result instanceof String text) {
            return text;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(result);
        } catch (Exception ex) {
            return "{\"capability\":\"geo.search\",\"status\":\"error\",\"message\":\"serialization failed\"}";
        }
    }
}

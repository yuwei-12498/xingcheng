package com.citytrip.service.ai.tool;

import com.citytrip.service.ai.mcp.McpCapability;
import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class PoiSearchToolTest {

    @Test
    void shouldDelegateToGeoCapabilityByStandardName() {
        McpCapabilityRegistry registry = new McpCapabilityRegistry(List.of(new EchoGeoCapability()));
        PoiSearchTool tool = new PoiSearchTool(registry);

        assertThat(tool.search("万象城", "成都"))
                .contains("\"capability\":\"geo.search\"")
                .contains("\"keyword\":\"万象城\"")
                .contains("\"city\":\"成都\"");
    }

    private static class EchoGeoCapability implements McpCapability {
        @Override
        public String capabilityName() {
            return "geo.search";
        }

        @Override
        public Object execute(Object input) {
            return Map.of(
                    "capability", "geo.search",
                    "keyword", ((Map<?, ?>) input).get("keyword"),
                    "city", ((Map<?, ?>) input).get("city")
            );
        }
    }
}

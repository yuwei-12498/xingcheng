package com.citytrip.service.ai.mcp;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class RouteContextMcpCapability implements McpCapability {

    @Override
    public String capabilityName() {
        return "route.context";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capabilityName());
        result.put("status", "ok");
        if (input instanceof ChatRouteContextSkillService.RouteContext routeContext) {
            result.put("selectedOptionKey", routeContext.selectedOptionKey());
            result.put("summary", routeContext.summary());
            result.put("nodeCount", routeContext.nodes() == null ? 0 : routeContext.nodes().size());
            result.put("nodes", routeContext.nodes() == null
                    ? List.of()
                    : routeContext.nodes().stream()
                    .filter(node -> node != null && StringUtils.hasText(node.getPoiName()))
                    .map(this::nodeSummary)
                    .toList());
            return result;
        }
        if (input instanceof Map<?, ?> map) {
            result.put("selectedOptionKey", value(map, "selectedOptionKey"));
            result.put("summary", value(map, "summary"));
            Object nodes = map.get("nodes");
            result.put("nodes", nodes == null ? List.of() : nodes);
            if (nodes instanceof List<?> list) {
                result.put("nodeCount", list.size());
            }
            return result;
        }
        result.put("summary", input == null ? "" : String.valueOf(input));
        result.put("nodes", List.of());
        result.put("nodeCount", 0);
        return result;
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Normalize the current itinerary route context for model grounding.");
    }

    private Map<String, Object> nodeSummary(ChatReqDTO.ChatRouteNode node) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("name", node.getPoiName());
        item.put("category", node.getCategory());
        item.put("district", node.getDistrict());
        item.put("travelMode", node.getTravelTransportMode());
        item.put("travelMinutes", node.getTravelTime());
        return item;
    }

    private Object value(Map<?, ?> map, String key) {
        return map.get(key);
    }
}

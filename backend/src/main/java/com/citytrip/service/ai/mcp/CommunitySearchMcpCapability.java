package com.citytrip.service.ai.mcp;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class CommunitySearchMcpCapability implements McpCapability {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final CommunityItineraryQueryService communityItineraryQueryService;

    public CommunitySearchMcpCapability(CommunityItineraryQueryService communityItineraryQueryService) {
        this.communityItineraryQueryService = communityItineraryQueryService;
    }

    @Override
    public String capabilityName() {
        return "community.search";
    }

    @Override
    public Object execute(Object input) {
        Map<String, Object> arguments = normalizeInput(input);
        String keyword = firstText(arguments, "keyword", "query", "question");
        int limit = clamp(intValue(arguments, "limit", 5), 1, 10);
        Long currentUserId = longValue(arguments, "currentUserId");
        String theme = text(arguments, "theme");
        if (!StringUtils.hasText(keyword)) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "error",
                    "message", "keyword is required"
            );
        }
        if (communityItineraryQueryService == null) {
            return Map.of(
                    "capability", capabilityName(),
                    "status", "unavailable",
                    "message", "community service is not configured"
            );
        }
        CommunityItineraryPageVO page = communityItineraryQueryService.listPublic(
                1,
                limit,
                "latest",
                keyword,
                StringUtils.hasText(theme) ? theme.trim() : null,
                currentUserId
        );
        List<CommunityItineraryVO> records = page == null || page.getRecords() == null ? List.of() : page.getRecords();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("capability", capabilityName());
        result.put("status", "ok");
        result.put("keyword", keyword);
        result.put("results", records.stream().limit(limit).map(this::toResult).toList());
        return result;
    }

    @Override
    public java.util.Optional<String> description() {
        return java.util.Optional.of("Search community route posts using the application query service.");
    }

    private Map<String, Object> toResult(CommunityItineraryVO item) {
        Map<String, Object> result = new LinkedHashMap<>();
        if (item == null) {
            return result;
        }
        result.put("id", item.getId());
        result.put("title", item.getTitle());
        result.put("cityName", item.getCityName());
        result.put("shareNote", item.getShareNote());
        result.put("routeSummary", item.getRouteSummary());
        result.put("themes", item.getThemes());
        result.put("highlights", item.getHighlights());
        result.put("likeCount", item.getLikeCount());
        result.put("commentCount", item.getCommentCount());
        result.put("liked", item.getLiked());
        result.put("updatedAt", item.getUpdatedAt());
        return result;
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

    private int clamp(int value, int min, int max) {
        return Math.max(min, Math.min(max, value));
    }
}

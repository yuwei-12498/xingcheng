package com.citytrip.service.ai.runtime;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.ai.mcp.McpCapability;
import com.citytrip.service.ai.mcp.McpCapabilityRegistry;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.ai.model.AiPlatformProperties;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.RetrievalDocument;
import com.citytrip.service.domain.ai.ChatRouteContextSkillService;
import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class AiChatAugmentationService {
    private static final List<String> QUERY_HINT_MARKERS = List.of(
            "附近", "周边", "旁边", "有什么", "有啥", "推荐", "去哪", "哪里", "哪儿", "好玩", "好吃", "吃饭", "拍照", "逛", "怎么玩", "怎么安排"
    );
    private static final List<String> COMMUNITY_QUERY_MARKERS = List.of(
            "社区", "帖子", "攻略"
    );
    private static final List<String> ROUTE_QUERY_MARKERS = List.of(
            "多久", "多远", "怎么走", "路线", "交通", "耗时", "赶", "顺路"
    );

    private final AiRetrieverFacade aiRetrieverFacade;
    private final VivoFunctionCallingService vivoFunctionCallingService;
    private final McpCapabilityRegistry mcpCapabilityRegistry;
    private final ObjectMapper objectMapper;
    private final AiPlatformProperties.Augmentation augmentationProperties;
    private final ChatAugmentationIntentResolver intentResolver;

    public AiChatAugmentationService(AiRetrieverFacade aiRetrieverFacade,
                                     VivoFunctionCallingService vivoFunctionCallingService,
                                     McpCapabilityRegistry mcpCapabilityRegistry) {
        this(aiRetrieverFacade, vivoFunctionCallingService, mcpCapabilityRegistry, new ObjectMapper(),
                new AiPlatformProperties.Augmentation(), new ChatAugmentationIntentResolver());
    }

    public AiChatAugmentationService(AiRetrieverFacade aiRetrieverFacade,
                                     VivoFunctionCallingService vivoFunctionCallingService,
                                     McpCapabilityRegistry mcpCapabilityRegistry,
                                     ObjectMapper objectMapper) {
        this(aiRetrieverFacade, vivoFunctionCallingService, mcpCapabilityRegistry, objectMapper,
                new AiPlatformProperties.Augmentation(), new ChatAugmentationIntentResolver());
    }

    public AiChatAugmentationService(AiRetrieverFacade aiRetrieverFacade,
                                     VivoFunctionCallingService vivoFunctionCallingService,
                                     McpCapabilityRegistry mcpCapabilityRegistry,
                                     ObjectMapper objectMapper,
                                     AiPlatformProperties.Augmentation augmentationProperties) {
        this(aiRetrieverFacade, vivoFunctionCallingService, mcpCapabilityRegistry, objectMapper,
                augmentationProperties, new ChatAugmentationIntentResolver());
    }

    public AiChatAugmentationService(AiRetrieverFacade aiRetrieverFacade,
                                     VivoFunctionCallingService vivoFunctionCallingService,
                                     McpCapabilityRegistry mcpCapabilityRegistry,
                                     ObjectMapper objectMapper,
                                     AiPlatformProperties.Augmentation augmentationProperties,
                                     ChatAugmentationIntentResolver intentResolver) {
        this.aiRetrieverFacade = aiRetrieverFacade;
        this.vivoFunctionCallingService = vivoFunctionCallingService;
        this.mcpCapabilityRegistry = mcpCapabilityRegistry;
        this.objectMapper = objectMapper == null ? new ObjectMapper() : objectMapper;
        this.augmentationProperties = augmentationProperties == null ? new AiPlatformProperties.Augmentation() : augmentationProperties;
        this.intentResolver = intentResolver == null ? new ChatAugmentationIntentResolver() : intentResolver;
    }

    public AiChatAugmentationContext build(ChatReqDTO req,
                                           ChatRouteContextSkillService.RouteContext routeContext) {
        return build(req, routeContext, true, true);
    }

    public AiChatAugmentationContext build(ChatReqDTO req,
                                           ChatRouteContextSkillService.RouteContext routeContext,
                                           boolean toolEnabled,
                                           boolean mcpEnabled) {
        String question = safe(req == null ? null : req.getQuestion());
        Long userId = resolveUserId(req);
        String cityName = resolveCityName(req);
        Long itineraryId = resolveItineraryId(req);
        String nearbyKeyword = extractNearbyKeyword(question);
        ChatAugmentationIntent intent = intentResolver.resolve(req, routeContext);

        List<String> evidence = new ArrayList<>();
        List<String> ragDocuments = collectRagDocuments(question, userId, cityName, itineraryId, toolEnabled, mcpEnabled, evidence);
        List<String> toolPayloads = toolEnabled ? collectToolPayloads(question, cityName, nearbyKeyword, intent, evidence) : List.of();
        List<String> mcpEvidence = mcpEnabled
                ? collectMcpEvidence(req, routeContext, question, cityName, nearbyKeyword, intent, toolPayloads, evidence)
                : List.of();

        return AiChatAugmentationContext.of(ragDocuments, toolPayloads, mcpEvidence, evidence);
    }

    private List<String> collectRagDocuments(String question,
                                             Long userId,
                                             String cityName,
                                             Long itineraryId,
                                             boolean toolEnabled,
                                             boolean mcpEnabled,
                                             List<String> evidence) {
        if (aiRetrieverFacade == null || !StringUtils.hasText(question)) {
            return List.of();
        }
        AiExecutionContext context = AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput(question)
                .userId(userId)
                .cityName(cityName)
                .itineraryId(itineraryId)
                .ragEnabled(true)
                .toolEnabled(toolEnabled)
                .mcpEnabled(mcpEnabled)
                .build();
        List<String> documents = new ArrayList<>();
        try {
            for (RetrievalDocument document : aiRetrieverFacade.retrieve(context)) {
                if (document == null || !StringUtils.hasText(document.content())) {
                    continue;
                }
                String source = StringUtils.hasText(document.source()) ? document.source().trim() : "unknown";
                documents.add(summarizeTextPayload(source, document.content().trim()));
                evidence.add("rag:" + source);
                if (documents.size() >= maxRagDocuments()) {
                    break;
                }
            }
        } catch (RuntimeException ex) {
            evidence.add("rag:error");
        }
        return documents;
    }

    private List<String> collectToolPayloads(String question,
                                             String cityName,
                                             String nearbyKeyword,
                                             ChatAugmentationIntent intent,
                                             List<String> evidence) {
        if (vivoFunctionCallingService == null || !StringUtils.hasText(question)) {
            return List.of();
        }
        List<String> payloads = new ArrayList<>();
        if (StringUtils.hasText(nearbyKeyword)) {
            addToolPayload(payloads, evidence, "search_poi", Map.of(
                    "keyword", nearbyKeyword,
                    "city", cityName,
                    "limit", 5
            ));
        }
        if (intent == ChatAugmentationIntent.COMMUNITY_GUIDE || containsAny(question, COMMUNITY_QUERY_MARKERS)) {
            addToolPayload(payloads, evidence, "search_community_posts", Map.of(
                    "keyword", question,
                    "limit", 5
            ));
        }
        return payloads.size() > maxToolPayloads() ? List.copyOf(payloads.subList(0, maxToolPayloads())) : List.copyOf(payloads);
    }

    private void addToolPayload(List<String> payloads,
                                List<String> evidence,
                                String toolName,
                                Map<String, Object> arguments) {
        try {
            String payload = vivoFunctionCallingService.executeToolCall(toolName, objectMapper.writeValueAsString(arguments));
            if (StringUtils.hasText(payload) && !VivoFunctionCallingService.NO_RESULT.equals(payload)) {
                payloads.add(summarizeJsonPayload(payload.trim(), toolName));
                evidence.add("tool:" + toolName);
            }
        } catch (Exception ex) {
            evidence.add("tool:" + toolName + ":error");
        }
    }

    private List<String> collectMcpEvidence(ChatReqDTO req,
                                            ChatRouteContextSkillService.RouteContext routeContext,
                                            String question,
                                            String cityName,
                                            String nearbyKeyword,
                                            ChatAugmentationIntent intent,
                                            List<String> toolPayloads,
                                            List<String> evidence) {
        if (mcpCapabilityRegistry == null) {
            return List.of();
        }
        List<String> result = new ArrayList<>();
        if (intent == ChatAugmentationIntent.NEARBY_DISCOVERY && !hasLivePoiToolPayload(toolPayloads)) {
            Optional<Map<String, Object>> nearbyInput = buildNearbyInput(req, routeContext, cityName, question);
            if (nearbyInput.isPresent()) {
                executeMcp("geo.nearby", nearbyInput.get(), evidence).ifPresent(result::add);
            } else if (StringUtils.hasText(nearbyKeyword)) {
                executeMcp("geo.search", Map.of(
                        "keyword", nearbyKeyword,
                        "city", cityName,
                        "limit", 5
                ), evidence).ifPresent(result::add);
            }
        }
        if (intent == ChatAugmentationIntent.COMMUNITY_GUIDE) {
            Map<String, Object> input = new LinkedHashMap<>();
            input.put("keyword", question);
            input.put("limit", 5);
            Long currentUserId = resolveUserId(req);
            if (currentUserId != null) {
                input.put("currentUserId", currentUserId);
            }
            executeMcp("community.search", input, evidence).ifPresent(result::add);
        }
        if (routeContext != null && routeContext.available()) {
            Map<String, Object> routeInput = new LinkedHashMap<>();
            routeInput.put("selectedOptionKey", routeContext.selectedOptionKey());
            routeInput.put("summary", routeContext.summary());
            routeInput.put("nodes", routeContext.nodes().stream()
                    .filter(node -> node != null && StringUtils.hasText(node.getPoiName()))
                    .map(node -> Map.of(
                            "name", node.getPoiName(),
                            "travelMode", safe(node.getTravelTransportMode()),
                            "travelMinutes", node.getTravelTime() == null ? "" : node.getTravelTime()
                    ))
                    .toList());
            executeMcp("route.context", routeInput, evidence).ifPresent(result::add);
        }
        if (intent == ChatAugmentationIntent.ROUTE_VERIFY) {
            buildItinerarySnapshotInput(req).ifPresent(input ->
                    executeMcp("itinerary.snapshot", input, evidence).ifPresent(result::add)
            );
        }
        if (intent == ChatAugmentationIntent.ROUTE_VERIFY && shouldFetchRouteEstimate(question)) {
            buildRouteEstimateInput(req).ifPresent(input ->
                    executeMcp("route.amap", input, evidence).ifPresent(result::add)
            );
        }
        return result.size() > maxMcpEvidence() ? List.copyOf(result.subList(0, maxMcpEvidence())) : List.copyOf(result);
    }

    private boolean hasLivePoiToolPayload(List<String> toolPayloads) {
        if (toolPayloads == null || toolPayloads.isEmpty()) {
            return false;
        }
        return toolPayloads.stream().anyMatch(payload -> StringUtils.hasText(payload) && payload.contains("search_poi"));
    }

    private boolean shouldFetchRouteEstimate(String question) {
        return StringUtils.hasText(question) && containsAny(question, ROUTE_QUERY_MARKERS);
    }

    private Optional<Map<String, Object>> buildNearbyInput(ChatReqDTO req,
                                                           ChatRouteContextSkillService.RouteContext routeContext,
                                                           String cityName,
                                                           String question) {
        Map<String, Object> center = new LinkedHashMap<>();
        if (req != null && req.getContext() != null
                && req.getContext().getUserLat() != null
                && req.getContext().getUserLng() != null) {
            center.put("latitude", req.getContext().getUserLat());
            center.put("longitude", req.getContext().getUserLng());
        } else if (routeContext != null && routeContext.firstNode() != null
                && routeContext.firstNode().getLatitude() != null
                && routeContext.firstNode().getLongitude() != null) {
            center.put("latitude", routeContext.firstNode().getLatitude());
            center.put("longitude", routeContext.firstNode().getLongitude());
        } else if (req != null
                && req.getContext() != null
                && req.getContext().getItinerary() != null
                && req.getContext().getItinerary().getNodes() != null) {
            for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
                if (node == null || node.getLatitude() == null || node.getLongitude() == null) {
                    continue;
                }
                center.put("latitude", node.getLatitude());
                center.put("longitude", node.getLongitude());
                break;
            }
        }
        if (!center.containsKey("latitude") || !center.containsKey("longitude")) {
            return Optional.empty();
        }
        Map<String, Object> input = new LinkedHashMap<>();
        input.put("center", center);
        input.put("city", cityName);
        String category = inferNearbyCategory(question);
        if (StringUtils.hasText(category)) {
            input.put("category", category);
        }
        input.put("radiusMeters", 1500);
        input.put("limit", 5);
        return Optional.of(input);
    }

    private Optional<Map<String, Object>> buildRouteEstimateInput(ChatReqDTO req) {
        if (req == null
                || req.getContext() == null
                || req.getContext().getItinerary() == null
                || req.getContext().getItinerary().getNodes() == null) {
            return Optional.empty();
        }
        List<ChatReqDTO.ChatRouteNode> nodes = req.getContext().getItinerary().getNodes().stream()
                .filter(node -> node != null
                        && StringUtils.hasText(node.getPoiName())
                        && node.getLatitude() != null
                        && node.getLongitude() != null)
                .limit(2)
                .toList();
        if (nodes.size() < 2) {
            return Optional.empty();
        }
        ChatReqDTO.ChatRouteNode from = nodes.get(0);
        ChatReqDTO.ChatRouteNode to = nodes.get(1);
        return Optional.of(Map.of(
                "from", Map.of(
                        "name", from.getPoiName(),
                        "district", safe(from.getDistrict()),
                        "latitude", from.getLatitude(),
                        "longitude", from.getLongitude()
                ),
                "to", Map.of(
                        "name", to.getPoiName(),
                        "district", safe(to.getDistrict()),
                        "latitude", to.getLatitude(),
                        "longitude", to.getLongitude()
                )
        ));
    }

    private Optional<Map<String, Object>> buildItinerarySnapshotInput(ChatReqDTO req) {
        Long itineraryId = resolveItineraryId(req);
        return itineraryId == null ? Optional.empty() : Optional.of(Map.of("itineraryId", itineraryId));
    }

    private Optional<String> executeMcp(String capabilityName, Object input, List<String> evidence) {
        try {
            Optional<McpCapability> capability = mcpCapabilityRegistry.find(capabilityName);
            if (capability.isEmpty()) {
                appendEvidence(evidence, "mcp:" + capabilityName + ":missing");
                return Optional.empty();
            }
            Object payload = capability.get().execute(input);
            if (payload == null) {
                appendEvidence(evidence, "mcp:" + capabilityName + ":empty");
                return Optional.empty();
            }
            String serialized = payload instanceof String value ? value : objectMapper.writeValueAsString(payload);
            if (!StringUtils.hasText(serialized)) {
                appendEvidence(evidence, "mcp:" + capabilityName + ":empty");
                return Optional.empty();
            }
            String status = resolvePayloadStatus(serialized);
            if (isUnavailableStatus(status)) {
                appendEvidence(evidence, "mcp:" + capabilityName + ":" + normalizeEvidenceStatus(status));
                return Optional.empty();
            }
            if (!hasUsefulPayload(serialized)) {
                appendEvidence(evidence, "mcp:" + capabilityName + ":empty");
                return Optional.empty();
            }
            appendEvidence(evidence, "mcp:" + capabilityName);
            return Optional.of(summarizeJsonPayload(serialized.trim(), capabilityName));
        } catch (Exception ex) {
            appendEvidence(evidence, "mcp:" + capabilityName + ":error");
            return Optional.empty();
        }
    }

    private void appendEvidence(List<String> evidence, String value) {
        if (evidence != null && StringUtils.hasText(value)) {
            evidence.add(value);
        }
    }

    private String resolvePayloadStatus(String payload) {
        if (!StringUtils.hasText(payload) || !payload.trim().startsWith("{")) {
            return "";
        }
        try {
            return objectMapper.readTree(payload).path("status").asText("");
        } catch (Exception ex) {
            return "";
        }
    }

    private boolean isUnavailableStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return false;
        }
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return "unavailable".equals(normalized)
                || "error".equals(normalized)
                || "failed".equals(normalized)
                || "fail".equals(normalized);
    }

    private String normalizeEvidenceStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "unknown";
        }
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "failed", "fail" -> "error";
            default -> normalized;
        };
    }

    private boolean hasUsefulPayload(String payload) {
        if (!StringUtils.hasText(payload)) {
            return false;
        }
        String trimmed = payload.trim();
        if (!trimmed.startsWith("{")) {
            return true;
        }
        try {
            JsonNode root = objectMapper.readTree(trimmed);
            JsonNode results = root.path("results");
            if (results.isArray()) {
                return !results.isEmpty();
            }
            JsonNode nodes = root.path("nodes");
            if (nodes.isArray()) {
                return !nodes.isEmpty();
            }
            if (StringUtils.hasText(root.path("summary").asText(""))) {
                return true;
            }
            if (StringUtils.hasText(root.path("routePath").asText(""))) {
                return true;
            }
            return root.hasNonNull("estimatedMinutes")
                    || root.hasNonNull("estimatedDistanceKm")
                    || StringUtils.hasText(root.path("transportMode").asText(""))
                    || StringUtils.hasText(root.path("from").asText(""))
                    || StringUtils.hasText(root.path("to").asText(""));
        } catch (Exception ex) {
            return true;
        }
    }

    private String extractNearbyKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question.trim();
        for (String marker : QUERY_HINT_MARKERS) {
            int index = normalized.indexOf(marker);
            if (index > 0) {
                String keyword = normalized.substring(0, index)
                        .replace("请问", "")
                        .replace("想知道", "")
                        .replace("我想问", "")
                        .replace("我想", "")
                        .replace("帮我", "")
                        .replace("麻烦", "")
                        .trim();
                if (StringUtils.hasText(keyword)) {
                    return keyword;
                }
            }
        }
        return null;
    }

    private String summarizeJsonPayload(String payload, String label) {
        if (!StringUtils.hasText(payload)) {
            return "暂无记录";
        }
        try {
            JsonNode root = objectMapper.readTree(payload);
            List<String> parts = new ArrayList<>();
            String name = firstText(root, "tool", "capability");
            if (!StringUtils.hasText(name)) {
                name = StringUtils.hasText(label) ? label : "context";
            }
            parts.add("来源=" + name);
            String status = text(root, "status");
            if (StringUtils.hasText(status)) {
                parts.add("状态=" + displayStatus(status));
            }

            JsonNode results = root.path("results");
            if (results.isArray()) {
                parts.add("结果=" + summarizeItems(results, 5));
            } else if (hasRouteEstimateFields(root)) {
                appendRootField(parts, "路线", joinRoute(root));
                appendRootField(parts, "耗时", text(root, "estimatedMinutes") + "分钟");
                appendRootField(parts, "距离", text(root, "estimatedDistanceKm") + "km");
                appendRootField(parts, "方式", text(root, "transportMode"));
            } else if (hasItinerarySnapshotFields(root)) {
                appendRootField(parts, "选中方案", text(root, "selectedOptionKey"));
                appendRootField(parts, "路线", text(root, "routePath"));
                appendRootField(parts, "原因", text(root, "recommendReason"));
                appendRootField(parts, "备注", text(root, "shareNote"));
                appendRootField(parts, "摘要", text(root, "summary"));
            } else if (root.has("summary")) {
                parts.add("摘要=" + trim(root.path("summary").asText("")));
            } else if (root.has("nodes")) {
                parts.add("节点=" + summarizeItems(root.path("nodes"), 5));
            }
            return limit(String.join(" | ", parts), maxContextChars());
        } catch (Exception ex) {
            return limit(payload, maxContextChars());
        }
    }

    private boolean hasRouteEstimateFields(JsonNode root) {
        return root != null && (root.hasNonNull("estimatedMinutes")
                || root.hasNonNull("estimatedDistanceKm")
                || StringUtils.hasText(root.path("transportMode").asText(""))
                || StringUtils.hasText(root.path("from").asText(""))
                || StringUtils.hasText(root.path("to").asText("")));
    }

    private boolean hasItinerarySnapshotFields(JsonNode root) {
        return root != null && (StringUtils.hasText(root.path("routePath").asText(""))
                || StringUtils.hasText(root.path("selectedOptionKey").asText(""))
                || StringUtils.hasText(root.path("recommendReason").asText(""))
                || StringUtils.hasText(root.path("shareNote").asText("")));
    }

    private String joinRoute(JsonNode root) {
        String from = text(root, "from");
        String to = text(root, "to");
        if (StringUtils.hasText(from) && StringUtils.hasText(to)) {
            return from + " -> " + to;
        }
        return "";
    }

    private void appendRootField(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value) && !"分钟".equals(value) && !"km".equals(value)) {
            parts.add(label + "=" + trim(value));
        }
    }

    private String summarizeTextPayload(String source, String text) {
        String prefix = StringUtils.hasText(source) ? source.trim() : "context";
        return limit(prefix + ": " + trim(text), maxContextChars());
    }

    private String summarizeItems(JsonNode items, int limit) {
        if (items == null || !items.isArray() || items.isEmpty()) {
            return "none";
        }
        List<String> parts = new ArrayList<>();
        int count = 0;
        for (JsonNode item : items) {
            if (item == null || item.isNull() || count >= limit) {
                continue;
            }
            StringBuilder builder = new StringBuilder();
            String name = firstText(item, "name", "title");
            if (StringUtils.hasText(name)) {
                builder.append(name.trim());
            }
            appendItemField(builder, "类别", text(item, "category"));
            appendItemField(builder, "地址", text(item, "address"));
            appendItemField(builder, "区域", text(item, "district"));
            appendItemField(builder, "城市", text(item, "cityName"));
            appendItemField(builder, "来源", text(item, "source"));
            String distance = text(item, "distanceMeters");
            if (StringUtils.hasText(distance)) {
                appendItemField(builder, "距离", distance + "m");
            }
            String mode = firstText(item, "travelMode", "transportMode");
            appendItemField(builder, "方式", mode);
            appendItemField(builder, "分享", text(item, "shareNote"));
            appendItemField(builder, "路线", text(item, "routeSummary"));
            appendItemField(builder, "主题", arrayText(item, "themes"));
            appendItemField(builder, "人均", text(item, "avgCost"));
            appendItemField(builder, "营业", text(item, "openingHours"));
            String value = trim(builder.toString());
            if (StringUtils.hasText(value)) {
                parts.add(value);
                count++;
            }
        }
        return parts.isEmpty() ? "none" : String.join("; ", parts);
    }

    private void appendItemField(StringBuilder builder, String label, String value) {
        if (!StringUtils.hasText(value)) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("，");
        }
        builder.append(label).append("=").append(trim(value));
    }

    private String text(JsonNode node, String field) {
        return node == null ? "" : node.path(field).asText("");
    }

    private String firstText(JsonNode node, String... fields) {
        if (node == null || fields == null) {
            return "";
        }
        for (String field : fields) {
            String value = text(node, field);
            if (StringUtils.hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private String arrayText(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.path(field);
        if (!value.isArray() || value.isEmpty()) {
            return "";
        }
        List<String> items = new ArrayList<>();
        for (JsonNode item : value) {
            String text = item == null ? "" : item.asText("");
            if (StringUtils.hasText(text)) {
                items.add(text.trim());
            }
        }
        return items.isEmpty() ? "" : String.join("/", items);
    }

    private String displayStatus(String status) {
        if (!StringUtils.hasText(status)) {
            return "未知";
        }
        String normalized = status.trim().toLowerCase(java.util.Locale.ROOT);
        return switch (normalized) {
            case "ok", "success" -> "可用";
            case "unavailable" -> "暂不可用";
            case "error", "failed", "fail" -> "异常";
            default -> status.trim();
        };
    }

    private boolean containsAny(String value, List<String> markers) {
        if (!StringUtils.hasText(value) || markers == null || markers.isEmpty()) {
            return false;
        }
        for (String marker : markers) {
            if (value.contains(marker)) {
                return true;
            }
        }
        return false;
    }

    private String trim(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.replaceAll("[\\r\\n]+", " ").trim();
    }

    private Long resolveItineraryId(ChatReqDTO req) {
        return req == null
                || req.getContext() == null
                || req.getContext().getItinerary() == null
                ? null
                : req.getContext().getItinerary().getItineraryId();
    }

    private Long resolveUserId(ChatReqDTO req) {
        return req == null || req.getContext() == null ? null : req.getContext().getCurrentUserId();
    }

    private String resolveCityName(ChatReqDTO req) {
        String cityName = req == null || req.getContext() == null ? null : req.getContext().getCityName();
        return StringUtils.hasText(cityName) ? cityName.trim() : "";
    }

    private String inferNearbyCategory(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        if (question.contains("酒店") || question.contains("住")) {
            return "酒店";
        }
        if (question.contains("吃") || question.contains("餐") || question.contains("咖啡")) {
            return "餐饮";
        }
        if (question.contains("商场") || question.contains("购物") || question.contains("逛街")) {
            return "购物";
        }
        if (question.contains("景点") || question.contains("好玩") || question.contains("拍照")) {
            return "景点";
        }
        return null;
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private String limit(String value, int maxChars) {
        if (!StringUtils.hasText(value) || value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, maxChars);
    }

    private int maxRagDocuments() {
        return Math.max(1, augmentationProperties.getMaxRagDocuments());
    }

    private int maxToolPayloads() {
        return Math.max(1, augmentationProperties.getMaxToolPayloads());
    }

    private int maxMcpEvidence() {
        return Math.max(1, augmentationProperties.getMaxMcpEvidence());
    }

    private int maxContextChars() {
        return Math.max(120, augmentationProperties.getMaxContextChars());
    }
}

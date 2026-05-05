package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.RouteNodeDecorationVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.SmartFillVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RealLlmGatewayService {
    private final OpenAiGatewayClient openAiGatewayClient;
    private final LlmProperties llmProperties;
    private final SafePromptBuilder safePromptBuilder;
    private final ObjectMapper objectMapper;

    public RealLlmGatewayService(OpenAiGatewayClient openAiGatewayClient,
                                 LlmProperties llmProperties,
                                 SafePromptBuilder safePromptBuilder,
                                 ObjectMapper objectMapper) {
        this.openAiGatewayClient = openAiGatewayClient;
        this.llmProperties = llmProperties;
        this.safePromptBuilder = safePromptBuilder;
        this.objectMapper = objectMapper;
    }

    public String generateRouteWarmTip(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return callText(safePromptBuilder.buildGenerateRouteWarmTipPrompt(userReq, nodes));
    }

    public String explainOptionRecommendation(GenerateReqDTO userReq, ItineraryOptionVO option) {
        return callText(safePromptBuilder.buildExplainOptionRecommendationPrompt(userReq, option));
    }

    public RouteCriticDecisionVO criticSelectItineraryOption(GenerateReqDTO userReq, List<ItineraryOptionVO> options) {
        String raw = callText(
                safePromptBuilder.buildRouteCriticPrompt(userReq, options),
                safePromptBuilder.buildSmartFillSystemPrompt()
        );
        return parseRouteCriticDecisionResponse(raw, options);
    }

    public String explainPoiChoice(GenerateReqDTO userReq, ItineraryNodeVO node) {
        return callText(safePromptBuilder.buildExplainPoiChoicePrompt(userReq, node));
    }

    public List<String> generateChatFollowUpTips(String question, String cityName) {
        String raw = callText(
                safePromptBuilder.buildChatFollowUpTipsPrompt(question, cityName),
                safePromptBuilder.buildChatSystemPrompt()
        );
        if (!StringUtils.hasText(raw)) {
            return List.of();
        }
        return raw.lines()
                .map(this::normalizeTipLine)
                .filter(StringUtils::hasText)
                .limit(3)
                .toList();
    }

    public SmartFillVO parseSmartFill(String text, List<String> poiNameHints) {
        String raw = callText(
                safePromptBuilder.buildSmartFillPrompt(text, poiNameHints),
                safePromptBuilder.buildSmartFillSystemPrompt()
        );
        return parseSmartFillResponse(raw);
    }

    public DepartureLegEstimateVO estimateDepartureLeg(GenerateReqDTO userReq, ItineraryNodeVO firstNode) {
        String raw = callText(
                safePromptBuilder.buildDepartureLegEstimatePrompt(userReq, firstNode),
                safePromptBuilder.buildSmartFillSystemPrompt()
        );
        return parseDepartureLegEstimateResponse(raw);
    }

    public SegmentTransportAnalysisVO analyzeSegmentTransport(GenerateReqDTO userReq, ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        String raw = callText(
                safePromptBuilder.buildSegmentTransportAnalysisPrompt(userReq, fromNode, toNode),
                safePromptBuilder.buildSmartFillSystemPrompt()
        );
        return parseSegmentTransportAnalysisResponse(raw);
    }

    public ItineraryRouteDecorationVO decorateRouteExperience(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        String raw = callText(
                safePromptBuilder.buildRouteExperienceDecorationPrompt(userReq, nodes),
                safePromptBuilder.buildSmartFillSystemPrompt()
        );
        return parseRouteExperienceDecorationResponse(raw);
    }

    private String callText(String userPrompt) {
        return callText(userPrompt, safePromptBuilder.buildItinerarySystemPrompt());
    }

    private String callText(String userPrompt, String systemPrompt) {
        if (!llmProperties.canTryRealText()) {
            throw new IllegalStateException("OpenAI real text model is not configured");
        }
        if (openAiGatewayClient == null) {
            throw new IllegalStateException("OpenAI gateway is not configured");
        }

        LlmProperties.ResolvedOpenAiOptions textOptions = llmProperties.getOpenai().resolveTextOptions();
        return openAiGatewayClient.request(
                textOptions,
                llmProperties.getOpenai().getApiKey(),
                List.of(
                        new OpenAiGatewayClient.OpenAiMessage("system", systemPrompt),
                        new OpenAiGatewayClient.OpenAiMessage("user", userPrompt)
                )
        );
    }

    private SmartFillVO parseSmartFillResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("智能填写解析失败：模型返回为空");
        }
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("智能填写解析失败：未找到 JSON 结构");
        }
        try {
            SmartFillVO parsed = objectMapper.readValue(json, SmartFillVO.class);
            if (parsed.getThemes() == null) {
                parsed.setThemes(List.of());
            }
            if (parsed.getMustVisitPoiNames() == null) {
                parsed.setMustVisitPoiNames(List.of());
            }
            if (parsed.getSummary() == null) {
                parsed.setSummary(List.of());
            }
            return parsed;
        } catch (Exception ex) {
            throw new IllegalStateException("智能填写解析失败：模型输出不是可解析 JSON", ex);
        }
    }

    private DepartureLegEstimateVO parseDepartureLegEstimateResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("首段通勤估算失败：模型返回为空");
        }
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("首段通勤估算失败：未提取到 JSON 结构");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            DepartureLegEstimateVO estimate = new DepartureLegEstimateVO();

            String transportMode = root.path("transportMode").asText(null);
            if (StringUtils.hasText(transportMode)) {
                estimate.setTransportMode(transportMode.trim());
            }

            if (root.hasNonNull("estimatedMinutes")) {
                int minutes = root.path("estimatedMinutes").asInt();
                if (minutes > 0 && minutes <= 240) {
                    estimate.setEstimatedMinutes(minutes);
                }
            }

            if (root.hasNonNull("estimatedDistanceKm")) {
                BigDecimal distance = root.path("estimatedDistanceKm").decimalValue();
                if (distance != null
                        && distance.compareTo(BigDecimal.valueOf(0.1D)) >= 0
                        && distance.compareTo(BigDecimal.valueOf(80D)) <= 0) {
                    estimate.setEstimatedDistanceKm(distance.setScale(1, RoundingMode.HALF_UP));
                }
            }
            return estimate;
        } catch (Exception ex) {
            throw new IllegalStateException("首段通勤估算失败：模型输出不是可解析 JSON", ex);
        }
    }

    SegmentTransportAnalysisVO parseSegmentTransportAnalysisResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("出行段交通分析失败：模型返回为空");
        }
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("出行段交通分析失败：未提取到 JSON 结构");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            SegmentTransportAnalysisVO analysis = new SegmentTransportAnalysisVO();
            String transportMode = root.path("transportMode").asText(null);
            if (StringUtils.hasText(transportMode)) {
                analysis.setTransportMode(transportMode.trim());
            }
            String narrative = root.path("narrative").asText(null);
            if (StringUtils.hasText(narrative)) {
                analysis.setNarrative(narrative.replaceAll("[\\r\\n]+", " ").trim());
            }
            return analysis;
        } catch (Exception ex) {
            throw new IllegalStateException("出行段交通分析失败：模型输出不是可解析 JSON", ex);
        }
    }

    ItineraryRouteDecorationVO parseRouteExperienceDecorationResponse(String raw) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("route experience decoration failed: empty model response");
        }
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("route experience decoration failed: missing json payload");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            ItineraryRouteDecorationVO decoration = new ItineraryRouteDecorationVO();
            String routeWarmTip = root.path("routeWarmTip").asText(null);
            if (StringUtils.hasText(routeWarmTip)) {
                decoration.setRouteWarmTip(routeWarmTip.replaceAll("[\\r\\n]+", " ").trim());
            }

            List<RouteNodeDecorationVO> decoratedNodes = new ArrayList<>();
            JsonNode nodeArray = root.path("nodes");
            if (nodeArray.isArray()) {
                for (JsonNode node : nodeArray) {
                    if (node == null || node.isNull() || node.isMissingNode()) {
                        continue;
                    }
                    RouteNodeDecorationVO item = new RouteNodeDecorationVO();
                    if (node.hasNonNull("index")) {
                        item.setIndex(node.path("index").asInt());
                    }
                    String transportMode = node.path("transportMode").asText(null);
                    if (StringUtils.hasText(transportMode)) {
                        item.setTransportMode(transportMode.trim());
                    }
                    String narrative = node.path("narrative").asText(null);
                    if (StringUtils.hasText(narrative)) {
                        item.setNarrative(narrative.replaceAll("[\\r\\n]+", " ").trim());
                    }
                    decoratedNodes.add(item);
                }
            }
            decoration.setNodes(decoratedNodes);
            return decoration;
        } catch (Exception ex) {
            throw new IllegalStateException("route experience decoration failed: model output is not valid json", ex);
        }
    }

    private RouteCriticDecisionVO parseRouteCriticDecisionResponse(String raw, List<ItineraryOptionVO> options) {
        if (!StringUtils.hasText(raw)) {
            throw new IllegalStateException("route critic failed: model returned empty response");
        }
        String json = extractJsonObject(raw);
        if (!StringUtils.hasText(json)) {
            throw new IllegalStateException("route critic failed: response has no JSON object");
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            RouteCriticDecisionVO decision = new RouteCriticDecisionVO();
            String selectedOptionKey = root.path("selectedOptionKey").asText(null);
            if (!StringUtils.hasText(selectedOptionKey) || !containsOptionKey(options, selectedOptionKey.trim())) {
                throw new IllegalStateException("route critic selected unknown option: " + selectedOptionKey);
            }
            decision.setSelectedOptionKey(selectedOptionKey.trim());
            String reason = root.path("reason").asText(null);
            if (StringUtils.hasText(reason)) {
                decision.setReason(normalizeShortText(reason, 160));
            }
            decision.setRejectedReasons(parseStringMap(root.path("rejectedReasons"), options));
            decision.setOptionScores(parseScoreMap(root.path("optionScores"), options));
            return decision;
        } catch (IllegalStateException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("route critic failed: model output is not valid json", ex);
        }
    }

    private boolean containsOptionKey(List<ItineraryOptionVO> options, String optionKey) {
        if (!StringUtils.hasText(optionKey) || options == null) {
            return false;
        }
        return options.stream()
                .anyMatch(option -> option != null && optionKey.equals(option.getOptionKey()));
    }

    private Map<String, String> parseStringMap(JsonNode node, List<ItineraryOptionVO> options) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Set<String> allowedKeys = optionKeys(options);
        Map<String, String> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!allowedKeys.contains(key)) {
                return;
            }
            String value = entry.getValue() == null ? null : entry.getValue().asText(null);
            if (StringUtils.hasText(value)) {
                result.put(key, normalizeShortText(value, 120));
            }
        });
        return result;
    }

    private Map<String, Double> parseScoreMap(JsonNode node, List<ItineraryOptionVO> options) {
        if (node == null || !node.isObject()) {
            return Map.of();
        }
        Set<String> allowedKeys = optionKeys(options);
        Map<String, Double> result = new LinkedHashMap<>();
        node.fields().forEachRemaining(entry -> {
            String key = entry.getKey();
            if (!allowedKeys.contains(key) || entry.getValue() == null || !entry.getValue().isNumber()) {
                return;
            }
            double score = entry.getValue().asDouble();
            result.put(key, Math.max(0D, Math.min(100D, score)));
        });
        return result;
    }

    private Set<String> optionKeys(List<ItineraryOptionVO> options) {
        if (options == null) {
            return Set.of();
        }
        return options.stream()
                .filter(option -> option != null && StringUtils.hasText(option.getOptionKey()))
                .map(ItineraryOptionVO::getOptionKey)
                .collect(Collectors.toSet());
    }

    private String normalizeShortText(String raw, int maxChars) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String normalized = raw.replaceAll("[\\r\\n]+", " ").trim();
        return normalized.length() <= maxChars ? normalized : normalized.substring(0, maxChars);
    }

    private String extractJsonObject(String raw) {
        String trimmed = raw.trim();
        if (trimmed.startsWith("```")) {
            int firstLineBreak = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstLineBreak >= 0 && lastFence > firstLineBreak) {
                trimmed = trimmed.substring(firstLineBreak + 1, lastFence).trim();
            }
        }
        int left = trimmed.indexOf('{');
        int right = trimmed.lastIndexOf('}');
        if (left < 0 || right <= left) {
            return null;
        }
        return trimmed.substring(left, right + 1);
    }

    private String normalizeTipLine(String line) {
        if (!StringUtils.hasText(line)) {
            return null;
        }
        String normalized = line.trim();
        normalized = normalized.replaceFirst("^[\\-\\d\\s\\.,、：:）\\)]+", "").trim();
        return normalized;
    }
}

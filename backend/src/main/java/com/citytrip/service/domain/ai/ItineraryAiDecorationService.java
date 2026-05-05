package com.citytrip.service.domain.ai;

import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.RouteNodeDecorationVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.LlmService;
import com.citytrip.service.domain.planning.NodeNearbyEnrichmentService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;

@Service
public class ItineraryAiDecorationService {
    private static final Logger log = LoggerFactory.getLogger(ItineraryAiDecorationService.class);
    private static final String RECOMMENDATION_SOURCE_LLM = "llm";
    private static final long DEPARTURE_LEG_TIMEOUT_CAP_MS = 500L;
    private static final long DECORATION_BUDGET_RESERVE_MS = 160L;

    private final LlmService llmService;
    private final ObjectMapper objectMapper;
    private final ItineraryComparisonAssembler itineraryComparisonAssembler;
    private final NodeNearbyEnrichmentService nodeNearbyEnrichmentService;
    private final AsyncTaskExecutor itineraryAiCallExecutor;
    private final long aiTimeoutMs;
    @Autowired(required = false)
    private GeoSearchService geoSearchService;

    @Autowired
    public ItineraryAiDecorationService(LlmService llmService,
                                        ObjectMapper objectMapper,
                                        ItineraryComparisonAssembler itineraryComparisonAssembler,
                                        NodeNearbyEnrichmentService nodeNearbyEnrichmentService,
                                        @Qualifier("itineraryAiCallExecutor") AsyncTaskExecutor itineraryAiCallExecutor,
                                        @Value("${app.planning.ai-timeout-ms:25000}") long aiTimeoutMs) {
        this.llmService = llmService;
        this.objectMapper = objectMapper;
        this.itineraryComparisonAssembler = itineraryComparisonAssembler;
        this.nodeNearbyEnrichmentService = nodeNearbyEnrichmentService;
        this.itineraryAiCallExecutor = itineraryAiCallExecutor;
        this.aiTimeoutMs = Math.max(aiTimeoutMs, 1L);
    }

    public ItineraryAiDecorationService(LlmService llmService,
                                        ObjectMapper objectMapper,
                                        ItineraryComparisonAssembler itineraryComparisonAssembler,
                                        @Qualifier("itineraryAiCallExecutor") AsyncTaskExecutor itineraryAiCallExecutor,
                                        long aiTimeoutMs) {
        this(
                llmService,
                objectMapper,
                itineraryComparisonAssembler,
                null,
                itineraryAiCallExecutor,
                aiTimeoutMs
        );
    }

    public ItineraryVO decorateWithLlm(ItineraryVO baseItinerary, GenerateReqDTO req) {
        ItineraryVO itinerary = copyItinerary(baseItinerary);
        if (itinerary == null) {
            return null;
        }
        AiBudget budget = new AiBudget(resolveEffectiveAiBudgetMs());
        Map<String, DepartureLegEstimateVO> departureLegCache = new HashMap<>();
        try {
            boolean criticApplied = applyRouteCriticDecision(req, itinerary, budget);
            if (!criticApplied) {
                decorateOptionRecommendations(req, itinerary, budget);
            }
            decorateDepartureLeg(req, itinerary.getNodes(), departureLegCache, budget);
            ItineraryRouteDecorationVO routeDecoration = resolveRouteExperienceDecoration(req, itinerary.getNodes(), budget);
            if (hasUsefulRouteDecoration(routeDecoration)) {
                applyRouteExperienceDecoration(itinerary, req, routeDecoration);
            } else {
                Map<String, String> nodeReasonCache = new HashMap<>();
                Map<String, SegmentTransportAnalysisVO> segmentTransportCache = new HashMap<>();
                decorateNodes(req, itinerary.getNodes(), nodeReasonCache, segmentTransportCache, budget);
                if (!StringUtils.hasText(itinerary.getTips())) {
                    applyWarmTips(itinerary, req, budget);
                }
            }
        } catch (RuntimeException ex) {
            log.warn("AI decoration fallback to partially decorated itinerary, reason={}", summarizeExecutionFailure(ex));
        }
        if (!StringUtils.hasText(itinerary.getTips())) {
            applyWarmTips(itinerary, req, budget);
        }
        applyNearbyEnrichment(itinerary, req);
        return itinerary;
    }

    public void applyWarmTips(ItineraryVO itinerary, GenerateReqDTO req) {
        applyWarmTips(itinerary, req, new AiBudget(aiTimeoutMs));
        applyNearbyEnrichment(itinerary, req);
    }

    private void applyWarmTips(ItineraryVO itinerary, GenerateReqDTO req, AiBudget budget) {
        if (itinerary == null) {
            return;
        }

        String routeWarmTip = resolveTextWithFallback(
                () -> llmService.generateRouteWarmTip(req, itinerary.getNodes()),
                "generateRouteWarmTip",
                budget
        );
        String fallback = buildFallbackRouteWarmTip(req, itinerary.getNodes());
        itinerary.setTips(normalizeSingleTip(keepChineseOrFallback(routeWarmTip, fallback), fallback));
    }

    private boolean applyRouteCriticDecision(GenerateReqDTO req, ItineraryVO itinerary, AiBudget budget) {
        if (itinerary == null || itinerary.getOptions() == null || itinerary.getOptions().size() < 2) {
            return false;
        }
        RouteCriticDecisionVO decision = resolveValueWithFallback(
                () -> llmService.criticSelectItineraryOption(req, itinerary.getOptions()),
                "criticSelectItineraryOption",
                budget
        );
        if (decision == null || !StringUtils.hasText(decision.getSelectedOptionKey())) {
            return false;
        }
        ItineraryOptionVO selected = findSelectedOption(itinerary.getOptions(), decision.getSelectedOptionKey());
        if (selected == null || !Objects.equals(selected.getOptionKey(), decision.getSelectedOptionKey())) {
            return false;
        }

        itinerary.setSelectedOptionKey(selected.getOptionKey());
        applySelectedOption(itinerary, selected);

        String reason = normalizeRecommendationReason(decision.getReason(), selected.getRecommendReason());
        if (StringUtils.hasText(reason)) {
            selected.setRecommendReason(reason);
            markRecommendationAsAi(selected);
            itinerary.setRecommendReason(reason);
            markRecommendationAsAi(itinerary);
            itinerary.setCriticReason(reason);
        }
        applyCriticScores(itinerary.getOptions(), decision.getOptionScores());

        Map<String, String> rejectedReasons = normalizeRejectedReasons(decision.getRejectedReasons());
        rejectedReasons.remove(selected.getOptionKey());
        if (!rejectedReasons.isEmpty()) {
            itinerary.setRejectedOptionReasons(rejectedReasons);
            for (ItineraryOptionVO option : itinerary.getOptions()) {
                if (option == null || !StringUtils.hasText(option.getOptionKey())) {
                    continue;
                }
                String rejectedReason = rejectedReasons.get(option.getOptionKey());
                if (StringUtils.hasText(rejectedReason)) {
                    option.setNotRecommendReason(rejectedReason);
                }
            }
        }

        // tips only stores the route-level warm tip. Do not put option-comparison template copy here.
        // Keep critic selection/recommendation, then generateRouteWarmTip will create the actual <=40-char route tip.
        return true;
    }

    private void applyCriticScores(List<ItineraryOptionVO> options, Map<String, Double> optionScores) {
        if (options == null || options.isEmpty() || optionScores == null || optionScores.isEmpty()) {
            return;
        }
        for (ItineraryOptionVO option : options) {
            if (option == null || !StringUtils.hasText(option.getOptionKey())) {
                continue;
            }
            Double rawScore = optionScores.get(option.getOptionKey());
            if (rawScore == null || rawScore.isNaN() || rawScore.isInfinite()) {
                continue;
            }
            option.setCriticScore(Math.max(0D, Math.min(100D, rawScore)));
        }
    }

    private void applySelectedOption(ItineraryVO itinerary, ItineraryOptionVO option) {
        if (itinerary == null || option == null) {
            return;
        }
        itinerary.setNodes(option.getNodes());
        itinerary.setTotalCost(option.getTotalCost());
        itinerary.setTotalDuration(option.getTotalDuration());
        itinerary.setRecommendReason(option.getRecommendReason());
        itinerary.setRecommendationSource(option.getRecommendationSource());
        itinerary.setAiDecorated(option.getAiDecorated());
        itinerary.setAlerts(option.getAlerts());
    }

    private Map<String, String> normalizeRejectedReasons(Map<String, String> rawReasons) {
        if (rawReasons == null || rawReasons.isEmpty()) {
            return Map.of();
        }
        Map<String, String> normalized = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : rawReasons.entrySet()) {
            if (!StringUtils.hasText(entry.getKey()) || !StringUtils.hasText(entry.getValue())) {
                continue;
            }
            String reason = entry.getValue().replaceAll("[\\r\\n]+", " ").trim();
            if (reason.length() > 120) {
                reason = reason.substring(0, 120);
            }
            normalized.put(entry.getKey().trim(), reason);
        }
        return normalized;
    }

    private void decorateOptionRecommendations(GenerateReqDTO req, ItineraryVO itinerary, AiBudget budget) {
        if (itinerary == null || itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            return;
        }

        List<ItineraryOptionVO> orderedOptions = orderOptionsForRecommendation(itinerary.getOptions(), itinerary.getSelectedOptionKey());
        for (ItineraryOptionVO option : orderedOptions) {
            if (option == null) {
                continue;
            }
            String ruleFallback = option.getRecommendReason();
            String narrativeFallback = buildFallbackOptionRecommendation(req, option);
            String generated = resolveTextWithFallback(
                    () -> llmService.explainOptionRecommendation(req, option),
                    "explainOptionRecommendation",
                    budget
            );
            String resolved = resolveOptionRecommendationReason(generated, ruleFallback, narrativeFallback);
            if (StringUtils.hasText(resolved)) {
                option.setRecommendReason(resolved);
                if (StringUtils.hasText(generated) && !isEnglishDominant(generated)) {
                    markRecommendationAsAi(option);
                }
            }
        }

        ItineraryOptionVO selected = findSelectedOption(itinerary.getOptions(), itinerary.getSelectedOptionKey());
        if (selected != null && StringUtils.hasText(selected.getRecommendReason())) {
            itinerary.setRecommendReason(selected.getRecommendReason().trim());
            itinerary.setRecommendationSource(selected.getRecommendationSource());
            itinerary.setAiDecorated(selected.getAiDecorated());
        }
    }

    private void markRecommendationAsAi(ItineraryVO itinerary) {
        if (itinerary == null) {
            return;
        }
        itinerary.setRecommendationSource(RECOMMENDATION_SOURCE_LLM);
        itinerary.setAiDecorated(true);
    }

    private void markRecommendationAsAi(ItineraryOptionVO option) {
        if (option == null) {
            return;
        }
        option.setRecommendationSource(RECOMMENDATION_SOURCE_LLM);
        option.setAiDecorated(true);
    }

    private List<ItineraryOptionVO> orderOptionsForRecommendation(List<ItineraryOptionVO> options, String selectedOptionKey) {
        if (options == null || options.isEmpty()) {
            return List.of();
        }
        List<ItineraryOptionVO> ordered = new ArrayList<>();
        ItineraryOptionVO selected = findSelectedOption(options, selectedOptionKey);
        if (selected != null) {
            ordered.add(selected);
        }
        for (ItineraryOptionVO option : options) {
            if (option != null && ordered.stream().noneMatch(existing -> existing == option)) {
                ordered.add(option);
            }
        }
        return ordered;
    }

    private ItineraryOptionVO findSelectedOption(List<ItineraryOptionVO> options, String selectedOptionKey) {
        if (options == null || options.isEmpty()) {
            return null;
        }
        if (StringUtils.hasText(selectedOptionKey)) {
            for (ItineraryOptionVO option : options) {
                if (option != null && Objects.equals(option.getOptionKey(), selectedOptionKey)) {
                    return option;
                }
            }
        }
        return options.stream().filter(Objects::nonNull).findFirst().orElse(null);
    }

    private String normalizeRecommendationReason(String candidate, String fallback) {
        String value = StringUtils.hasText(candidate) ? candidate.trim() : fallback;
        if (!StringUtils.hasText(value)) {
            return null;
        }
        value = value.replaceAll("[\\r\\n]+", " ").trim();
        if (value.length() > 160) {
            value = value.substring(0, 160);
        }
        return value;
    }

    private String resolveOptionRecommendationReason(String generated, String ruleFallback, String narrativeFallback) {
        if (StringUtils.hasText(generated)) {
            if (isEnglishDominant(generated)) {
                return normalizeRecommendationReason(ruleFallback, ruleFallback);
            }
            return normalizeRecommendationReason(generated, ruleFallback);
        }
        String fallback = StringUtils.hasText(narrativeFallback) ? narrativeFallback : ruleFallback;
        return normalizeRecommendationReason(fallback, ruleFallback);
    }

    private String buildFallbackOptionRecommendation(GenerateReqDTO req, ItineraryOptionVO option) {
        if (option == null) {
            return null;
        }
        List<ItineraryNodeVO> nodes = option.getNodes();
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }

        List<String> poiNames = new ArrayList<>();
        String first = safePoiName(nodes.get(0));
        if (StringUtils.hasText(first)) {
            poiNames.add(first);
        }
        if (nodes.size() >= 3) {
            String middle = safePoiName(nodes.get(nodes.size() / 2));
            if (StringUtils.hasText(middle) && poiNames.stream().noneMatch(existing -> existing.equals(middle))) {
                poiNames.add(middle);
            }
        } else if (nodes.size() == 2) {
            String second = safePoiName(nodes.get(1));
            if (StringUtils.hasText(second) && poiNames.stream().noneMatch(existing -> existing.equals(second))) {
                poiNames.add(second);
            }
        }
        String last = safePoiName(nodes.get(nodes.size() - 1));
        if (StringUtils.hasText(last) && poiNames.stream().noneMatch(existing -> existing.equals(last))) {
            poiNames.add(last);
        }
        poiNames = poiNames.stream().filter(StringUtils::hasText).limit(3).toList();
        if (poiNames.isEmpty()) {
            return null;
        }

        boolean hasExternal = nodes.stream().anyMatch(node -> node != null && "external".equalsIgnoreCase(node.getSourceType()));
        boolean rainy = Boolean.TRUE.equals(req == null ? null : req.getIsRainy());
        boolean night = Boolean.TRUE.equals(req == null ? null : req.getIsNight());

        StringBuilder sb = new StringBuilder();
        if (poiNames.size() >= 2) {
            sb.append("从").append(poiNames.get(0)).append("出发，顺路串联");
            sb.append(String.join("、", poiNames.subList(1, poiNames.size())));
            sb.append("，节奏更连贯。");
        } else {
            sb.append("以").append(poiNames.get(0)).append("为主线，按时间窗顺走更省折返。");
        }

        if (rainy) {
            sb.append("雨天尽量把室外停留分散在短段。");
        } else if (night) {
            sb.append("夜游建议把热闹点位放后段，回程更好衔接。");
        }
        if (hasExternal) {
            sb.append("含地图候选点位，出发前请再确认营业状态。");
        }

        String result = sb.toString().replaceAll("[\\r\\n]+", " ").trim();
        if (result.length() > 160) {
            result = result.substring(0, 160);
        }
        return result;
    }

    private String safePoiName(ItineraryNodeVO node) {
        if (node == null) {
            return null;
        }
        String name = node.getPoiName();
        if (!StringUtils.hasText(name)) {
            return null;
        }
        name = name.trim();
        if (name.length() > 18) {
            name = name.substring(0, 18);
        }
        return name;
    }

    private ItineraryRouteDecorationVO resolveRouteExperienceDecoration(GenerateReqDTO req,
                                                                        List<ItineraryNodeVO> nodes,
                                                                        AiBudget budget) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        ItineraryRouteDecorationVO generated = resolveValueWithFallback(
                () -> llmService.decorateRouteExperience(req, nodes),
                "decorateRouteExperience",
                budget
        );
        return hasUsefulRouteDecoration(generated) ? generated : null;
    }

    private boolean hasUsefulRouteDecoration(ItineraryRouteDecorationVO decoration) {
        if (decoration == null) {
            return false;
        }
        if (StringUtils.hasText(decoration.getRouteWarmTip())) {
            return true;
        }
        if (decoration.getNodes() == null || decoration.getNodes().isEmpty()) {
            return false;
        }
        return decoration.getNodes().stream().anyMatch(item ->
                item != null
                        && (StringUtils.hasText(item.getTransportMode())
                        || StringUtils.hasText(item.getNarrative())));
    }

    private void applyRouteExperienceDecoration(ItineraryVO itinerary,
                                                GenerateReqDTO req,
                                                ItineraryRouteDecorationVO decoration) {
        if (itinerary == null || decoration == null || itinerary.getNodes() == null || itinerary.getNodes().isEmpty()) {
            return;
        }
        if (StringUtils.hasText(decoration.getRouteWarmTip())) {
            itinerary.setTips(decoration.getRouteWarmTip().replaceAll("[\\r\\n]+", " ").trim());
        }
        if (decoration.getNodes() == null || decoration.getNodes().isEmpty()) {
            return;
        }

        for (RouteNodeDecorationVO item : decoration.getNodes()) {
            if (item == null || item.getIndex() == null) {
                continue;
            }
            int index = item.getIndex();
            if (index < 0 || index >= itinerary.getNodes().size()) {
                continue;
            }
            ItineraryNodeVO node = itinerary.getNodes().get(index);
            if (node == null) {
                continue;
            }

            ItineraryNodeVO fromNode = index == 0 ? null : itinerary.getNodes().get(index - 1);
            String factualMode = resolveExistingSegmentTransportMode(fromNode, node);
            String normalizedMode = normalizeTransportMode(item.getTransportMode(), factualMode, fromNode, node);
            String fallbackNarrative = buildFallbackSegmentNarrative(req, fromNode, node, normalizedMode);
            SegmentTransportAnalysisVO analysis = new SegmentTransportAnalysisVO();
            analysis.setTransportMode(normalizedMode);
            analysis.setNarrative(normalizeNarrative(item.getNarrative(), fallbackNarrative));
            applySegmentTransportAnalysis(node, analysis, index == 0);
        }
    }

    private void decorateNodes(GenerateReqDTO req,
                               List<ItineraryNodeVO> nodes,
                               Map<String, String> nodeReasonCache,
                               Map<String, SegmentTransportAnalysisVO> segmentTransportCache,
                               AiBudget budget) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        ItineraryNodeVO prevNode = null;
        for (ItineraryNodeVO node : nodes) {
            if (node == null) {
                continue;
            }
            String nodeKey = buildNodeKey(node);
            String nodeReason = nodeReasonCache.computeIfAbsent(nodeKey, key -> {
                String reason = resolveTextWithFallback(
                        () -> llmService.explainPoiChoice(req, node),
                        "explainPoiChoice",
                        budget
                );
                reason = keepChineseOrFallback(reason, node.getSysReason());
                return StringUtils.hasText(reason) ? reason.trim() : null;
            });
            if (StringUtils.hasText(nodeReason)) {
                node.setSysReason(nodeReason);
            }

            ItineraryNodeVO fromNode = prevNode;
            String segmentKey = buildSegmentKey(req, fromNode, node);
            SegmentTransportAnalysisVO segmentAnalysis = segmentTransportCache.computeIfAbsent(
                    segmentKey,
                    key -> resolveSegmentTransportAnalysis(req, fromNode, node, budget)
            );
            applySegmentTransportAnalysis(node, segmentAnalysis, fromNode == null);
            prevNode = node;
        }
    }

    private void decorateDepartureLeg(GenerateReqDTO req,
                                      List<ItineraryNodeVO> nodes,
                                      Map<String, DepartureLegEstimateVO> departureLegCache,
                                      AiBudget budget) {
        if (nodes == null || nodes.isEmpty() || !hasDepartureCoordinate(req)) {
            return;
        }
        ItineraryNodeVO firstNode = nodes.get(0);
        if (firstNode == null) {
            return;
        }

        String cacheKey = buildDepartureLegKey(req, firstNode);
        DepartureLegEstimateVO estimate;
        if (departureLegCache.containsKey(cacheKey)) {
            estimate = departureLegCache.get(cacheKey);
        } else {
            estimate = resolveDepartureLegEstimate(req, firstNode, budget);
            departureLegCache.put(cacheKey, estimate);
        }
        applyDepartureEstimate(firstNode, estimate);
    }

    private DepartureLegEstimateVO resolveDepartureLegEstimate(GenerateReqDTO req,
                                                               ItineraryNodeVO firstNode,
                                                               AiBudget budget) {
        DepartureLegEstimateVO geoEstimate = resolveDepartureLegEstimateByGeo(req, firstNode);
        if (hasAnyDepartureEstimate(geoEstimate)) {
            return geoEstimate;
        }

        DepartureLegEstimateVO llmEstimate = resolveValueWithFallback(
                () -> llmService.estimateDepartureLeg(req, firstNode),
                "estimateDepartureLeg",
                budget,
                DEPARTURE_LEG_TIMEOUT_CAP_MS
        );
        if (hasAnyDepartureEstimate(llmEstimate)) {
            return llmEstimate;
        }
        return buildDepartureFallback(req, firstNode);
    }

    private void applyDepartureEstimate(ItineraryNodeVO firstNode, DepartureLegEstimateVO estimate) {
        if (firstNode == null || !hasAnyDepartureEstimate(estimate)) {
            return;
        }
        if (estimate.getEstimatedMinutes() != null && estimate.getEstimatedMinutes() > 0) {
            firstNode.setDepartureTravelTime(estimate.getEstimatedMinutes());
        }
        if (estimate.getEstimatedDistanceKm() != null) {
            firstNode.setDepartureDistanceKm(estimate.getEstimatedDistanceKm().setScale(1, RoundingMode.HALF_UP));
        }
        if (StringUtils.hasText(estimate.getTransportMode())) {
            firstNode.setDepartureTransportMode(estimate.getTransportMode().trim());
        }
    }

    private boolean hasAnyDepartureEstimate(DepartureLegEstimateVO estimate) {
        return estimate != null
                && (estimate.getEstimatedMinutes() != null
                || estimate.getEstimatedDistanceKm() != null
                || StringUtils.hasText(estimate.getTransportMode()));
    }

    private DepartureLegEstimateVO buildDepartureFallback(GenerateReqDTO req, ItineraryNodeVO firstNode) {
        if (firstNode == null) {
            return null;
        }
        DepartureLegEstimateVO fallback = new DepartureLegEstimateVO();

        if (firstNode.getTravelTime() != null && firstNode.getTravelTime() > 0) {
            fallback.setEstimatedMinutes(firstNode.getTravelTime());
        }

        double roadDistanceKm = estimateRoadDistanceKm(req, firstNode);
        if (roadDistanceKm > 0D) {
            fallback.setEstimatedDistanceKm(BigDecimal.valueOf(roadDistanceKm).setScale(1, RoundingMode.HALF_UP));
        }

        String mode = resolveFallbackTransportMode(roadDistanceKm, fallback.getEstimatedMinutes());
        if (StringUtils.hasText(mode)) {
            fallback.setTransportMode(mode);
        }

        return hasAnyDepartureEstimate(fallback) ? fallback : null;
    }

    private String buildDepartureLegKey(GenerateReqDTO req, ItineraryNodeVO firstNode) {
        return String.join("#",
                defaultString(req != null && req.getDepartureLatitude() != null ? String.valueOf(req.getDepartureLatitude()) : null),
                defaultString(req != null && req.getDepartureLongitude() != null ? String.valueOf(req.getDepartureLongitude()) : null),
                defaultString(firstNode != null && firstNode.getPoiId() != null ? String.valueOf(firstNode.getPoiId()) : null),
                defaultString(firstNode == null ? null : firstNode.getPoiName())
        );
    }

    private boolean hasDepartureCoordinate(GenerateReqDTO req) {
        if (req == null || req.getDepartureLatitude() == null || req.getDepartureLongitude() == null) {
            return false;
        }
        double lat = req.getDepartureLatitude();
        double lng = req.getDepartureLongitude();
        return Math.abs(lat) <= 90D && Math.abs(lng) <= 180D;
    }

    private DepartureLegEstimateVO resolveDepartureLegEstimateByGeo(GenerateReqDTO req,
                                                                    ItineraryNodeVO firstNode) {
        if (geoSearchService == null
                || !hasDepartureCoordinate(req)
                || firstNode == null
                || firstNode.getLatitude() == null
                || firstNode.getLongitude() == null) {
            return null;
        }
        GeoPoint from = new GeoPoint(
                BigDecimal.valueOf(req.getDepartureLatitude()),
                BigDecimal.valueOf(req.getDepartureLongitude())
        );
        GeoPoint to = new GeoPoint(firstNode.getLatitude(), firstNode.getLongitude());
        if (!from.valid() || !to.valid()) {
            return null;
        }

        GeoRouteEstimate geoEstimate = geoSearchService
                .estimateTravel(from, to, req.getCityName(), null)
                .orElse(null);
        if (geoEstimate == null) {
            return null;
        }

        DepartureLegEstimateVO estimate = new DepartureLegEstimateVO();
        Integer minutes = geoEstimate.durationMinutes();
        if (minutes != null && minutes > 0) {
            estimate.setEstimatedMinutes(minutes);
        }
        BigDecimal distanceKm = geoEstimate.distanceKm();
        if (distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
            estimate.setEstimatedDistanceKm(distanceKm.setScale(1, RoundingMode.HALF_UP));
        }
        String mode = geoEstimate.transportMode();
        if (!StringUtils.hasText(mode)) {
            mode = resolveFallbackTransportMode(
                    estimate.getEstimatedDistanceKm() == null ? 0D : estimate.getEstimatedDistanceKm().doubleValue(),
                    estimate.getEstimatedMinutes()
            );
        }
        if (StringUtils.hasText(mode)) {
            estimate.setTransportMode(mode);
        }
        return hasAnyDepartureEstimate(estimate) ? estimate : null;
    }

    private SegmentTransportAnalysisVO resolveSegmentTransportAnalysis(GenerateReqDTO req,
                                                                      ItineraryNodeVO fromNode,
                                                                      ItineraryNodeVO toNode,
                                                                      AiBudget budget) {
        SegmentTransportAnalysisVO generated = resolveValueWithFallback(
                () -> llmService.analyzeSegmentTransport(req, fromNode, toNode),
                "analyzeSegmentTransport",
                budget
        );
        String factualMode = resolveExistingSegmentTransportMode(fromNode, toNode);
        String normalizedMode = normalizeTransportMode(generated == null ? null : generated.getTransportMode(), factualMode, fromNode, toNode);
        String fallbackNarrative = buildFallbackSegmentNarrative(req, fromNode, toNode, normalizedMode);
        String narrative = normalizeNarrative(generated == null ? null : generated.getNarrative(), fallbackNarrative);

        SegmentTransportAnalysisVO resolved = new SegmentTransportAnalysisVO();
        resolved.setTransportMode(normalizedMode);
        resolved.setNarrative(narrative);
        return resolved;
    }

    private void applySegmentTransportAnalysis(ItineraryNodeVO node,
                                               SegmentTransportAnalysisVO analysis,
                                               boolean firstStop) {
        if (node == null || analysis == null) {
            return;
        }
        if (StringUtils.hasText(analysis.getTransportMode())) {
            if (firstStop) {
                node.setDepartureTransportMode(analysis.getTransportMode().trim());
            }
            node.setTravelTransportMode(analysis.getTransportMode().trim());
        }
        if (StringUtils.hasText(analysis.getNarrative())) {
            node.setTravelNarrative(analysis.getNarrative().trim());
        }
    }

    private String resolveExistingSegmentTransportMode(ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        if (toNode == null) {
            return null;
        }
        if (fromNode == null && StringUtils.hasText(toNode.getDepartureTransportMode())) {
            return toNode.getDepartureTransportMode().trim();
        }
        if (StringUtils.hasText(toNode.getTravelTransportMode())) {
            return toNode.getTravelTransportMode().trim();
        }
        if (StringUtils.hasText(toNode.getDepartureTransportMode())) {
            return toNode.getDepartureTransportMode().trim();
        }
        double distanceKm = resolveSegmentDistanceKm(fromNode, toNode);
        Integer minutes = resolveSegmentMinutes(fromNode, toNode);
        return resolveFallbackTransportMode(distanceKm, minutes);
    }

    private String buildSegmentKey(GenerateReqDTO req, ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        String fromKey = fromNode == null
                ? "departure@" + defaultString(req != null && req.getDepartureLatitude() != null ? String.valueOf(req.getDepartureLatitude()) : null)
                + "," + defaultString(req != null && req.getDepartureLongitude() != null ? String.valueOf(req.getDepartureLongitude()) : null)
                : buildNodeKey(fromNode);
        return fromKey + "->" + buildNodeKey(toNode);
    }

    private String normalizeTransportMode(String candidate,
                                          String fallback,
                                          ItineraryNodeVO fromNode,
                                          ItineraryNodeVO toNode) {
        String raw = StringUtils.hasText(candidate) ? candidate.trim() : fallback;
        if (!StringUtils.hasText(raw)) {
            return resolveFallbackTransportMode(resolveSegmentDistanceKm(fromNode, toNode), resolveSegmentMinutes(fromNode, toNode));
        }
        String normalized = raw.toLowerCase(Locale.ROOT);
        if (normalized.contains("subway") || normalized.contains("metro")) {
            return "地铁+步行";
        }
        if (normalized.contains("bus") || normalized.contains("transit")) {
            return "公交+步行";
        }
        if (normalized.contains("taxi") || normalized.contains("cab") || normalized.contains("drive") || normalized.contains("ride")) {
            return "打车";
        }
        if (normalized.contains("bike") || normalized.contains("cycle")) {
            return "骑行";
        }
        if (normalized.contains("walk")) {
            return "步行";
        }
        if (isEnglishDominant(raw) && StringUtils.hasText(fallback)) {
            return fallback.trim();
        }
        return raw;
    }

    private String normalizeNarrative(String candidate, String fallback) {
        String preferred = keepChineseOrFallback(candidate, fallback);
        String narrative = StringUtils.hasText(preferred) ? preferred.trim() : fallback;
        if (!StringUtils.hasText(narrative)) {
            return null;
        }
        narrative = narrative.replaceAll("[\r\n]+", " ").trim();
        if (narrative.length() > 72) {
            narrative = narrative.substring(0, 72);
        }
        return narrative;
    }

    private String buildFallbackSegmentNarrative(GenerateReqDTO req,
                                                 ItineraryNodeVO fromNode,
                                                 ItineraryNodeVO toNode,
                                                 String transportMode) {
        String origin = fromNode == null
                ? defaultString(req == null ? null : req.getDeparturePlaceName())
                : defaultString(fromNode.getPoiName());
        if (!StringUtils.hasText(origin)) {
            origin = "当前位置";
        }
        String destination = toNode == null ? "下一站" : defaultString(toNode.getPoiName());
        if (!StringUtils.hasText(destination)) {
            destination = "下一站";
        }
        Integer minutes = resolveSegmentMinutes(fromNode, toNode);
        double distanceKm = resolveSegmentDistanceKm(fromNode, toNode);
        String mode = StringUtils.hasText(transportMode)
                ? transportMode.trim()
                : resolveFallbackTransportMode(distanceKm, minutes);
        if (minutes != null && minutes > 0 && distanceKm > 0D) {
            return origin + "到" + destination + "这段约" + minutes + "分钟，约"
                    + BigDecimal.valueOf(distanceKm).setScale(1, RoundingMode.HALF_UP).toPlainString()
                    + "公里，用" + mode + "更稳妥。";
        }
        if (minutes != null && minutes > 0) {
            return origin + "到" + destination + "这段约" + minutes + "分钟，用" + mode + "衔接更顺。";
        }
        return origin + "到" + destination + "这段建议优先用" + mode + "，整体节奏更稳。";
    }

    private Integer resolveSegmentMinutes(ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        if (toNode == null) {
            return null;
        }
        return fromNode == null ? toNode.getDepartureTravelTime() : toNode.getTravelTime();
    }

    private double resolveSegmentDistanceKm(ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        if (toNode == null) {
            return 0D;
        }
        BigDecimal distance = fromNode == null ? toNode.getDepartureDistanceKm() : toNode.getTravelDistanceKm();
        return distance == null ? 0D : Math.max(0D, distance.doubleValue());
    }

    private String resolveTextWithFallback(Supplier<String> supplier, String scene, AiBudget budget) {
        String result = resolveValueWithFallback(supplier, scene, budget);
        return StringUtils.hasText(result) ? result.trim() : null;
    }

    private <T> T resolveValueWithFallback(Supplier<T> supplier, String scene, AiBudget budget) {
        return resolveValueWithFallback(supplier, scene, budget, null);
    }

    private <T> T resolveValueWithFallback(Supplier<T> supplier,
                                           String scene,
                                           AiBudget budget,
                                           Long timeoutCapMs) {
        long timeoutMs = budget == null ? aiTimeoutMs : budget.nextTimeoutMs();
        if (timeoutCapMs != null && timeoutCapMs > 0L) {
            timeoutMs = Math.min(timeoutMs, timeoutCapMs);
        }
        if (timeoutMs <= 0) {
            log.debug("Skip AI decoration because budget is exhausted, scene={}", scene);
            return null;
        }

        Future<T> future;
        try {
            future = itineraryAiCallExecutor.submit(supplier::get);
        } catch (RejectedExecutionException ex) {
            log.warn("AI decoration executor is saturated, scene={}, timeoutMs={}, reason={}", scene, timeoutMs, summarizeExecutionFailure(ex));
            return null;
        }

        try {
            return future.get(timeoutMs, TimeUnit.MILLISECONDS);
        } catch (TimeoutException ex) {
            future.cancel(true);
            log.warn("AI decoration timed out, scene={}, timeoutMs={}", scene, timeoutMs);
            return null;
        } catch (InterruptedException ex) {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("AI decoration interrupted, scene={}", scene);
            return null;
        } catch (ExecutionException ex) {
            future.cancel(true);
            log.warn("AI decoration fallback, scene={}, reason={}", scene, summarizeExecutionFailure(ex));
            return null;
        } catch (RuntimeException ex) {
            future.cancel(true);
            log.warn("AI decoration fallback, scene={}, reason={}", scene, summarizeExecutionFailure(ex));
            return null;
        }
    }

    private String summarizeExecutionFailure(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                return current.getMessage().trim();
            }
            current = current.getCause();
        }
        return throwable == null ? "unknown" : throwable.getClass().getSimpleName();
    }

    private long resolveEffectiveAiBudgetMs() {
        if (aiTimeoutMs <= 1L) {
            return 1L;
        }
        long reserved = Math.min(DECORATION_BUDGET_RESERVE_MS, Math.max(40L, aiTimeoutMs / 5));
        long effective = aiTimeoutMs - reserved;
        return Math.max(120L, effective);
    }

    private static final class AiBudget {
        private final long deadlineNanos;

        private AiBudget(long totalTimeoutMs) {
            this.deadlineNanos = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(Math.max(totalTimeoutMs, 1L));
        }

        private long nextTimeoutMs() {
            long remainingNanos = deadlineNanos - System.nanoTime();
            if (remainingNanos <= 0) {
                return 0L;
            }
            return Math.max(1L, TimeUnit.NANOSECONDS.toMillis(remainingNanos));
        }
    }

    private String buildFallbackRouteWarmTip(GenerateReqDTO req, List<ItineraryNodeVO> nodes) {
        int stopCount = nodes == null ? 0 : nodes.size();
        if (Boolean.TRUE.equals(req == null ? null : req.getIsRainy())) {
            return "雨天路面偏滑，今天按主线慢慢走，拍照和休息都别压太满。";
        }
        if (Boolean.TRUE.equals(req == null ? null : req.getIsNight())) {
            return "夜游结束别拖太晚，给返程和等车都预留一点机动时间。";
        }
        if (stopCount >= 4) {
            return "今天站点不少，先按主线顺着走，中途记得给休息留出空档。";
        }
        return "今天这条线更适合顺路慢逛，边走边拍会比来回折返更舒服。";
    }

    private String keepChineseOrFallback(String generated, String fallback) {
        if (!StringUtils.hasText(generated)) {
            return fallback;
        }
        if (isEnglishDominant(generated)) {
            return fallback;
        }
        return generated;
    }

    private boolean isEnglishDominant(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        int englishLetters = 0;
        int cjkChars = 0;
        for (char ch : text.toCharArray()) {
            if ((ch >= 'a' && ch <= 'z') || (ch >= 'A' && ch <= 'Z')) {
                englishLetters++;
                continue;
            }
            if (Character.UnicodeScript.of(ch) == Character.UnicodeScript.HAN) {
                cjkChars++;
            }
        }
        return englishLetters >= 8 && (cjkChars == 0 || englishLetters > cjkChars * 2);
    }

    private String normalizeSingleTip(String preferred, String fallback) {
        String candidate = StringUtils.hasText(preferred) ? preferred.trim() : fallback;
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        if (isEnglishDominant(candidate) && StringUtils.hasText(fallback)) {
            candidate = fallback.trim();
        }
        candidate = candidate.replaceAll("[\\r\\n]+", " ").trim();
        candidate = stripTemplateLikeWarmTip(candidate);
        if (!StringUtils.hasText(candidate)) {
            candidate = StringUtils.hasText(fallback) ? fallback.trim() : null;
        }
        if (!StringUtils.hasText(candidate)) {
            return null;
        }
        if (candidate.length() > 40) {
            candidate = candidate.substring(0, 40);
        }
        return candidate;
    }


    private String stripTemplateLikeWarmTip(String candidate) {
        if (!StringUtils.hasText(candidate)) {
            return candidate;
        }
        String text = candidate.trim();
        List<String> templateMarkers = List.of(
                "\u7CFB\u7EDF\u5DF2\u6309\u5F53\u524D\u65F6\u95F4\u7A97",
                "\u53EF\u6267\u884C\u65B9\u6848",
                "\u5F53\u524D\u9ED8\u8BA4\u65B9\u6848",
                "\u5019\u9009\u8DEF\u7EBF",
                "\u51FA\u884C\u65E5\u671F"
        );
        for (String marker : templateMarkers) {
            if (text.contains(marker)) {
                return null;
            }
        }
        return text;
    }

    private String buildNodeKey(ItineraryNodeVO node) {
        if (node == null) {
            return "unknown";
        }
        return defaultString(node.getPoiId() == null ? null : String.valueOf(node.getPoiId()))
                + "#"
                + defaultString(node.getPoiName())
                + "#"
                + defaultString(node.getStepOrder() == null ? null : String.valueOf(node.getStepOrder()));
    }

    private double estimateRoadDistanceKm(GenerateReqDTO req, ItineraryNodeVO firstNode) {
        if (!hasDepartureCoordinate(req) || firstNode == null
                || firstNode.getLatitude() == null
                || firstNode.getLongitude() == null) {
            return 0D;
        }

        double fromLat = req.getDepartureLatitude();
        double fromLng = req.getDepartureLongitude();
        double toLat = firstNode.getLatitude().doubleValue();
        double toLng = firstNode.getLongitude().doubleValue();
        if (!isValidCoordinate(toLat, toLng)) {
            return 0D;
        }

        double straightKm = haversineDistanceKm(fromLat, fromLng, toLat, toLng);
        if (straightKm <= 0D) {
            return 0D;
        }

        double roadFactor;
        if (straightKm <= 1D) {
            roadFactor = 1.2D;
        } else if (straightKm <= 4D) {
            roadFactor = 1.3D;
        } else if (straightKm <= 10D) {
            roadFactor = 1.4D;
        } else {
            roadFactor = 1.5D;
        }
        return straightKm * roadFactor;
    }

    private double haversineDistanceKm(double lat1, double lon1, double lat2, double lon2) {
        final double earthRadiusKm = 6371.0D;
        double lat1Rad = Math.toRadians(lat1);
        double lat2Rad = Math.toRadians(lat2);
        double deltaLat = lat1Rad - lat2Rad;
        double deltaLon = Math.toRadians(lon1 - lon2);
        double a = Math.pow(Math.sin(deltaLat / 2), 2)
                + Math.cos(lat1Rad) * Math.cos(lat2Rad) * Math.pow(Math.sin(deltaLon / 2), 2);
        return 2 * earthRadiusKm * Math.asin(Math.sqrt(a));
    }

    private boolean isValidCoordinate(double lat, double lng) {
        return Math.abs(lat) <= 90D && Math.abs(lng) <= 180D;
    }

    private String resolveFallbackTransportMode(double roadDistanceKm, Integer minutes) {
        if (roadDistanceKm > 0D) {
            if (roadDistanceKm <= 1.2D) {
                return "步行";
            }
            if (roadDistanceKm <= 3.5D) {
                return "骑行";
            }
            if (roadDistanceKm <= 10D) {
                return "地铁+步行";
            }
            return "打车";
        }

        int duration = minutes == null ? 0 : minutes;
        if (duration <= 12) {
            return "步行";
        }
        if (duration <= 22) {
            return "骑行";
        }
        if (duration <= 45) {
            return "地铁+步行";
        }
        return "打车";
    }


    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    @SuppressWarnings("unused")
    private String buildComparisonHint(GenerateReqDTO req, ItineraryVO itinerary) {
        if (itineraryComparisonAssembler == null) {
            return null;
        }
        return itineraryComparisonAssembler.buildComparisonTips(
                req,
                itinerary.getOptions(),
                itinerary.getSelectedOptionKey()
        );
    }

    private void applyNearbyEnrichment(ItineraryVO itinerary, GenerateReqDTO req) {
        if (itinerary == null || nodeNearbyEnrichmentService == null) {
            return;
        }
        String cityName = req == null ? null : req.getCityName();
        nodeNearbyEnrichmentService.enrich(itinerary.getNodes(), cityName);
        if (itinerary.getOptions() != null) {
            for (ItineraryOptionVO option : itinerary.getOptions()) {
                if (option == null) {
                    continue;
                }
                nodeNearbyEnrichmentService.enrich(option.getNodes(), cityName);
            }
        }
    }

    private ItineraryVO copyItinerary(ItineraryVO source) {
        if (source == null) {
            return null;
        }
        try {
            return objectMapper.convertValue(source, ItineraryVO.class);
        } catch (IllegalArgumentException ex) {
            return source;
        }
    }
}

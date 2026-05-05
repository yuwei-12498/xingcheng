package com.citytrip.assembler;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.RouteFeatureVectorVO;
import com.citytrip.service.domain.planning.RouteAnalysisService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class ItineraryComparisonAssembler {

    private static final int MAX_OPTIONS = 5;

    private final RouteAnalysisService routeAnalysisService;

    public ItineraryComparisonAssembler(RouteAnalysisService routeAnalysisService) {
        this.routeAnalysisService = routeAnalysisService;
    }

    public ItineraryVO buildComparedItinerary(List<ItineraryRouteOptimizer.RouteOption> ranked,
                                              GenerateReqDTO req,
                                              Map<Long, String> existingReasons,
                                              String preferredSignature,
                                              Set<String> excludedOptionSignatures) {
        List<ItineraryRouteOptimizer.RouteOption> selectedRoutes = selectOptionRoutes(
                ranked,
                preferredSignature,
                excludedOptionSignatures
        );
        if (selectedRoutes.isEmpty()) {
            return buildEmptyItinerary(req);
        }

        List<RouteAnalysisService.RouteAnalysis> analyses = selectedRoutes.stream()
                .map(route -> routeAnalysisService.analyzeRoute(route, req, existingReasons))
                .toList();
        List<OptionStyle> styles = assignOptionStyles(analyses);

        List<ItineraryOptionVO> options = new ArrayList<>(analyses.size());
        for (int i = 0; i < analyses.size(); i++) {
            options.add(toOption(analyses.get(i), analyses, styles.get(i), req));
        }

        String selectedOptionKey = options.get(0).getOptionKey();
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setOriginalReq(req);
        itinerary.setOptions(options);
        itinerary.setSelectedOptionKey(selectedOptionKey);
        applySelectedOption(itinerary, options.get(0));
        itinerary.setTips(buildComparisonTips(req, options, selectedOptionKey));
        return itinerary;
    }

    public ItineraryVO buildEmptyItinerary(GenerateReqDTO req) {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setOriginalReq(req);
        itinerary.setOptions(Collections.emptyList());
        itinerary.setSelectedOptionKey(null);
        itinerary.setNodes(Collections.emptyList());
        itinerary.setTotalCost(BigDecimal.ZERO);
        itinerary.setTotalDuration(0);
        itinerary.setRecommendReason("当前条件下未找到可执行路线。");
        itinerary.setAlerts(List.of("可尝试放宽时间窗、降低约束条件或更换出行日期后再生成。"));
        itinerary.setTips(buildComparisonTips(req, Collections.emptyList(), null));
        return itinerary;
    }

    public String buildComparisonTips(GenerateReqDTO req,
                                      List<ItineraryOptionVO> options,
                                      String selectedOptionKey) {
        List<String> parts = new ArrayList<>();
        if (options == null || options.isEmpty()) {
            parts.add("当前条件下暂未生成可执行方案，可尝试放宽时间窗或更换出行日期后再生成。");
        } else {
            if (options.size() == 1) {
                parts.add("系统已基于当前时间窗生成 1 条可执行路线。");
            } else {
                parts.add("系统已按当前时间窗提供 " + options.size() + " 套可执行方案。");
            }
            if (req != null && req.getTripDays() != null && req.getTripDays() > 1.0D) {
                parts.add("多日模式会优先提升候选点位覆盖度，但当前结果仍以首日可执行性为主。");
            }
            ItineraryOptionVO selected = options.stream()
                    .filter(option -> Objects.equals(option.getOptionKey(), selectedOptionKey))
                    .findFirst()
                    .orElse(options.get(0));
            if (selected.getAlerts() != null && !selected.getAlerts().isEmpty()) {
                parts.add("当前默认方案提醒：" + selected.getAlerts().get(0));
                if (options.size() > 1) {
                    parts.add("你也可以切换查看其他候选路线。");
                }
            } else if (options.size() == 1) {
                parts.add("如需不同走法，可继续点击“换一版路线”重新生成。");
            } else {
                parts.add("当前默认方案更适合作为首选，你也可以切换为更省钱或更省时的候选路线。");
            }
        }
        if (req != null && StringUtils.hasText(req.getTripDate())) {
            parts.add("出行日期：" + req.getTripDate() + "。");
        }
        return String.join("", parts);
    }

    private void applySelectedOption(ItineraryVO itinerary, ItineraryOptionVO option) {
        itinerary.setNodes(option.getNodes());
        itinerary.setTotalCost(option.getTotalCost());
        itinerary.setTotalDuration(option.getTotalDuration());
        itinerary.setRecommendReason(option.getRecommendReason());
        itinerary.setRecommendationSource(option.getRecommendationSource());
        itinerary.setAiDecorated(option.getAiDecorated());
        itinerary.setAlerts(option.getAlerts());
    }

    private List<ItineraryRouteOptimizer.RouteOption> selectOptionRoutes(List<ItineraryRouteOptimizer.RouteOption> ranked,
                                                                         String preferredSignature,
                                                                         Set<String> excludedOptionSignatures) {
        if (ranked == null || ranked.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItineraryRouteOptimizer.RouteOption> filtered = ranked.stream()
                .filter(route -> route != null && route.path() != null && !route.path().isEmpty())
                .filter(route -> excludedOptionSignatures == null
                        || !excludedOptionSignatures.contains(route.signature())
                        || Objects.equals(route.signature(), preferredSignature))
                .toList();
        if (filtered.isEmpty()) {
            return Collections.emptyList();
        }

        List<ItineraryRouteOptimizer.RouteOption> selected = new ArrayList<>();
        if (StringUtils.hasText(preferredSignature)) {
            filtered.stream()
                    .filter(route -> Objects.equals(route.signature(), preferredSignature))
                    .findFirst()
                    .ifPresent(selected::add);
        }

        for (ItineraryRouteOptimizer.RouteOption route : filtered) {
            if (selected.size() >= MAX_OPTIONS) {
                break;
            }
            if (selected.stream().anyMatch(item -> Objects.equals(item.signature(), route.signature()))) {
                continue;
            }
            if (selected.isEmpty() || isDistinctEnough(route, selected)) {
                selected.add(route);
            }
        }

        for (ItineraryRouteOptimizer.RouteOption route : filtered) {
            if (selected.size() >= MAX_OPTIONS) {
                break;
            }
            if (selected.stream().noneMatch(item -> Objects.equals(item.signature(), route.signature()))) {
                selected.add(route);
            }
        }
        return selected;
    }

    private boolean isDistinctEnough(ItineraryRouteOptimizer.RouteOption candidate,
                                     List<ItineraryRouteOptimizer.RouteOption> selected) {
        Set<Long> candidateIds = safePoiIds(candidate == null ? null : candidate.path());
        for (ItineraryRouteOptimizer.RouteOption existing : selected) {
            Set<Long> existingIds = safePoiIds(existing == null ? null : existing.path());
            long overlapCount = candidateIds.stream().filter(existingIds::contains).count();
            int divisor = Math.max(1, Math.min(candidateIds.size(), existingIds.size()));
            double overlap = overlapCount * 1.0D / divisor;
            if (overlap > 0.75D) {
                return false;
            }
        }
        return true;
    }

    private List<OptionStyle> assignOptionStyles(List<RouteAnalysisService.RouteAnalysis> analyses) {
        if (analyses.isEmpty()) {
            return Collections.emptyList();
        }

        int budgetIndex = findIndex(analyses, Comparator.comparing(RouteAnalysisService.RouteAnalysis::totalCost));
        int efficientIndex = findIndex(analyses, Comparator
                .comparingInt(RouteAnalysisService.RouteAnalysis::totalTravelTime)
                .thenComparingInt(RouteAnalysisService.RouteAnalysis::totalDuration));
        int exploreIndex = findIndex(analyses, Comparator
                .comparingInt(RouteAnalysisService.RouteAnalysis::themeMatchCount).reversed()
                .thenComparingInt(RouteAnalysisService.RouteAnalysis::stopCount).reversed()
                .thenComparingDouble(RouteAnalysisService.RouteAnalysis::utility).reversed());
        int stableIndex = findIndex(analyses, Comparator
                .comparingInt(RouteAnalysisService.RouteAnalysis::businessRiskScore)
                .thenComparingInt(RouteAnalysisService.RouteAnalysis::totalTravelTime));

        List<OptionStyle> styles = new ArrayList<>(analyses.size());
        styles.add(new OptionStyle("balanced", "均衡方案", "偏好匹配、路线顺滑与可执行性更均衡"));
        for (int i = 1; i < analyses.size(); i++) {
            if (i == budgetIndex) {
                styles.add(new OptionStyle("budget", "省钱方案", "适合预算控制优先的出行场景"));
            } else if (i == efficientIndex) {
                styles.add(new OptionStyle("efficient", "高效方案", "优先减少绕路，整体时间效率更高"));
            } else if (i == stableIndex) {
                styles.add(new OptionStyle("stable", "稳妥方案", "优先选择营业状态和时间窗风险更低的点位"));
            } else if (i == exploreIndex) {
                styles.add(new OptionStyle("explore", "探索方案", "更强调主题覆盖和高分亮点探索"));
            } else {
                styles.add(new OptionStyle("alternative-" + (i + 1), "备选方案" + (i + 1), "提供另一种可执行取舍，便于横向比较"));
            }
        }
        return styles;
    }

    private int findIndex(List<RouteAnalysisService.RouteAnalysis> analyses,
                          Comparator<RouteAnalysisService.RouteAnalysis> comparator) {
        RouteAnalysisService.RouteAnalysis best = analyses.stream().min(comparator).orElse(analyses.get(0));
        return analyses.indexOf(best);
    }

    private ItineraryOptionVO toOption(RouteAnalysisService.RouteAnalysis analysis,
                                       List<RouteAnalysisService.RouteAnalysis> analyses,
                                       OptionStyle style,
                                       GenerateReqDTO req) {
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey(style.key());
        option.setTitle(style.title());
        option.setSubtitle(style.subtitle());
        option.setSignature(analysis.route() == null ? "" : analysis.route().signature());
        option.setTotalDuration(analysis.totalDuration());
        option.setTotalCost(analysis.totalCost());
        option.setStopCount(analysis.stopCount());
        option.setTotalTravelTime(analysis.totalTravelTime());
        option.setBusinessRiskScore(analysis.businessRiskScore());
        option.setThemeMatchCount(analysis.themeMatchCount());
        option.setRouteUtility(analysis.utility());
        option.setFeatureVector(buildFeatureVector(analysis, req));
        option.setNodes(analysis.nodes());
        option.setAlerts(analysis.alerts());
        option.setSummary(buildOptionSummary(analysis, analyses));
        option.setHighlights(buildHighlights(analysis, analyses));
        option.setTradeoffs(buildTradeoffs(analysis, analyses));
        option.setRecommendReason(buildRecommendReason(analysis, analyses, style));
        option.setNotRecommendReason(buildNotRecommendReason(analysis, analyses));
        return option;
    }

    private RouteFeatureVectorVO buildFeatureVector(RouteAnalysisService.RouteAnalysis analysis, GenerateReqDTO req) {
        RouteFeatureVectorVO vector = new RouteFeatureVectorVO();
        vector.setSignature(analysis.route() == null ? "" : analysis.route().signature());
        vector.setStopCount(analysis.stopCount());
        vector.setTotalCostEstimated(analysis.totalCost());
        vector.setTotalDurationMinutes(analysis.totalDuration());
        vector.setTotalTravelTimeMinutes(analysis.totalTravelTime());
        vector.setTotalWaitTimeMinutes(analysis.totalWaitTime());
        vector.setTotalWalkingDistanceEstimatedKm(resolveWalkingDistance(analysis.nodes()));
        vector.setThemeMatchCount(analysis.themeMatchCount());
        vector.setCompanionMatchCount(analysis.companionMatchCount());
        vector.setNightFriendlyCount(analysis.nightFriendlyCount());
        vector.setIndoorFriendlyCount(analysis.indoorFriendlyCount());
        vector.setBusinessRiskScore(analysis.businessRiskScore());
        vector.setUniqueDistrictCount(analysis.uniqueDistrictCount());
        vector.setRouteUtility(analysis.utility());
        Map<String, Object> breakdown = new LinkedHashMap<>();
        breakdown.put("utility", analysis.utility());
        breakdown.put("themeMatchCount", analysis.themeMatchCount());
        breakdown.put("companionMatchCount", analysis.companionMatchCount());
        breakdown.put("businessRiskScore", analysis.businessRiskScore());
        breakdown.put("totalWaitTimeMinutes", analysis.totalWaitTime());
        breakdown.put("totalTravelTimeMinutes", analysis.totalTravelTime());
        breakdown.put("totalCostEstimated", analysis.totalCost());
        breakdown.put("uniqueDistrictCount", analysis.uniqueDistrictCount());
        breakdown.put("externalPoiCount", countExternalPoi(analysis.nodes()));
        breakdown.put("mustVisitRequested", req == null || req.getMustVisitPoiNames() == null ? 0 : req.getMustVisitPoiNames().size());
        breakdown.put("mustVisitCovered", countMustVisitCovered(analysis.nodes(), req));
        breakdown.put("mustVisitSatisfied", isMustVisitSatisfied(analysis.nodes(), req));
        breakdown.put("budgetCeiling", req == null ? null : req.getTotalBudget());
        breakdown.put("budgetRemaining", resolveBudgetRemaining(req, analysis.totalCost()));
        breakdown.put("poiScoreBreakdown", buildPoiScoreBreakdown(analysis.nodes()));
        vector.setScoreBreakdown(breakdown);
        return vector;
    }

    private int countExternalPoi(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        return safeLongToInt(nodes.stream()
                .filter(Objects::nonNull)
                .filter(node -> "external".equalsIgnoreCase(node.getSourceType()))
                .count());
    }

    private int countMustVisitCovered(List<ItineraryNodeVO> nodes, GenerateReqDTO req) {
        return normalizeMustVisitKeywords(req).stream()
                .map(String::toLowerCase)
                .mapToInt(keyword -> containsKeyword(nodes, keyword) ? 1 : 0)
                .sum();
    }

    private boolean isMustVisitSatisfied(List<ItineraryNodeVO> nodes, GenerateReqDTO req) {
        List<String> mustVisitKeywords = normalizeMustVisitKeywords(req);
        return mustVisitKeywords.isEmpty() || countMustVisitCovered(nodes, req) >= mustVisitKeywords.size();
    }

    private BigDecimal resolveBudgetRemaining(GenerateReqDTO req, BigDecimal totalCost) {
        if (req == null || req.getTotalBudget() == null || totalCost == null) {
            return null;
        }
        return BigDecimal.valueOf(req.getTotalBudget()).subtract(totalCost).setScale(2, RoundingMode.HALF_UP);
    }

    private Map<String, Object> buildPoiScoreBreakdown(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<String, Object> result = new LinkedHashMap<>();
        for (ItineraryNodeVO node : nodes) {
            if (node == null || !StringUtils.hasText(node.getPoiName()) || node.getScoreBreakdown() == null || node.getScoreBreakdown().isEmpty()) {
                continue;
            }
            result.put(node.getPoiName(), new LinkedHashMap<>(node.getScoreBreakdown()));
        }
        return result;
    }

    private List<String> normalizeMustVisitKeywords(GenerateReqDTO req) {
        if (req == null || req.getMustVisitPoiNames() == null || req.getMustVisitPoiNames().isEmpty()) {
            return Collections.emptyList();
        }
        return req.getMustVisitPoiNames().stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .distinct()
                .toList();
    }

    private boolean containsKeyword(List<ItineraryNodeVO> nodes, String keyword) {
        if (!StringUtils.hasText(keyword) || nodes == null || nodes.isEmpty()) {
            return false;
        }
        return nodes.stream()
                .filter(Objects::nonNull)
                .map(ItineraryNodeVO::getPoiName)
                .filter(StringUtils::hasText)
                .map(name -> name.toLowerCase())
                .anyMatch(name -> name.contains(keyword) || keyword.contains(name));
    }

    private int safeLongToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private BigDecimal resolveWalkingDistance(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return BigDecimal.ZERO.setScale(1, RoundingMode.HALF_UP);
        }
        BigDecimal total = BigDecimal.ZERO;
        for (ItineraryNodeVO node : nodes) {
            if (node == null || node.getTravelDistanceKm() == null || !isWalkingMode(node.getTravelTransportMode())) {
                continue;
            }
            total = total.add(node.getTravelDistanceKm());
        }
        return total.setScale(1, RoundingMode.HALF_UP);
    }

    private boolean isWalkingMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return false;
        }
        String normalized = mode.trim().toLowerCase();
        return normalized.contains("walk") || normalized.contains("步行");
    }

    private String buildOptionSummary(RouteAnalysisService.RouteAnalysis analysis,
                                      List<RouteAnalysisService.RouteAnalysis> analyses) {
        List<ItineraryNodeVO> nodes = analysis.nodes();
        if (nodes == null || nodes.isEmpty()) {
            return "当前方案暂无可展示节点，建议重新生成路线后再比较。";
        }
        List<String> parts = new ArrayList<>();
        if (isBestUtility(analysis, analyses)) {
            parts.add("综合得分最高");
        }
        if (isMinCost(analysis, analyses)) {
            parts.add("总成本最低");
        }
        if (isMinTravel(analysis, analyses)) {
            parts.add("路途耗时最短");
        }
        if (isMaxThemeMatch(analysis, analyses)) {
            parts.add("主题覆盖最集中");
        }
        if (isMinRisk(analysis, analyses)) {
            parts.add("营业风险最低");
        }
        if (parts.isEmpty()) {
            parts.add("另一种仍然可执行的取舍");
        }
        return "以" + nodes.get(0).getPoiName()
                + "为起点，以" + nodes.get(nodes.size() - 1).getPoiName()
                + "收尾，整体特点是：" + String.join("、", parts) + "。";
    }

    private List<String> buildHighlights(RouteAnalysisService.RouteAnalysis analysis,
                                         List<RouteAnalysisService.RouteAnalysis> analyses) {
        List<String> tags = new ArrayList<>();
        if (isBestUtility(analysis, analyses)) {
            tags.add("综合最均衡");
        }
        if (isMinCost(analysis, analyses)) {
            tags.add("预算压力最低");
        }
        if (isMinTravel(analysis, analyses)) {
            tags.add("路途耗时最短");
        }
        if (isMaxThemeMatch(analysis, analyses)) {
            tags.add("主题覆盖最强");
        }
        if (isMinRisk(analysis, analyses)) {
            tags.add("营业风险最低");
        }
        if (analysis.uniqueDistrictCount() > 1) {
            tags.add("区域覆盖更广");
        }
        return tags.stream().limit(4).toList();
    }

    private List<String> buildTradeoffs(RouteAnalysisService.RouteAnalysis analysis,
                                        List<RouteAnalysisService.RouteAnalysis> analyses) {
        List<String> tags = new ArrayList<>();
        BigDecimal minCost = analyses.stream()
                .map(RouteAnalysisService.RouteAnalysis::totalCost)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int minTravel = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::totalTravelTime)
                .min()
                .orElse(0);
        int minRisk = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::businessRiskScore)
                .min()
                .orElse(0);
        int maxStops = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::stopCount)
                .max()
                .orElse(analysis.stopCount());

        if (analysis.totalCost().compareTo(minCost.add(new BigDecimal("30"))) > 0) {
            tags.add("成本更高");
        }
        if (analysis.totalTravelTime() > minTravel + 20) {
            tags.add("路途更长");
        }
        if (analysis.businessRiskScore() > minRisk) {
            tags.add("需再次确认营业状态");
        }
        if (analysis.stopCount() < maxStops) {
            tags.add("点位密度较低");
        }
        return tags.stream().limit(3).toList();
    }

    private String buildRecommendReason(RouteAnalysisService.RouteAnalysis analysis,
                                        List<RouteAnalysisService.RouteAnalysis> analyses,
                                        OptionStyle style) {
        List<String> reasons = new ArrayList<>();
        if ("balanced".equals(style.key())) {
            reasons.add("这条路线在偏好匹配、通行顺滑度和可执行性之间更均衡");
        }
        if (isBestUtility(analysis, analyses)) {
            reasons.add("它在候选方案中的综合得分最高");
        }
        if (isMinCost(analysis, analyses)) {
            reasons.add("它的总花费低于其他方案");
        }
        if (isMinTravel(analysis, analyses)) {
            reasons.add("它在路上的耗时更少，绕路情况也更少");
        }
        if (isMaxThemeMatch(analysis, analyses)) {
            reasons.add("它覆盖了更多符合你主题偏好的点位");
        }
        if (isMinRisk(analysis, analyses)) {
            reasons.add("它的营业状态与时间窗风险更低");
        }
        if (reasons.isEmpty()) {
            reasons.add("在当前约束下，它依然是一条稳妥且可执行的路线");
        }
        return String.join("；", reasons) + "。";
    }

    private String buildNotRecommendReason(RouteAnalysisService.RouteAnalysis analysis,
                                           List<RouteAnalysisService.RouteAnalysis> analyses) {
        List<String> reasons = new ArrayList<>();
        BigDecimal minCost = analyses.stream()
                .map(RouteAnalysisService.RouteAnalysis::totalCost)
                .min(BigDecimal::compareTo)
                .orElse(BigDecimal.ZERO);
        int minTravel = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::totalTravelTime)
                .min()
                .orElse(0);
        int minRisk = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::businessRiskScore)
                .min()
                .orElse(0);
        int maxTheme = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::themeMatchCount)
                .max()
                .orElse(analysis.themeMatchCount());

        if (analysis.totalCost().compareTo(minCost.add(new BigDecimal("30"))) > 0) {
            reasons.add("如果你最看重预算，这条方案花费会更高");
        }
        if (analysis.totalTravelTime() > minTravel + 20) {
            reasons.add("如果你更在意少绕路，这条方案在路上的时间会更长");
        }
        if (analysis.businessRiskScore() > minRisk) {
            reasons.add("如果你最看重稳妥性，部分点位仍建议出发前再次确认营业状态");
        }
        if (analysis.themeMatchCount() < maxTheme) {
            reasons.add("如果你最看重主题纯度，其他方案的主题聚焦度会更高");
        }
        if (reasons.isEmpty()) {
            return "它不太适合只追求单一指标的用户，例如极致省钱或极致刷点。";
        }
        return String.join("；", reasons.stream().limit(2).toList()) + "。";
    }

    private boolean isBestUtility(RouteAnalysisService.RouteAnalysis analysis,
                                  List<RouteAnalysisService.RouteAnalysis> analyses) {
        double max = analyses.stream().mapToDouble(RouteAnalysisService.RouteAnalysis::utility).max().orElse(analysis.utility());
        return Double.compare(analysis.utility(), max) == 0;
    }

    private boolean isMinCost(RouteAnalysisService.RouteAnalysis analysis,
                              List<RouteAnalysisService.RouteAnalysis> analyses) {
        BigDecimal min = analyses.stream()
                .map(RouteAnalysisService.RouteAnalysis::totalCost)
                .min(BigDecimal::compareTo)
                .orElse(analysis.totalCost());
        return analysis.totalCost().compareTo(min) == 0;
    }

    private boolean isMinTravel(RouteAnalysisService.RouteAnalysis analysis,
                                List<RouteAnalysisService.RouteAnalysis> analyses) {
        int min = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::totalTravelTime)
                .min()
                .orElse(analysis.totalTravelTime());
        return analysis.totalTravelTime() == min;
    }

    private boolean isMaxThemeMatch(RouteAnalysisService.RouteAnalysis analysis,
                                    List<RouteAnalysisService.RouteAnalysis> analyses) {
        int max = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::themeMatchCount)
                .max()
                .orElse(analysis.themeMatchCount());
        return analysis.themeMatchCount() == max;
    }

    private boolean isMinRisk(RouteAnalysisService.RouteAnalysis analysis,
                              List<RouteAnalysisService.RouteAnalysis> analyses) {
        int min = analyses.stream()
                .mapToInt(RouteAnalysisService.RouteAnalysis::businessRiskScore)
                .min()
                .orElse(analysis.businessRiskScore());
        return analysis.businessRiskScore() == min;
    }

    private Set<Long> safePoiIds(List<Poi> path) {
        if (path == null || path.isEmpty()) {
            return Collections.emptySet();
        }
        return path.stream()
                .filter(Objects::nonNull)
                .map(Poi::getId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private record OptionStyle(String key, String title, String subtitle) {
    }
}

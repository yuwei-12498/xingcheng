package com.citytrip.service.impl;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.planning.ExternalPoiCandidateService;
import com.citytrip.service.domain.policy.MaxStopsPolicy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class PlanningOrchestrator {

    public static final String ALGORITHM_VERSION = "constraint-aware-hybrid-v3";
    public static final String RECALL_STRATEGY = "hybrid-usercf-content-v1";

    private static final Logger log = LoggerFactory.getLogger(PlanningOrchestrator.class);
    private static final int PLANNING_DB_FETCH_LIMIT = 200;
    private static final int RECALL_LIMIT = 18;
    private static final int EXTERNAL_RECALL_LIMIT = 8;
    private static final int MAX_RECALL_POOL_SIZE = RECALL_LIMIT + EXTERNAL_RECALL_LIMIT;
    private static final Pattern ASCII_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");
    private final PoiMapper poiMapper;
    private final ItineraryRouteOptimizer routeOptimizer;
    private final HybridPoiRecallService hybridPoiRecallService;
    private final ExternalPoiCandidateService externalPoiCandidateService;
    private final Executor planningExecutor;
    private final Executor aiExecutor;
    private final long aiTimeoutMs;
    private final MaxStopsPolicy maxStopsPolicy;

    public PlanningOrchestrator(PoiMapper poiMapper,
                                ItineraryRouteOptimizer routeOptimizer,
                                HybridPoiRecallService hybridPoiRecallService,
                                ExternalPoiCandidateService externalPoiCandidateService,
                                MaxStopsPolicy maxStopsPolicy,
                                @Qualifier("itineraryPlanningExecutor") Executor planningExecutor,
                                @Qualifier("itineraryAiExecutor") Executor aiExecutor,
                                @Value("${app.planning.ai-timeout-ms:800}") long aiTimeoutMs) {
        this.poiMapper = poiMapper;
        this.routeOptimizer = routeOptimizer;
        this.hybridPoiRecallService = hybridPoiRecallService;
        this.externalPoiCandidateService = externalPoiCandidateService;
        this.maxStopsPolicy = maxStopsPolicy;
        this.planningExecutor = planningExecutor;
        this.aiExecutor = aiExecutor;
        this.aiTimeoutMs = aiTimeoutMs;
    }

    public PlanningResult generate(Long userId,
                                   GenerateReqDTO request,
                                   RouteItineraryBuilder itineraryBuilder,
                                   AiItineraryDecorator aiDecorator) {
        LocalDateTime planningStartedAt = LocalDateTime.now();
        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(request);

        CompletableFuture<RecallStage> recallFuture = CompletableFuture.supplyAsync(
                () -> safeRecallStage(userId, normalized),
                planningExecutor
        );
        CompletableFuture<RoutePlanningSnapshot> routeFuture = recallFuture.thenApplyAsync(
                stage -> routePlanningSnapshot(stage, normalized),
                planningExecutor
        );
        CompletableFuture<ItineraryVO> baseFuture = routeFuture.thenApplyAsync(
                itineraryBuilder::build,
                planningExecutor
        );
        CompletableFuture<ItineraryVO> decoratedFuture = baseFuture.thenCompose(baseItinerary -> CompletableFuture
                .supplyAsync(() -> aiDecorator.decorate(normalized, baseItinerary), aiExecutor)
                .completeOnTimeout(baseItinerary, aiTimeoutMs, TimeUnit.MILLISECONDS)
                .exceptionally(ex -> {
                    log.warn("AI explain stage fallback to rule-based itinerary, reason={}", ex.getMessage());
                    return baseItinerary;
                }));

        return routeFuture.thenCombine(decoratedFuture, (snapshot, itinerary) -> {
            boolean success = itinerary != null && itinerary.getOptions() != null && !itinerary.getOptions().isEmpty();
            String failReason = success ? null : resolveFailReason(snapshot);
            return new PlanningResult(
                    snapshot.normalizedRequest(),
                    itinerary,
                    snapshot.rawCandidateCount(),
                    snapshot.filteredCandidateCount(),
                    snapshot.finalCandidateCount(),
                    snapshot.maxStops(),
                    snapshot.generatedRouteCount(),
                    itinerary == null || itinerary.getOptions() == null ? 0 : itinerary.getOptions().size(),
                    ALGORITHM_VERSION,
                    snapshot.recallStrategy(),
                    success,
                    failReason,
                    planningStartedAt
            );
        }).join();
    }

    private RecallStage recallStage(Long userId, GenerateReqDTO normalized) {
        List<Poi> rawCandidates = poiMapper.selectPlanningCandidates(
                normalized != null && Boolean.TRUE.equals(normalized.getIsRainy()),
                normalized == null ? null : normalized.getWalkingLevel(),
                normalized == null ? null : normalized.getCityCode(),
                normalized == null ? null : normalized.getCityName(),
                PLANNING_DB_FETCH_LIMIT
        );
        HybridPoiRecallService.RecallResult recallResult = hybridPoiRecallService.recall(
                userId,
                normalized,
                rawCandidates,
                RECALL_LIMIT
        );
        List<Poi> recalledCandidates = recallResult.recalledCandidates() == null
                ? Collections.emptyList()
                : recallResult.recalledCandidates();
        List<Poi> externalCandidates = recallExternalCandidates(recalledCandidates, normalized);
        List<Poi> mergedCandidates = mergeRecalledCandidates(recalledCandidates, externalCandidates);
        String recallStrategy = normalizeRecallStrategy(recallResult.recallStrategy());
        if (!externalCandidates.isEmpty()) {
            log.info("Planning recall merged {} external POIs from GEO API.", externalCandidates.size());
            recallStrategy = recallStrategy + "+geo-poi";
        }
        return new RecallStage(
                rawCandidates == null ? Collections.emptyList() : rawCandidates,
                recallResult.filteredCandidates(),
                mergedCandidates,
                recallStrategy
        );
    }

    private RecallStage safeRecallStage(Long userId, GenerateReqDTO normalized) {
        try {
            return recallStage(userId, normalized);
        } catch (Exception ex) {
            log.warn("Planning recall stage fallback to empty candidates, reason={}", ex.getMessage(), ex);
            return new RecallStage(
                    Collections.emptyList(),
                    Collections.emptyList(),
                    Collections.emptyList(),
                    RECALL_STRATEGY + "-degraded"
            );
        }
    }

    private List<Poi> recallExternalCandidates(List<Poi> baseCandidates, GenerateReqDTO normalized) {
        if (externalPoiCandidateService == null || baseCandidates == null || baseCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            List<Poi> external = externalPoiCandidateService.recallForReplan(baseCandidates, normalized, EXTERNAL_RECALL_LIMIT);
            if (external == null || external.isEmpty()) {
                return Collections.emptyList();
            }
            List<Poi> prepared = routeOptimizer.prepareCandidates(external, normalized, false);
            return prepared == null ? Collections.emptyList() : prepared;
        } catch (Exception ex) {
            log.warn("External GEO recall failed during generate stage, fallback to local pool only. reason={}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private List<Poi> mergeRecalledCandidates(List<Poi> recalledCandidates, List<Poi> externalCandidates) {
        if ((recalledCandidates == null || recalledCandidates.isEmpty())
                && (externalCandidates == null || externalCandidates.isEmpty())) {
            return Collections.emptyList();
        }
        List<Poi> merged = new ArrayList<>();
        Set<String> dedupeKeys = new LinkedHashSet<>();

        appendCandidates(merged, dedupeKeys, recalledCandidates);
        appendCandidates(merged, dedupeKeys, externalCandidates);

        if (merged.size() > MAX_RECALL_POOL_SIZE) {
            return new ArrayList<>(merged.subList(0, MAX_RECALL_POOL_SIZE));
        }
        return merged;
    }

    private void appendCandidates(List<Poi> merged, Set<String> dedupeKeys, List<Poi> candidates) {
        if (merged == null || dedupeKeys == null || candidates == null || candidates.isEmpty()) {
            return;
        }
        for (Poi candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            String key = buildCandidateDedupeKey(candidate);
            if (dedupeKeys.add(key)) {
                merged.add(candidate);
            }
        }
    }

    private String buildCandidateDedupeKey(Poi poi) {
        if (poi == null) {
            return "null";
        }
        if (poi.getId() != null) {
            return "id:" + poi.getId();
        }
        String name = StringUtils.hasText(poi.getName()) ? poi.getName().trim().toLowerCase(Locale.ROOT) : "";
        String lat = poi.getLatitude() == null ? "" : poi.getLatitude().toPlainString();
        String lon = poi.getLongitude() == null ? "" : poi.getLongitude().toPlainString();
        return name + "|" + lat + "|" + lon;
    }

    private RoutePlanningSnapshot routePlanningSnapshot(RecallStage recallStage, GenerateReqDTO normalized) {
        List<Poi> candidates = recallStage.recalledCandidates() == null ? Collections.emptyList() : recallStage.recalledCandidates();
        candidates = mergeWithMustVisitCandidates(candidates, recallStage.filteredCandidates(), normalized);
        int maxStops = maxStopsPolicy.resolve(normalized, candidates.size());
        int tripDayCount = resolveTripDayCount(normalized);
        List<ItineraryRouteOptimizer.RouteOption> rankedRoutes = tripDayCount > 1
                ? rankMultiDayRoutes(candidates, normalized, maxStops, tripDayCount)
                : routeOptimizer.rankRoutes(candidates, normalized, maxStops);
        String failureReason = rankedRoutes == null || rankedRoutes.isEmpty()
                ? diagnosePlanningFailure(candidates, normalized, maxStops, tripDayCount)
                : null;
        return new RoutePlanningSnapshot(
                normalized,
                recallStage.rawCandidates().size(),
                recallStage.filteredCandidates().size(),
                candidates.size(),
                maxStops,
                rankedRoutes == null ? Collections.emptyList() : rankedRoutes,
                recallStage.recallStrategy(),
                failureReason
        );
    }

    private List<ItineraryRouteOptimizer.RouteOption> rankMultiDayRoutes(List<Poi> candidates,
                                                                          GenerateReqDTO normalized,
                                                                          int maxStops,
                                                                          int tripDayCount) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        List<Poi> remaining = new ArrayList<>(candidates);
        List<Poi> mergedPath = new ArrayList<>();
        List<String> daySignatures = new ArrayList<>();
        List<String> remainingMustVisit = new ArrayList<>(normalizeMustVisitKeywords(normalized));
        double totalUtility = 0D;

        for (int day = 0; day < tripDayCount; day++) {
            if (remaining.isEmpty()) {
                break;
            }
            int dailyMaxStops = resolveDailyMaxStops(maxStops, tripDayCount, remaining.size());
            if (dailyMaxStops <= 0) {
                break;
            }
            List<String> dayMustVisitKeywords = selectDailyMustVisitKeywords(remaining, remainingMustVisit, dailyMaxStops);
            GenerateReqDTO dayRequest = dayMustVisitKeywords.isEmpty()
                    ? normalized
                    : copyRequestWithMustVisit(normalized, dayMustVisitKeywords);
            List<ItineraryRouteOptimizer.RouteOption> dailyRanked = routeOptimizer.rankRoutes(remaining, dayRequest, dailyMaxStops);
            if (dailyRanked == null || dailyRanked.isEmpty() || dailyRanked.get(0) == null || dailyRanked.get(0).path().isEmpty()) {
                break;
            }

            ItineraryRouteOptimizer.RouteOption bestForDay = dailyRanked.get(0);
            mergedPath.addAll(bestForDay.path());
            daySignatures.add(bestForDay.signature());
            totalUtility += bestForDay.utility();
            remainingMustVisit.removeIf(keyword -> pathContainsKeyword(bestForDay.path(), keyword));

            Set<Long> usedPoiIds = bestForDay.path().stream()
                    .map(Poi::getId)
                    .collect(java.util.stream.Collectors.toSet());
            remaining.removeIf(poi -> poi != null && poi.getId() != null && usedPoiIds.contains(poi.getId()));
        }

        if (!remainingMustVisit.isEmpty()) {
            return Collections.emptyList();
        }
        if (mergedPath.isEmpty()) {
            return routeOptimizer.rankRoutes(candidates, normalized, maxStops);
        }
        String combinedSignature = String.join("|", daySignatures);
        return List.of(new ItineraryRouteOptimizer.RouteOption(mergedPath, combinedSignature, totalUtility));
    }

    private int resolveTripDayCount(GenerateReqDTO normalized) {
        if (normalized == null || normalized.getTripDays() == null) {
            return 1;
        }
        return Math.max(1, (int) Math.round(normalized.getTripDays()));
    }

    private int resolveDailyMaxStops(int maxStops, int tripDayCount, int remainingSize) {
        if (remainingSize <= 0 || maxStops <= 0) {
            return 0;
        }
        int average = (int) Math.ceil(maxStops * 1.0D / Math.max(tripDayCount, 1));
        int bounded = Math.max(1, average);
        return Math.min(bounded, remainingSize);
    }

    private GenerateReqDTO copyRequestWithMustVisit(GenerateReqDTO source, List<String> mustVisitKeywords) {
        if (source == null) {
            return null;
        }
        GenerateReqDTO copy = new GenerateReqDTO();
        copy.setCityName(source.getCityName());
        copy.setCityCode(source.getCityCode());
        copy.setTripDays(1.0D);
        copy.setTripDate(source.getTripDate());
        copy.setTotalBudget(source.getTotalBudget());
        copy.setBudgetLevel(source.getBudgetLevel());
        copy.setThemes(source.getThemes() == null ? List.of() : new ArrayList<>(source.getThemes()));
        copy.setIsRainy(source.getIsRainy());
        copy.setIsNight(source.getIsNight());
        copy.setWalkingLevel(source.getWalkingLevel());
        copy.setCompanionType(source.getCompanionType());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setMustVisitPoiNames(mustVisitKeywords == null ? List.of() : new ArrayList<>(mustVisitKeywords));
        copy.setNaturalLanguageRequirement(source.getNaturalLanguageRequirement());
        copy.setDeparturePlaceName(source.getDeparturePlaceName());
        copy.setDepartureLatitude(source.getDepartureLatitude());
        copy.setDepartureLongitude(source.getDepartureLongitude());
        return copy;
    }

    private List<String> selectDailyMustVisitKeywords(List<Poi> remainingCandidates,
                                                      List<String> remainingMustVisit,
                                                      int dailyMaxStops) {
        if (remainingMustVisit == null || remainingMustVisit.isEmpty() || dailyMaxStops <= 0) {
            return List.of();
        }
        List<String> selected = new ArrayList<>();
        for (String keyword : remainingMustVisit) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            if (matchesAnyMustVisitCandidate(remainingCandidates, keyword)) {
                selected.add(keyword);
            }
            if (selected.size() >= dailyMaxStops) {
                break;
            }
        }
        return selected;
    }

    private String normalizeRecallStrategy(String recallStrategy) {
        return StringUtils.hasText(recallStrategy) ? recallStrategy.trim() : RECALL_STRATEGY;
    }

    private String resolveFailReason(RoutePlanningSnapshot snapshot) {
        if (snapshot == null) {
            return "当前时间窗与约束条件下未找到可执行路线。";
        }
        if (StringUtils.hasText(snapshot.failureReason())) {
            return snapshot.failureReason();
        }
        return "当前时间窗与约束条件下未找到可执行路线。";
    }

    private String diagnosePlanningFailure(List<Poi> candidates,
                                           GenerateReqDTO request,
                                           int maxStops,
                                           int tripDayCount) {
        List<String> mustVisitKeywords = normalizeMustVisitKeywords(request);
        if (!mustVisitKeywords.isEmpty()) {
            List<String> missingInCandidates = mustVisitKeywords.stream()
                    .filter(keyword -> !matchesAnyMustVisitCandidate(candidates, keyword))
                    .toList();
            if (!missingInCandidates.isEmpty()) {
                return "以下必去点未召回到候选池：" + String.join("、", missingInCandidates) + "。";
            }
            if (tripDayCount <= 1 && mustVisitKeywords.size() > Math.max(0, maxStops)) {
                return "必去点数量超过单日可安排点位上限，请增加天数或减少必去点。";
            }
            if (request != null && request.getTotalBudget() != null) {
                double minRequiredBudget = estimateMinimumMustVisitCost(candidates, mustVisitKeywords);
                if (minRequiredBudget > request.getTotalBudget() + 1e-6) {
                    return "总预算不足以覆盖必去点，至少需要约 " + Math.round(minRequiredBudget) + " 元。";
                }
            }
            return "当前时间窗、营业时间或预算约束下，无法同时覆盖必去点：" + String.join("、", mustVisitKeywords) + "。";
        }
        if (request != null && request.getTotalBudget() != null) {
            return "当前预算与时间窗约束下未找到可执行路线，请适当提高预算或放宽时间安排。";
        }
        return "当前时间窗与约束条件下未找到可执行路线。";
    }

    private boolean matchesAnyMustVisitCandidate(List<Poi> candidates, String keyword) {
        if (!StringUtils.hasText(keyword) || candidates == null || candidates.isEmpty()) {
            return false;
        }
        MustVisitRequirement requirement = buildKeywordRequirement(keyword);
        return candidates.stream().anyMatch(poi -> matchesRequirement(poi, requirement));
    }

    private boolean pathContainsKeyword(List<Poi> path, String keyword) {
        if (!StringUtils.hasText(keyword) || path == null || path.isEmpty()) {
            return false;
        }
        MustVisitRequirement requirement = buildKeywordRequirement(keyword);
        return path.stream().anyMatch(poi -> matchesRequirement(poi, requirement));
    }

    private double estimateMinimumMustVisitCost(List<Poi> candidates, List<String> mustVisitKeywords) {
        if (candidates == null || candidates.isEmpty() || mustVisitKeywords == null || mustVisitKeywords.isEmpty()) {
            return 0D;
        }
        double total = 0D;
        Set<Long> usedPoiIds = new LinkedHashSet<>();
        for (String keyword : mustVisitKeywords) {
            Poi cheapest = candidates.stream()
                    .filter(poi -> matchesMustVisit(poi, List.of(keyword)))
                    .filter(poi -> poi.getId() == null || !usedPoiIds.contains(poi.getId()))
                    .min((left, right) -> Double.compare(resolvePoiCost(left), resolvePoiCost(right)))
                    .orElse(null);
            if (cheapest == null) {
                continue;
            }
            if (cheapest.getId() != null) {
                usedPoiIds.add(cheapest.getId());
            }
            total += resolvePoiCost(cheapest);
        }
        return total;
    }

    private double resolvePoiCost(Poi poi) {
        if (poi == null || poi.getAvgCost() == null) {
            return 0D;
        }
        return Math.max(0D, poi.getAvgCost().doubleValue());
    }

    private List<Poi> mergeWithMustVisitCandidates(List<Poi> recalledCandidates,
                                                   List<Poi> filteredCandidates,
                                                   GenerateReqDTO normalizedRequest) {
        List<String> mustVisitKeywords = normalizeMustVisitKeywords(normalizedRequest);
        if (mustVisitKeywords.isEmpty()) {
            return recalledCandidates == null ? Collections.emptyList() : recalledCandidates;
        }

        List<Poi> merged = recalledCandidates == null ? new ArrayList<>() : new ArrayList<>(recalledCandidates);
        Set<Long> existingIds = new LinkedHashSet<>();
        for (Poi poi : merged) {
            if (poi != null && poi.getId() != null) {
                existingIds.add(poi.getId());
            }
        }

        if (filteredCandidates != null) {
            for (Poi poi : filteredCandidates) {
                if (!matchesMustVisit(poi, mustVisitKeywords)) {
                    continue;
                }
                Long poiId = poi.getId();
                if (poiId == null || existingIds.add(poiId)) {
                    merged.add(poi);
                }
            }
        }

        return merged;
    }

    private List<String> normalizeMustVisitKeywords(GenerateReqDTO request) {
        return buildMustVisitRequirements(request).stream()
                .map(MustVisitRequirement::displayName)
                .toList();
    }

    private List<MustVisitRequirement> buildMustVisitRequirements(GenerateReqDTO request) {
        if (request == null || request.getMustVisitPoiNames() == null || request.getMustVisitPoiNames().isEmpty()) {
            return Collections.emptyList();
        }
        List<MustVisitRequirement> requirements = new ArrayList<>();
        for (String keyword : request.getMustVisitPoiNames()) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            requirements.add(buildKeywordRequirement(keyword));
        }
        return requirements.stream()
                .filter(requirement -> !requirement.variants().isEmpty())
                .toList();
    }

    private boolean matchesMustVisit(Poi poi, List<String> mustVisitKeywords) {
        if (poi == null || !StringUtils.hasText(poi.getName()) || mustVisitKeywords == null || mustVisitKeywords.isEmpty()) {
            return false;
        }
        return mustVisitKeywords.stream()
                .filter(StringUtils::hasText)
                .map(this::buildKeywordRequirement)
                .filter(requirement -> !requirement.variants().isEmpty())
                .anyMatch(requirement -> matchesRequirement(poi, requirement));
    }

    private MustVisitRequirement buildKeywordRequirement(String keyword) {
        String displayName = keyword == null ? "" : keyword.trim();
        String comparable = normalizeComparableText(displayName);
        LinkedHashSet<String> variants = new LinkedHashSet<>();
        if (StringUtils.hasText(comparable)) {
            variants.add(comparable);
            extractAsciiTokens(comparable).forEach(variants::add);
        }
        return new MustVisitRequirement(displayName, variants);
    }

    private boolean matchesRequirement(Poi poi, MustVisitRequirement requirement) {
        if (poi == null || requirement == null || !StringUtils.hasText(poi.getName())) {
            return false;
        }
        String poiComparable = normalizeComparableText(poi.getName());
        if (!StringUtils.hasText(poiComparable)) {
            return false;
        }
        return requirement.variants().stream()
                .filter(StringUtils::hasText)
                .anyMatch(variant -> poiComparable.contains(variant) || variant.contains(poiComparable));
    }

    private String normalizeComparableText(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        String lowered = raw.trim().toLowerCase(Locale.ROOT);
        return lowered.replaceAll("[\\s\\p{Punct}·•，。、“”‘’（）()【】\\-_/]+", "");
    }

    private List<String> extractAsciiTokens(String raw) {
        if (!StringUtils.hasText(raw)) {
            return Collections.emptyList();
        }
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        Matcher matcher = ASCII_TOKEN_PATTERN.matcher(raw);
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return new ArrayList<>(tokens);
    }

    @FunctionalInterface
    public interface RouteItineraryBuilder {
        ItineraryVO build(RoutePlanningSnapshot snapshot);
    }

    @FunctionalInterface
    public interface AiItineraryDecorator {
        ItineraryVO decorate(GenerateReqDTO normalizedRequest, ItineraryVO baseItinerary);
    }

    public record RoutePlanningSnapshot(GenerateReqDTO normalizedRequest,
                                        int rawCandidateCount,
                                        int filteredCandidateCount,
                                        int finalCandidateCount,
                                        int maxStops,
                                        List<ItineraryRouteOptimizer.RouteOption> rankedRoutes,
                                        String recallStrategy,
                                        String failureReason) {

        public int generatedRouteCount() {
            return rankedRoutes == null ? 0 : rankedRoutes.size();
        }
    }

    public record PlanningResult(GenerateReqDTO normalizedRequest,
                                 ItineraryVO itinerary,
                                 int rawCandidateCount,
                                 int filteredCandidateCount,
                                 int finalCandidateCount,
                                 int maxStops,
                                 int generatedRouteCount,
                                 int displayedOptionCount,
                                 String algorithmVersion,
                                 String recallStrategy,
                                 boolean success,
                                 String failReason,
                                 LocalDateTime planningStartedAt) {
    }

    private record MustVisitRequirement(String displayName, Set<String> variants) {
    }

    private record RecallStage(List<Poi> rawCandidates,
                               List<Poi> filteredCandidates,
                               List<Poi> recalledCandidates,
                               String recallStrategy) {
    }
}

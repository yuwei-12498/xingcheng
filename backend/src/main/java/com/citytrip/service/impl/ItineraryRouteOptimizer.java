package com.citytrip.service.impl;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.PoiService;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.domain.planning.CandidatePreparationService;
import com.citytrip.service.domain.planning.ItineraryRequestNormalizer;
import com.citytrip.service.domain.scoring.AlgorithmWeightProvider;
import com.citytrip.service.domain.scoring.AlgorithmWeightsSnapshot;
import com.citytrip.service.domain.scoring.DefaultPoiScoringStrategy;
import com.citytrip.service.domain.scoring.DynamicAlgorithmWeightProvider;
import com.citytrip.service.domain.routing.LegacyRouteSearchEngine;
import com.citytrip.service.domain.routing.RouteSearchEngine;
import com.citytrip.service.geo.CityResolverService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
public class ItineraryRouteOptimizer {

    public static final int DEFAULT_START_MINUTE = 9 * 60;
    public static final int DEFAULT_END_MINUTE = 18 * 60;
    public static final String DEFAULT_CITY_NAME = "成都";

    private static final int CANDIDATE_LIMIT = 18;
    private static final int EXACT_DP_THRESHOLD = 15;
    private static final int BEAM_WIDTH = 80;
    private static final Pattern ASCII_TOKEN_PATTERN = Pattern.compile("[a-z0-9]{2,}");

    private static final List<String> SOLO_COMPANION_KEYWORDS = List.of(
            "solo", "single", "alone", "\u72ec\u81ea", "\u5355\u4eba", "\u4e00\u4eba", "\u4e2a\u4eba"
    );
    private static final List<String> GROUP_COMPANION_KEYWORDS = List.of(
            "group", "multi", "friends", "friend", "family", "families", "team", "classmate",
            "couple", "partner", "kids", "parent", "child", "children",
            "\u591a\u4eba", "\u7ed3\u4f34", "\u670b\u53cb", "\u597d\u53cb", "\u5bb6\u5ead", "\u5bb6\u4eba",
            "\u4eb2\u5b50", "\u56e2\u961f", "\u56e2\u5efa", "\u540c\u5b66", "\u60c5\u4fa3", "\u4f34\u4fa3"
    );
    private static final List<String> GROUP_FRIENDLY_POI_KEYWORDS = List.of(
            "group", "friends", "friend", "family", "families", "team", "couple", "kids",
            "parent", "child", "children", "social", "interactive",
            "\u591a\u4eba", "\u7ed3\u4f34", "\u670b\u53cb", "\u597d\u53cb", "\u5bb6\u5ead", "\u5bb6\u4eba",
            "\u4eb2\u5b50", "\u56e2\u961f", "\u56e2\u5efa", "\u540c\u5b66", "\u60c5\u4fa3", "\u4e92\u52a8",
            "\u805a\u4f1a", "\u5546\u5708", "\u8857\u533a", "\u7f8e\u98df", "\u516c\u56ed", "\u535a\u7269\u9986"
    );
    private static final List<String> SOLO_FOCUSED_POI_KEYWORDS = List.of(
            "solo", "single", "alone", "quiet", "meditation", "study",
            "\u72ec\u81ea", "\u5355\u4eba", "\u4e00\u4eba", "\u4e2a\u4eba", "\u5b89\u9759", "\u9759\u4fee",
            "\u51a5\u60f3", "\u81ea\u4e60"
    );

    private final PoiService poiService;
    private final TravelTimeService travelTimeService;
    private final ItineraryRequestNormalizer requestNormalizer;
    private final DefaultPoiScoringStrategy scoringStrategy;
    private final CandidatePreparationService candidatePreparationService;
    private final RouteSearchEngine routeSearchEngine;
    private final AlgorithmWeightProvider weightProvider;
    @Autowired(required = false)
    private GeoSearchService geoSearchService;
    @Autowired(required = false)
    private CityResolverService cityResolverService;

    public ItineraryRouteOptimizer(PoiService poiService, TravelTimeService travelTimeService) {
        this(poiService, travelTimeService, new ItineraryRequestNormalizer(), null, null, null);
    }

    public ItineraryRouteOptimizer(PoiService poiService,
                                   TravelTimeService travelTimeService,
                                   ItineraryRequestNormalizer requestNormalizer,
                                   DefaultPoiScoringStrategy scoringStrategy,
                                   CandidatePreparationService candidatePreparationService) {
        this(poiService, travelTimeService, requestNormalizer, scoringStrategy, candidatePreparationService, null);
    }

    @Autowired
    public ItineraryRouteOptimizer(PoiService poiService,
                                   TravelTimeService travelTimeService,
                                   ItineraryRequestNormalizer requestNormalizer,
                                   DefaultPoiScoringStrategy scoringStrategy,
                                   CandidatePreparationService candidatePreparationService,
                                   AlgorithmWeightProvider weightProvider) {
        this.poiService = poiService;
        this.travelTimeService = travelTimeService;
        this.requestNormalizer = requestNormalizer == null ? new ItineraryRequestNormalizer() : requestNormalizer;
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
        this.scoringStrategy = scoringStrategy == null
                ? new DefaultPoiScoringStrategy(this.requestNormalizer, this.weightProvider)
                : scoringStrategy;
        this.candidatePreparationService = candidatePreparationService == null
                ? new CandidatePreparationService(poiService, this.requestNormalizer, this.scoringStrategy)
                : candidatePreparationService;
        this.routeSearchEngine = new LegacyRouteSearchEngine(this::rankRoutesInternal);
    }

    public GenerateReqDTO normalizeRequest(GenerateReqDTO req) {
        return requestNormalizer.normalize(req);
    }

    public List<Poi> prepareCandidates(List<Poi> source, GenerateReqDTO req, boolean applyLimit) {
        return candidatePreparationService.prepareCandidates(source, req, applyLimit);
    }

    public RouteOption bestRoute(List<Poi> candidates, GenerateReqDTO req, int maxStops) {
        List<RouteOption> ranked = rankRoutes(candidates, req, maxStops);
        return ranked.isEmpty() ? new RouteOption(Collections.emptyList(), "", 0D) : ranked.get(0);
    }

    public List<RouteOption> rankRoutes(List<Poi> candidates, GenerateReqDTO req, int maxStops) {
        return routeSearchEngine.rankRoutes(candidates, req, maxStops);
    }

    private List<RouteOption> rankRoutesInternal(List<Poi> candidates, GenerateReqDTO req, int maxStops) {
        if (candidates == null || candidates.isEmpty() || maxStops <= 0) {
            return Collections.emptyList();
        }

        GenerateReqDTO normalized = normalizeRequest(req);
        HardConstraintContext constraintContext = buildHardConstraintContext(normalized, candidates, maxStops);
        if (constraintContext.hasUnmatchedMustVisit()) {
            return Collections.emptyList();
        }
        List<Poi> searchCandidates = candidates;
        if (candidates.size() > Long.SIZE) {
            searchCandidates = capCandidatesForBeamMask(candidates, normalized);
        }
        if (searchCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        constraintContext = buildHardConstraintContext(normalized, searchCandidates, maxStops);

        int normalizedMaxStops = Math.min(maxStops, searchCandidates.size());
        int startMinute = parseTimeMinutes(normalized.getStartTime(), DEFAULT_START_MINUTE);
        int endMinute = parseTimeMinutes(normalized.getEndTime(), DEFAULT_END_MINUTE);
        StartAccessProfile[] startAccessProfiles = buildStartAccessProfiles(searchCandidates, normalized);
        int[][] travelMatrix = buildTravelTimeMatrix(searchCandidates);

        if (searchCandidates.size() <= EXACT_DP_THRESHOLD) {
            return prioritizeMustVisitRoutes(
                    rankRoutesWithParetoDp(searchCandidates, normalized, normalizedMaxStops, startMinute, endMinute, travelMatrix, startAccessProfiles, constraintContext),
                    normalized
            );
        }
        return prioritizeMustVisitRoutes(
                rankRoutesWithBeamSearch(searchCandidates, normalized, normalizedMaxStops, startMinute, endMinute, travelMatrix, startAccessProfiles, constraintContext),
                normalized
        );
    }

    public double replacementScore(Poi targetPoi, Poi candidate) {
        if (targetPoi == null || candidate == null) {
            return Double.NEGATIVE_INFINITY;
        }
        double score = candidate.getTempScore() == null ? 0 : candidate.getTempScore();
        if (Objects.equals(targetPoi.getCategory(), candidate.getCategory())) {
            score += 6.0;
        }
        if (Objects.equals(targetPoi.getDistrict(), candidate.getDistrict())) {
            score += 4.0;
        }
        score += Math.max(0, 20 - travelTimeService.estimateTravelTimeMinutes(targetPoi, candidate)) / 3.0;
        score -= resolveBaseCrowdPenalty(candidate);
        return score;
    }

    public LocalDate resolveTripDate(GenerateReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getTripDate())) {
            return LocalDate.now();
        }
        try {
            return LocalDate.parse(req.getTripDate());
        } catch (DateTimeParseException ex) {
            return LocalDate.now();
        }
    }

    public int parseTimeMinutes(String timeStr, int defaultMinutes) {
        if (!StringUtils.hasText(timeStr) || !timeStr.contains(":")) {
            return defaultMinutes;
        }
        try {
            String[] parts = timeStr.split(":");
            if (parts.length < 2) {
                return defaultMinutes;
            }
            long hours = Long.parseLong(parts[0].trim());
            long minutes = Long.parseLong(parts[1].trim());
            long total = Math.addExact(Math.multiplyExact(hours, 60L), minutes);
            return clampToInt(total);
        } catch (RuntimeException ex) {
            return defaultMinutes;
        }
    }

    public int resolveOpenMinute(Poi poi, int defaultMinute) {
        if (poi == null) {
            return defaultMinute;
        }
        LocalTime openTime = poi.getOpenTime();
        return openTime == null ? defaultMinute : openTime.getHour() * 60 + openTime.getMinute();
    }

    public int resolveCloseMinute(Poi poi, int defaultMinute) {
        if (poi == null) {
            return defaultMinute;
        }
        LocalTime closeTime = poi.getCloseTime();
        return closeTime == null ? defaultMinute : closeTime.getHour() * 60 + closeTime.getMinute();
    }

    public String formatTime(int totalMinutes) {
        int normalizedMinutes = Math.floorMod(totalMinutes, 24 * 60);
        int hour = normalizedMinutes / 60;
        int minute = normalizedMinutes % 60;
        return String.format("%02d:%02d", hour, minute);
    }

    public String signature(Collection<Poi> pois) {
        if (pois == null || pois.isEmpty()) {
            return "";
        }
        return pois.stream()
                .filter(Objects::nonNull)
                .map(Poi::getId)
                .map(String::valueOf)
                .collect(Collectors.joining("-"));
    }

    private List<RouteOption> rankRoutesWithParetoDp(List<Poi> candidates,
                                                     GenerateReqDTO req,
                                                     int maxStops,
                                                     int startMinute,
                                                     int endMinute,
                                                     int[][] travelMatrix,
                                                     StartAccessProfile[] startAccessProfiles,
                                                     HardConstraintContext constraintContext) {
        ArrayDeque<DpLabel> queue = new ArrayDeque<>();
        Map<Long, List<DpLabel>> paretoFrontier = new HashMap<>();

        DpLabel seed = DpLabel.seed(startMinute);
        queue.offer(seed);
        paretoFrontier.computeIfAbsent(dpBucketKey(seed.mask, seed.lastIndex), key -> new ArrayList<>()).add(seed);

        while (!queue.isEmpty()) {
            DpLabel current = queue.poll();
            if (current.stopCount >= maxStops) {
                continue;
            }
            for (int nextIndex = 0; nextIndex < candidates.size(); nextIndex++) {
                if ((current.mask & (1 << nextIndex)) != 0) {
                    continue;
                }
                DpLabel next = expandDp(current, nextIndex, candidates, req, startMinute, endMinute, travelMatrix, startAccessProfiles, constraintContext);
                if (next == null) {
                    continue;
                }
                List<DpLabel> bucket = paretoFrontier.computeIfAbsent(
                        dpBucketKey(next.mask, next.lastIndex),
                        key -> new ArrayList<>()
                );
                if (insertIfNonDominated(bucket, next)) {
                    queue.offer(next);
                }
            }
        }

        List<DpLabel> finalStates = paretoFrontier.values().stream()
                .flatMap(Collection::stream)
                .filter(label -> label.stopCount > 0)
                .filter(label -> !constraintContext.requiresFullMustVisitCoverage()
                        || label.mustVisitMask == constraintContext.requiredMustVisitMask())
                .sorted(this::compareDpLabel)
                .toList();

        return deduplicateRouteOptions(finalStates.stream()
                .map(label -> toRouteOption(label, candidates))
                .toList());
    }

    private List<RouteOption> rankRoutesWithBeamSearch(List<Poi> candidates,
                                                       GenerateReqDTO req,
                                                       int maxStops,
                                                       int startMinute,
                                                       int endMinute,
                                                       int[][] travelMatrix,
                                                       StartAccessProfile[] startAccessProfiles,
                                                       HardConstraintContext constraintContext) {
        List<SearchState> beam = List.of(SearchState.seed(startMinute));
        List<SearchState> completed = new ArrayList<>();

        for (int depth = 0; depth < maxStops; depth++) {
            Map<String, SearchState> nextLevel = new HashMap<>();
            for (SearchState state : beam) {
                for (int nextIndex = 0; nextIndex < candidates.size(); nextIndex++) {
                    if ((state.mask & (1L << nextIndex)) != 0L) {
                        continue;
                    }
                    SearchState next = expandBeam(state, nextIndex, candidates, req, startMinute, endMinute, travelMatrix, startAccessProfiles, constraintContext);
                    if (next == null) {
                        continue;
                    }
                    keepBetter(nextLevel, next);
                    completed.add(next);
                }
            }
            if (nextLevel.isEmpty()) {
                break;
            }
            beam = nextLevel.values().stream()
                    .sorted(this::compareBeamState)
                    .limit(BEAM_WIDTH)
                    .toList();
        }

        List<RouteOption> ranked = completed.stream()
                .filter(state -> !state.path.isEmpty())
                .filter(state -> !constraintContext.requiresFullMustVisitCoverage()
                        || state.mustVisitMask == constraintContext.requiredMustVisitMask())
                .sorted(this::compareBeamState)
                .map(state -> toRouteOption(state, candidates))
                .toList();
        return deduplicateRouteOptions(ranked);
    }

    private int[][] buildTravelTimeMatrix(List<Poi> candidates) {
        int size = candidates.size();
        int[][] matrix = new int[size][size];
        for (int i = 0; i < size; i++) {
            matrix[i][i] = 0;
            for (int j = i + 1; j < size; j++) {
                int minutes = Math.max(0, travelTimeService.estimateTravelTimeMinutes(candidates.get(i), candidates.get(j)));
                matrix[i][j] = minutes;
                matrix[j][i] = Math.max(0, travelTimeService.estimateTravelTimeMinutes(candidates.get(j), candidates.get(i)));
            }
        }
        return matrix;
    }

    private StartAccessProfile[] buildStartAccessProfiles(List<Poi> candidates, GenerateReqDTO req) {
        StartAccessProfile[] profiles = new StartAccessProfile[candidates.size()];
        Poi departurePoi = buildDeparturePoi(req);
        for (int i = 0; i < candidates.size(); i++) {
            if (departurePoi == null) {
                profiles[i] = new StartAccessProfile(0, null, null, 0D);
                continue;
            }
            TravelTimeService.TravelLegEstimate estimate = travelTimeService.estimateTravelLeg(departurePoi, candidates.get(i));
            int minutes = estimate == null
                    ? Math.max(0, travelTimeService.estimateTravelTimeMinutes(departurePoi, candidates.get(i)))
                    : Math.max(0, estimate.estimatedMinutes());
            BigDecimal distanceKm = estimate == null ? null : estimate.estimatedDistanceKm();
            String transportMode = estimate == null ? null : estimate.transportMode();
            double accessPenalty = resolveStartAccessPenalty(req, minutes, distanceKm, transportMode);
            profiles[i] = new StartAccessProfile(minutes, distanceKm, transportMode, accessPenalty);
        }
        return profiles;
    }

    private DpLabel expandDp(DpLabel state,
                             int nextIndex,
                             List<Poi> candidates,
                             GenerateReqDTO req,
                             int startMinute,
                             int endMinute,
                             int[][] travelMatrix,
                             StartAccessProfile[] startAccessProfiles,
                             HardConstraintContext constraintContext) {
        Poi nextPoi = candidates.get(nextIndex);
        StartAccessProfile startAccessProfile = resolveStartAccessProfile(startAccessProfiles, nextIndex);
        int travelTime = Math.max(0, state.lastIndex < 0 ? startAccessProfile.travelMinutes() : travelMatrix[state.lastIndex][nextIndex]);
        int arrival = safeAddMinutes(state.currentMinute, travelTime);
        int visitStart = Math.max(arrival, resolveOpenMinute(nextPoi, startMinute));
        int waitTime = Math.max(0, visitStart - arrival);
        int stayDuration = normalizeStayDuration(nextPoi.getStayDuration());
        int visitEnd = safeAddMinutes(visitStart, stayDuration);
        if (visitEnd > resolveCloseMinute(nextPoi, endMinute) || visitEnd > endMinute) {
            return null;
        }
        double nextCost = state.totalCost + resolvePoiCost(nextPoi);
        if (constraintContext.exceedsBudget(nextCost)) {
            return null;
        }

        AlgorithmWeightsSnapshot weights = weightProvider.current();
        double utility = state.utility
                + resolveRouteValue(req, nextPoi) * weights.routeScoreWeight()
                - travelTime * weights.routeTravelPenaltyWeight()
                - waitTime * weights.routeWaitPenaltyWeight()
                - resolveVisitCrowdPenalty(nextPoi, req, visitStart) * weights.routeCrowdPenaltyWeight();
        if (state.lastIndex < 0) {
            utility -= startAccessProfile.accessPenalty();
        }

        return new DpLabel(
                state.mask | (1 << nextIndex),
                nextIndex,
                visitEnd,
                nextCost,
                utility,
                state.stopCount + 1,
                state.mustVisitMask | constraintContext.poiMustVisitMasks()[nextIndex],
                state
        );
    }

    private SearchState expandBeam(SearchState state,
                                   int nextIndex,
                                   List<Poi> candidates,
                                   GenerateReqDTO req,
                                   int startMinute,
                                   int endMinute,
                                   int[][] travelMatrix,
                                   StartAccessProfile[] startAccessProfiles,
                                   HardConstraintContext constraintContext) {
        Poi nextPoi = candidates.get(nextIndex);
        StartAccessProfile startAccessProfile = resolveStartAccessProfile(startAccessProfiles, nextIndex);
        int travelTime = Math.max(0, state.lastIndex < 0 ? startAccessProfile.travelMinutes() : travelMatrix[state.lastIndex][nextIndex]);
        int arrival = safeAddMinutes(state.currentMinute, travelTime);
        int visitStart = Math.max(arrival, resolveOpenMinute(nextPoi, startMinute));
        int waitTime = Math.max(0, visitStart - arrival);
        int visitEnd = safeAddMinutes(visitStart, normalizeStayDuration(nextPoi.getStayDuration()));
        if (visitEnd > resolveCloseMinute(nextPoi, endMinute) || visitEnd > endMinute) {
            return null;
        }
        double nextCost = state.totalCost + resolvePoiCost(nextPoi);
        if (constraintContext.exceedsBudget(nextCost)) {
            return null;
        }
        List<Integer> path = new ArrayList<>(state.path);
        path.add(nextIndex);
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        double utility = state.utility
                + resolveRouteValue(req, nextPoi) * weights.routeScoreWeight()
                - travelTime * weights.routeTravelPenaltyWeight()
                - waitTime * weights.routeWaitPenaltyWeight()
                - resolveVisitCrowdPenalty(nextPoi, req, visitStart) * weights.routeCrowdPenaltyWeight();
        if (state.lastIndex < 0) {
            utility -= startAccessProfile.accessPenalty();
        }
        return new SearchState(
                state.mask | (1L << nextIndex),
                nextIndex,
                visitEnd,
                nextCost,
                utility,
                state.mustVisitMask | constraintContext.poiMustVisitMasks()[nextIndex],
                path
        );
    }

    private StartAccessProfile resolveStartAccessProfile(StartAccessProfile[] profiles, int index) {
        if (profiles == null || index < 0 || index >= profiles.length || profiles[index] == null) {
            return new StartAccessProfile(0, null, null, 0D);
        }
        return profiles[index];
    }

    private double resolveStartAccessPenalty(GenerateReqDTO req,
                                             int minutes,
                                             BigDecimal distanceKm,
                                             String transportMode) {
        if (!hasDepartureCoordinate(req)) {
            return 0D;
        }
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        double normalizedDistanceKm = distanceKm == null ? 0D : Math.max(0D, distanceKm.doubleValue());
        double distancePenalty = normalizedDistanceKm * weights.firstLegDistancePenaltyWeight();
        double distanceThresholdPenalty = resolveStartDistanceThresholdPenalty(normalizedDistanceKm);
        double timeThresholdPenalty = resolveStartTimeThresholdPenalty(minutes);
        double transferPenalty = resolveStartModePenalty(req, transportMode);
        return Math.max(0, minutes) * weights.firstLegTimePenaltyWeight()
                + distancePenalty
                + distanceThresholdPenalty
                + timeThresholdPenalty
                + transferPenalty;
    }

    private double resolveStartModePenalty(GenerateReqDTO req, String transportMode) {
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        if (!StringUtils.hasText(transportMode)) {
            return weights.startModeUnknownPenalty();
        }
        String normalizedMode = transportMode.trim().toLowerCase(Locale.ROOT);
        if (normalizedMode.contains("步行") || normalizedMode.contains("walk")) {
            return "low".equalsIgnoreCase(req == null ? null : req.getWalkingLevel())
                    ? weights.startModeLowWalkingPenalty()
                    : 0D;
        }
        if (normalizedMode.contains("骑行") || normalizedMode.contains("bike") || normalizedMode.contains("cycle")) {
            return weights.startModeBikePenalty();
        }
        if (normalizedMode.contains("地铁") || normalizedMode.contains("metro") || normalizedMode.contains("subway")) {
            return weights.firstLegTransferPenaltyWeight();
        }
        if (normalizedMode.contains("公交") || normalizedMode.contains("bus") || normalizedMode.contains("transit")) {
            return weights.firstLegTransferPenaltyWeight() + weights.startModeBusExtraPenalty();
        }
        if (normalizedMode.contains("打车") || normalizedMode.contains("taxi") || normalizedMode.contains("drive")) {
            return weights.firstLegTransferPenaltyWeight() + weights.startModeTaxiExtraPenalty();
        }
        return weights.startModeOtherPenalty();
    }

    private double resolveStartDistanceThresholdPenalty(double distanceKm) {
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        double penalty = 0D;
        if (distanceKm > weights.startDistanceSoftThresholdKm()) {
            penalty += (distanceKm - weights.startDistanceSoftThresholdKm()) * weights.startDistanceSoftThresholdWeight();
        }
        if (distanceKm > weights.startDistanceMediumThresholdKm()) {
            penalty += weights.startDistanceMediumBasePenalty()
                    + (distanceKm - weights.startDistanceMediumThresholdKm()) * weights.startDistanceMediumWeight();
        }
        if (distanceKm > weights.startDistanceHardThresholdKm()) {
            penalty += weights.startDistanceHardBasePenalty()
                    + (distanceKm - weights.startDistanceHardThresholdKm()) * weights.startDistanceHardWeight();
        }
        return penalty;
    }

    private double resolveStartTimeThresholdPenalty(int minutes) {
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        int safeMinutes = Math.max(0, minutes);
        double penalty = 0D;
        if (safeMinutes > weights.startTimeSoftThresholdMinutes()) {
            penalty += (safeMinutes - weights.startTimeSoftThresholdMinutes()) * weights.startTimeSoftThresholdWeight();
        }
        if (safeMinutes > weights.startTimeMediumThresholdMinutes()) {
            penalty += weights.startTimeMediumBasePenalty()
                    + (safeMinutes - weights.startTimeMediumThresholdMinutes()) * weights.startTimeMediumWeight();
        }
        if (safeMinutes > weights.startTimeHardThresholdMinutes()) {
            penalty += weights.startTimeHardBasePenalty()
                    + (safeMinutes - weights.startTimeHardThresholdMinutes()) * weights.startTimeHardWeight();
        }
        return penalty;
    }

    private boolean hasDepartureCoordinate(GenerateReqDTO req) {
        return req != null
                && req.getDepartureLatitude() != null
                && req.getDepartureLongitude() != null
                && Math.abs(req.getDepartureLatitude()) <= 90D
                && Math.abs(req.getDepartureLongitude()) <= 180D;
    }

    private boolean insertIfNonDominated(List<DpLabel> bucket, DpLabel candidate) {
        for (DpLabel existing : bucket) {
            if (dominates(existing, candidate)) {
                return false;
            }
        }
        bucket.removeIf(existing -> dominates(candidate, existing));
        bucket.add(candidate);
        return true;
    }

    private boolean dominates(DpLabel left, DpLabel right) {
        return left.currentMinute <= right.currentMinute
                && left.totalCost <= right.totalCost + 1e-6
                && left.utility >= right.utility - 1e-6;
    }

    private long dpBucketKey(int mask, int lastIndex) {
        return (((long) mask) << 6) | (lastIndex + 1L);
    }

    private void keepBetter(Map<String, SearchState> level, SearchState candidate) {
        String key = candidate.mask + "-" + candidate.lastIndex;
        SearchState existing = level.get(key);
        if (existing == null || compareBeamState(candidate, existing) < 0) {
            level.put(key, candidate);
        }
    }

    private int compareDpLabel(DpLabel left, DpLabel right) {
        int byMustVisit = Integer.compare(Integer.bitCount(right.mustVisitMask), Integer.bitCount(left.mustVisitMask));
        if (byMustVisit != 0) {
            return byMustVisit;
        }
        int byUtility = Double.compare(right.utility, left.utility);
        if (byUtility != 0) {
            return byUtility;
        }
        int byStops = Integer.compare(right.stopCount, left.stopCount);
        if (byStops != 0) {
            return byStops;
        }
        int byTime = Integer.compare(left.currentMinute, right.currentMinute);
        if (byTime != 0) {
            return byTime;
        }
        return Double.compare(left.totalCost, right.totalCost);
    }

    private int compareBeamState(SearchState left, SearchState right) {
        int byMustVisit = Integer.compare(Integer.bitCount(right.mustVisitMask), Integer.bitCount(left.mustVisitMask));
        if (byMustVisit != 0) {
            return byMustVisit;
        }
        int byUtility = Double.compare(right.utility, left.utility);
        if (byUtility != 0) {
            return byUtility;
        }
        int byStops = Integer.compare(right.path.size(), left.path.size());
        if (byStops != 0) {
            return byStops;
        }
        int byTime = Integer.compare(left.currentMinute, right.currentMinute);
        if (byTime != 0) {
            return byTime;
        }
        return Double.compare(left.totalCost, right.totalCost);
    }

    private RouteOption toRouteOption(DpLabel label, List<Poi> candidates) {
        List<Poi> path = reconstructDpPath(label, candidates);
        return new RouteOption(path, signature(path), label.utility);
    }

    private RouteOption toRouteOption(SearchState state, List<Poi> candidates) {
        List<Poi> path = state.path.stream().map(candidates::get).collect(Collectors.toList());
        return new RouteOption(path, signature(path), state.utility);
    }

    private List<Poi> reconstructDpPath(DpLabel label, List<Poi> candidates) {
        ArrayDeque<Poi> stack = new ArrayDeque<>();
        DpLabel cursor = label;
        while (cursor != null && cursor.lastIndex >= 0) {
            stack.push(candidates.get(cursor.lastIndex));
            cursor = cursor.prev;
        }
        return new ArrayList<>(stack);
    }

    private List<RouteOption> deduplicateRouteOptions(List<RouteOption> routes) {
        Map<String, RouteOption> ranked = new LinkedHashMap<>();
        for (RouteOption route : routes) {
            if (route == null || route.path() == null || route.path().isEmpty()) {
                continue;
            }
            ranked.putIfAbsent(route.signature(), route);
        }
        return new ArrayList<>(ranked.values());
    }

    private boolean matchesWeatherConstraint(GenerateReqDTO req, Poi poi) {
        return req == null
                || !Boolean.TRUE.equals(req.getIsRainy())
                || Integer.valueOf(1).equals(poi.getIndoor())
                || Integer.valueOf(1).equals(poi.getRainFriendly());
    }

    private boolean matchesWalkingConstraint(GenerateReqDTO req, Poi poi) {
        return walkingRank(req == null ? null : req.getWalkingLevel()) >= walkingRank(poi.getWalkingLevel()) - 1;
    }

    private int walkingRank(String value) {
        if (!StringUtils.hasText(value)) {
            return 2;
        }
        String normalized = normalizeMatchingText(value);
        if (normalized.contains("\u4f4e") || normalized.contains("low") || normalized.contains("light")) {
            return 1;
        }
        if (normalized.contains("\u9ad8") || normalized.contains("high") || normalized.contains("heavy")) {
            return 3;
        }
        return 2;
    }

    private boolean matchesCompanionPreference(GenerateReqDTO req, Poi poi) {
        if (req == null || poi == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeMatchingText(req.getCompanionType());
        String audienceText = buildPoiAudienceText(poi);
        if (!StringUtils.hasText(audienceText)) {
            return false;
        }
        return audienceText.contains(companionType)
                || (isMultiPersonTrip(req) && isGroupFriendlyPoi(poi));
    }

    private boolean isMultiPersonTrip(GenerateReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeMatchingText(req.getCompanionType());
        if (containsAnyNormalized(companionType, SOLO_COMPANION_KEYWORDS)) {
            return false;
        }
        return containsAnyNormalized(companionType, GROUP_COMPANION_KEYWORDS);
    }

    private boolean isGroupFriendlyPoi(Poi poi) {
        if (poi == null) {
            return false;
        }
        String audienceText = buildPoiAudienceText(poi);
        return containsAnyNormalized(audienceText, GROUP_FRIENDLY_POI_KEYWORDS);
    }

    private boolean isSoloFocusedPoi(Poi poi) {
        if (poi == null) {
            return false;
        }
        String audienceText = buildPoiAudienceText(poi);
        return containsAnyNormalized(audienceText, SOLO_FOCUSED_POI_KEYWORDS)
                && !containsAnyNormalized(audienceText, GROUP_FRIENDLY_POI_KEYWORDS);
    }

    private String buildPoiAudienceText(Poi poi) {
        if (poi == null) {
            return "";
        }
        return normalizeMatchingText(String.join(" ",
                nullToEmpty(poi.getSuitableFor()),
                nullToEmpty(poi.getTags()),
                nullToEmpty(poi.getCategory()),
                nullToEmpty(poi.getDescription())));
    }

    private boolean containsAnyNormalized(String normalizedText, Collection<String> keywords) {
        if (!StringUtils.hasText(normalizedText) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeMatchingText(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMatchingText(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double scorePoi(GenerateReqDTO req, Poi poi) {
        return scoringStrategy.scorePoi(req, poi);
    }

    private double resolveRouteValue(GenerateReqDTO req, Poi poi) {
        return poi.getTempScore() != null ? poi.getTempScore() : scorePoi(req, poi);
    }

    private double resolveBaseCrowdPenalty(Poi poi) {
        if (poi == null || poi.getCrowdPenalty() == null) {
            return 0D;
        }
        return Math.max(0D, poi.getCrowdPenalty().doubleValue());
    }

    private double resolveVisitCrowdPenalty(Poi poi, GenerateReqDTO req, int visitStartMinute) {
        double penalty = resolveBaseCrowdPenalty(poi);
        if (penalty <= 0D) {
            return 0D;
        }

        double factor = 1.0D;
        if (visitStartMinute >= 11 * 60 && visitStartMinute < 14 * 60) {
            factor += 0.25D;
        }
        if (visitStartMinute >= 18 * 60 && Integer.valueOf(1).equals(poi.getNightAvailable())) {
            factor += 0.20D;
        }
        LocalDate tripDate = resolveTripDate(req);
        DayOfWeek dayOfWeek = tripDate.getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            factor += 0.15D;
        }
        return penalty * factor;
    }

    private double resolvePoiCost(Poi poi) {
        if (poi == null || poi.getAvgCost() == null) {
            return 0D;
        }
        return Math.max(0D, poi.getAvgCost().doubleValue());
    }

    private int normalizeStayDuration(Integer stayDuration) {
        if (stayDuration == null) {
            return 90;
        }
        return Math.max(0, stayDuration);
    }

    private int safeAddMinutes(int base, int delta) {
        long total = (long) base + delta;
        return clampToInt(total);
    }

    private int clampToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private List<Poi> capCandidatesForBeamMask(List<Poi> candidates, GenerateReqDTO normalizedRequest) {
        if (candidates == null || candidates.size() <= Long.SIZE) {
            return candidates;
        }
        return candidates.stream()
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int byMustVisit = Boolean.compare(
                            matchesMustVisitPoi(normalizedRequest, right),
                            matchesMustVisitPoi(normalizedRequest, left)
                    );
                    if (byMustVisit != 0) {
                        return byMustVisit;
                    }
                    int byScore = Double.compare(resolveRouteValue(normalizedRequest, right), resolveRouteValue(normalizedRequest, left));
                    if (byScore != 0) {
                        return byScore;
                    }
                    BigDecimal rightPriority = right.getPriorityScore() == null ? BigDecimal.ZERO : right.getPriorityScore();
                    BigDecimal leftPriority = left.getPriorityScore() == null ? BigDecimal.ZERO : left.getPriorityScore();
                    return rightPriority.compareTo(leftPriority);
                })
                .limit(Long.SIZE)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    public Poi buildDeparturePoi(GenerateReqDTO req) {
        if (req == null) {
            return null;
        }
        BigDecimal latitude = null;
        BigDecimal longitude = null;
        if (req.getDepartureLatitude() != null && req.getDepartureLongitude() != null) {
            double lat = req.getDepartureLatitude();
            double lng = req.getDepartureLongitude();
            if (Math.abs(lat) <= 90 && Math.abs(lng) <= 180) {
                latitude = BigDecimal.valueOf(lat);
                longitude = BigDecimal.valueOf(lng);
            }
        }
        if (latitude == null || longitude == null) {
            GeoPoint geoPoint = resolveDepartureByGeo(req);
            if (geoPoint != null && geoPoint.valid()) {
                latitude = geoPoint.latitude();
                longitude = geoPoint.longitude();
            }
        }
        if (latitude == null || longitude == null) {
            return null;
        }
        Poi departure = new Poi();
        departure.setId(-1L);
        departure.setName(StringUtils.hasText(req.getDeparturePlaceName()) ? req.getDeparturePlaceName() : "CURRENT_LOCATION");
        departure.setCityCode(req.getCityCode());
        departure.setCityName(req.getCityName());
        departure.setLatitude(latitude);
        departure.setLongitude(longitude);
        departure.setSourceType("departure");
        return departure;
    }

    private GeoPoint resolveDepartureByGeo(GenerateReqDTO req) {
        if (geoSearchService == null) {
            return null;
        }
        String cityName = StringUtils.hasText(req.getCityName()) ? req.getCityName() : DEFAULT_CITY_NAME;
        if (StringUtils.hasText(req.getDeparturePlaceName())) {
            GeoPoint byDepartureName = geoSearchService.geocode(req.getDeparturePlaceName(), cityName).orElse(null);
            if (byDepartureName != null && byDepartureName.valid()) {
                return byDepartureName;
            }
        }
        return geoSearchService.geocode(cityName + "市中心", cityName).orElse(null);
    }

    private String textOrDefault(String value, String defaultValue) {
        return StringUtils.hasText(value) ? value.trim() : defaultValue;
    }

    private HardConstraintContext buildHardConstraintContext(GenerateReqDTO request, List<Poi> candidates, int maxStops) {
        List<MustVisitRequirement> mustVisitRequirements = buildMustVisitRequirements(
                request == null ? null : request.getMustVisitPoiNames()
        );
        int[] poiMustVisitMasks = new int[candidates == null ? 0 : candidates.size()];
        int requiredMask = 0;
        boolean hasUnmatchedMustVisit = false;
        if (candidates != null && !candidates.isEmpty() && !mustVisitRequirements.isEmpty()) {
            int keywordCount = Math.min(mustVisitRequirements.size(), Integer.SIZE - 1);
            for (int keywordIndex = 0; keywordIndex < keywordCount; keywordIndex++) {
                MustVisitRequirement requirement = mustVisitRequirements.get(keywordIndex);
                int bit = 1 << keywordIndex;
                boolean matched = false;
                for (int poiIndex = 0; poiIndex < candidates.size(); poiIndex++) {
                    if (matchesMustVisitRequirement(candidates.get(poiIndex), requirement)) {
                        poiMustVisitMasks[poiIndex] |= bit;
                        matched = true;
                    }
                }
                if (matched) {
                    requiredMask |= bit;
                } else {
                    hasUnmatchedMustVisit = true;
                }
            }
        }
        Double budgetCeiling = resolveBudgetCeiling(request);
        boolean requiresFullMustVisitCoverage = requiredMask != 0
                && Integer.bitCount(requiredMask) <= Math.max(0, maxStops);
        return new HardConstraintContext(
                mustVisitRequirements.stream().map(MustVisitRequirement::displayName).toList(),
                poiMustVisitMasks,
                requiredMask,
                requiresFullMustVisitCoverage,
                budgetCeiling,
                hasUnmatchedMustVisit
        );
    }

    private Double resolveBudgetCeiling(GenerateReqDTO request) {
        if (request == null || request.getTotalBudget() == null) {
            return null;
        }
        double value = request.getTotalBudget();
        return value >= 0D ? value : null;
    }

    private List<String> normalizeMustVisitPoiNames(List<String> mustVisitPoiNames) {
        if (mustVisitPoiNames == null || mustVisitPoiNames.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String item : mustVisitPoiNames) {
            if (StringUtils.hasText(item)) {
                String keyword = item.trim();
                normalized.add(keyword);
                if (keyword.toLowerCase(Locale.ROOT).contains("ifs")) {
                    normalized.add("ifs");
                }
            }
        }
        return new ArrayList<>(normalized);
    }

    private List<MustVisitRequirement> buildMustVisitRequirements(List<String> mustVisitPoiNames) {
        if (mustVisitPoiNames == null || mustVisitPoiNames.isEmpty()) {
            return Collections.emptyList();
        }
        List<MustVisitRequirement> requirements = new ArrayList<>();
        for (String raw : mustVisitPoiNames) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            String displayName = raw.trim();
            String comparable = normalizeComparableText(displayName);
            if (!StringUtils.hasText(comparable)) {
                continue;
            }
            LinkedHashSet<String> variants = new LinkedHashSet<>();
            variants.add(comparable);
            extractAsciiTokens(comparable).forEach(variants::add);
            requirements.add(new MustVisitRequirement(displayName, variants));
        }
        return requirements;
    }

    private List<RouteOption> prioritizeMustVisitRoutes(List<RouteOption> routes, GenerateReqDTO request) {
        if (routes == null || routes.isEmpty()) {
            return Collections.emptyList();
        }
        List<MustVisitRequirement> mustVisitRequirements = buildMustVisitRequirements(
                request == null ? null : request.getMustVisitPoiNames()
        );
        if (mustVisitRequirements.isEmpty()) {
            return routes;
        }
        return routes.stream()
                .sorted((left, right) -> {
                    int rightCoverage = countMustVisitCoverage(right.path(), mustVisitRequirements);
                    int leftCoverage = countMustVisitCoverage(left.path(), mustVisitRequirements);
                    int byCoverage = Integer.compare(rightCoverage, leftCoverage);
                    if (byCoverage != 0) {
                        return byCoverage;
                    }
                    return Double.compare(right.utility(), left.utility());
                })
                .toList();
    }

    private boolean matchesMustVisitPoi(GenerateReqDTO request, Poi poi) {
        if (request == null || poi == null || !StringUtils.hasText(poi.getName())) {
            return false;
        }
        List<MustVisitRequirement> mustVisitRequirements = buildMustVisitRequirements(request.getMustVisitPoiNames());
        if (mustVisitRequirements.isEmpty()) {
            return false;
        }
        for (MustVisitRequirement requirement : mustVisitRequirements) {
            if (matchesMustVisitRequirement(poi, requirement)) {
                return true;
            }
        }
        return false;
    }

    private int countMustVisitCoverage(List<Poi> path, List<MustVisitRequirement> mustVisitRequirements) {
        if (path == null || path.isEmpty() || mustVisitRequirements == null || mustVisitRequirements.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (MustVisitRequirement requirement : mustVisitRequirements) {
            boolean matched = path.stream().anyMatch(poi -> matchesMustVisitRequirement(poi, requirement));
            if (matched) {
                count++;
            }
        }
        return count;
    }

    private boolean matchesMustVisitRequirement(Poi poi, MustVisitRequirement requirement) {
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
            return List.of();
        }
        Matcher matcher = ASCII_TOKEN_PATTERN.matcher(raw);
        LinkedHashSet<String> tokens = new LinkedHashSet<>();
        while (matcher.find()) {
            tokens.add(matcher.group());
        }
        return new ArrayList<>(tokens);
    }

    private static final class SearchState {
        private final long mask;
        private final int lastIndex;
        private final int currentMinute;
        private final double totalCost;
        private final double utility;
        private final int mustVisitMask;
        private final List<Integer> path;

        private SearchState(long mask,
                            int lastIndex,
                            int currentMinute,
                            double totalCost,
                            double utility,
                            int mustVisitMask,
                            List<Integer> path) {
            this.mask = mask;
            this.lastIndex = lastIndex;
            this.currentMinute = currentMinute;
            this.totalCost = totalCost;
            this.utility = utility;
            this.mustVisitMask = mustVisitMask;
            this.path = path;
        }

        private static SearchState seed(int startMinute) {
            return new SearchState(0L, -1, startMinute, 0D, 0D, 0, Collections.emptyList());
        }
    }

    private record StartAccessProfile(int travelMinutes,
                                      BigDecimal distanceKm,
                                      String transportMode,
                                      double accessPenalty) {
    }

    private static final class DpLabel {
        private final int mask;
        private final int lastIndex;
        private final int currentMinute;
        private final double totalCost;
        private final double utility;
        private final int stopCount;
        private final int mustVisitMask;
        private final DpLabel prev;

        private DpLabel(int mask,
                        int lastIndex,
                        int currentMinute,
                        double totalCost,
                        double utility,
                        int stopCount,
                        int mustVisitMask,
                        DpLabel prev) {
            this.mask = mask;
            this.lastIndex = lastIndex;
            this.currentMinute = currentMinute;
            this.totalCost = totalCost;
            this.utility = utility;
            this.stopCount = stopCount;
            this.mustVisitMask = mustVisitMask;
            this.prev = prev;
        }

        private static DpLabel seed(int startMinute) {
            return new DpLabel(0, -1, startMinute, 0D, 0D, 0, 0, null);
        }
    }

    public record RouteOption(List<Poi> path, String signature, double utility) {
    }

    private record HardConstraintContext(List<String> mustVisitKeywords,
                                         int[] poiMustVisitMasks,
                                         int requiredMustVisitMask,
                                         boolean requiresFullMustVisitCoverage,
                                         Double budgetCeiling,
                                         boolean hasUnmatchedMustVisit) {

        private boolean exceedsBudget(double totalCost) {
            return budgetCeiling != null && totalCost - budgetCeiling > 1e-6;
        }
    }

    private record MustVisitRequirement(String displayName, Set<String> variants) {
    }
}

package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.domain.planning.ItineraryRequestNormalizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class DefaultPoiScoringStrategy implements PoiScoringStrategy {

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

    private final ItineraryRequestNormalizer requestNormalizer;
    private final AlgorithmWeightProvider weightProvider;
    private final CompanionScoringStrategy companionScoringStrategy;
    private final BudgetScoringStrategy budgetScoringStrategy;
    private final WeatherScoringStrategy weatherScoringStrategy;
    private final WalkingScoringStrategy walkingScoringStrategy;

    @Autowired
    public DefaultPoiScoringStrategy(@Autowired(required = false) ItineraryRequestNormalizer requestNormalizer,
                                     AlgorithmWeightProvider weightProvider) {
        this.requestNormalizer = requestNormalizer == null ? new ItineraryRequestNormalizer() : requestNormalizer;
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
        this.companionScoringStrategy = new CompanionScoringStrategy(this.weightProvider);
        this.budgetScoringStrategy = new BudgetScoringStrategy(this.weightProvider);
        this.weatherScoringStrategy = new WeatherScoringStrategy(this.weightProvider);
        this.walkingScoringStrategy = new WalkingScoringStrategy(this.weightProvider);
    }

    public DefaultPoiScoringStrategy(ItineraryRequestNormalizer requestNormalizer) {
        this(requestNormalizer, null);
    }

    public DefaultPoiScoringStrategy() {
        this(null);
    }

    @Override
    public ScoreBreakdown score(GenerateReqDTO request, Poi poi) {
        GenerateReqDTO normalized = normalize(request);
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        Map<String, Double> components = new LinkedHashMap<>();
        double score = 0D;

        double priority = poi == null || poi.getPriorityScore() == null
                ? weights.priorityDefaultScore()
                : poi.getPriorityScore().doubleValue() * weights.priorityMultiplier();
        if (isLocalPoi(poi)) {
            double multiplier = Math.max(0D, Math.min(1D, weights.localPriorityMultiplier()));
            double dampedPriority = priority * multiplier;
            if (Math.abs(dampedPriority - priority) > 1e-6) {
                components.put("localPriorityDamping", dampedPriority - priority);
            }
            priority = dampedPriority;
        }
        components.put("priority", priority);
        score += priority;

        double theme = 0D;
        if (poi != null && normalized.getThemes() != null && poi.getTags() != null) {
            theme = normalized.getThemes().stream()
                    .filter(item -> StringUtils.hasText(item) && poi.getTags().contains(item))
                    .count() * weights.themeMatchScore();
        }
        if (theme != 0D) {
            components.put("theme", theme);
            score += theme;
        }

        ScoreBreakdown companion = companionScoringStrategy.score(normalized, poi);
        components.putAll(companion.components());
        score += companion.total();
        if (matchesMustVisitPoi(normalized, poi)) {
            components.put("mustVisit", weights.mustVisitScore());
            score += weights.mustVisitScore();
        }
        if (poi != null && Boolean.TRUE.equals(normalized.getIsNight()) && Integer.valueOf(1).equals(poi.getNightAvailable())) {
            components.put("night", weights.nightAvailableScore());
            score += weights.nightAvailableScore();
        }
        ScoreBreakdown weather = weatherScoringStrategy.score(normalized, poi);
        components.putAll(weather.components());
        score += weather.total();
        ScoreBreakdown walking = walkingScoringStrategy.score(normalized, poi);
        components.putAll(walking.components());
        score += walking.total();

        ScoreBreakdown budget = budgetScoringStrategy.score(normalized, poi);
        components.putAll(budget.components());
        score += budget.total();
        ScoreBreakdown external = resolveExternalSourceScore(weights, poi);
        components.putAll(external.components());
        score += external.total();
        if (poi != null && Boolean.TRUE.equals(poi.getStatusStale())) {
            components.put("statusStale", -weights.statusStalePenalty());
            score -= weights.statusStalePenalty();
        }
        double stay = resolveStayDurationPenalty(poi);
        if (stay != 0D) {
            components.put("stayDuration", stay);
            score += stay;
        }
        if (poi == null || poi.getOpenTime() == null || poi.getCloseTime() == null) {
            components.put("missingBusinessHours", -weights.missingBusinessHoursPenalty());
            score -= weights.missingBusinessHoursPenalty();
        }
        double crowd = -resolveBaseCrowdPenalty(poi) * weights.candidateCrowdScoreWeight();
        if (crowd != 0D) {
            components.put("crowd", crowd);
            score += crowd;
        }
        return new ScoreBreakdown(Math.max(score, weights.minimumPoiScore()), components);
    }

    public double scorePoi(GenerateReqDTO request, Poi poi) {
        return score(request, poi).total();
    }

    public double resolveRouteValue(GenerateReqDTO request, Poi poi) {
        return poi != null && poi.getTempScore() != null ? poi.getTempScore() : scorePoi(request, poi);
    }

    public boolean matchesWeatherConstraint(GenerateReqDTO req, Poi poi) {
        return req == null
                || !Boolean.TRUE.equals(req.getIsRainy())
                || (poi != null && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly())));
    }

    public boolean matchesWalkingConstraint(GenerateReqDTO req, Poi poi) {
        return poi == null || walkingRank(req == null ? null : req.getWalkingLevel()) >= walkingRank(poi.getWalkingLevel()) - 1;
    }

    public int walkingRank(String value) {
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

    public boolean matchesCompanionPreference(GenerateReqDTO req, Poi poi) {
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

    public boolean isMultiPersonTrip(GenerateReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeMatchingText(req.getCompanionType());
        if (containsAnyNormalized(companionType, SOLO_COMPANION_KEYWORDS)) {
            return false;
        }
        return containsAnyNormalized(companionType, GROUP_COMPANION_KEYWORDS);
    }

    public boolean isGroupFriendlyPoi(Poi poi) {
        if (poi == null) {
            return false;
        }
        return containsAnyNormalized(buildPoiAudienceText(poi), GROUP_FRIENDLY_POI_KEYWORDS);
    }

    public boolean isSoloFocusedPoi(Poi poi) {
        if (poi == null) {
            return false;
        }
        String audienceText = buildPoiAudienceText(poi);
        return containsAnyNormalized(audienceText, SOLO_FOCUSED_POI_KEYWORDS)
                && !containsAnyNormalized(audienceText, GROUP_FRIENDLY_POI_KEYWORDS);
    }

    public double resolveBaseCrowdPenalty(Poi poi) {
        if (poi == null || poi.getCrowdPenalty() == null) {
            return 0D;
        }
        return Math.max(0D, poi.getCrowdPenalty().doubleValue());
    }

    public double resolveVisitCrowdPenalty(Poi poi, GenerateReqDTO req, int visitStartMinute) {
        double penalty = resolveBaseCrowdPenalty(poi);
        if (penalty <= 0D) {
            return 0D;
        }
        double factor = 1.0D;
        if (visitStartMinute >= 11 * 60 && visitStartMinute < 14 * 60) {
            factor += 0.25D;
        }
        if (poi != null && visitStartMinute >= 18 * 60 && Integer.valueOf(1).equals(poi.getNightAvailable())) {
            factor += 0.20D;
        }
        DayOfWeek dayOfWeek = resolveTripDate(req).getDayOfWeek();
        if (dayOfWeek == DayOfWeek.SATURDAY || dayOfWeek == DayOfWeek.SUNDAY) {
            factor += 0.15D;
        }
        return penalty * factor;
    }

    public double resolvePoiCost(Poi poi) {
        if (poi == null || poi.getAvgCost() == null) {
            return 0D;
        }
        return Math.max(0D, poi.getAvgCost().doubleValue());
    }

    public int normalizeStayDuration(Integer stayDuration) {
        if (stayDuration == null) {
            return 90;
        }
        return Math.max(0, stayDuration);
    }

    public boolean matchesMustVisitPoi(GenerateReqDTO request, Poi poi) {
        if (request == null || poi == null || !StringUtils.hasText(poi.getName())) {
            return false;
        }
        List<String> mustVisitKeywords = requestNormalizer.normalizeMustVisitPoiNames(request.getMustVisitPoiNames());
        if (mustVisitKeywords.isEmpty()) {
            return false;
        }
        String poiNameLower = poi.getName().toLowerCase(Locale.ROOT);
        for (String keyword : mustVisitKeywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            if (poiNameLower.contains(normalizedKeyword) || normalizedKeyword.contains(poiNameLower)) {
                return true;
            }
        }
        return false;
    }

    public int countMustVisitCoverage(List<Poi> path, List<String> mustVisitKeywords) {
        if (path == null || path.isEmpty() || mustVisitKeywords == null || mustVisitKeywords.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (String keyword : mustVisitKeywords) {
            if (!StringUtils.hasText(keyword)) {
                continue;
            }
            String normalizedKeyword = keyword.trim().toLowerCase(Locale.ROOT);
            boolean matched = path.stream()
                    .filter(item -> item != null && StringUtils.hasText(item.getName()))
                    .map(Poi::getName)
                    .map(name -> name.toLowerCase(Locale.ROOT))
                    .anyMatch(name -> name.contains(normalizedKeyword) || normalizedKeyword.contains(name));
            if (matched) {
                count++;
            }
        }
        return count;
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

    public GenerateReqDTO normalize(GenerateReqDTO req) {
        return requestNormalizer.normalize(req);
    }

    private double resolveStayDurationPenalty(Poi poi) {
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        int stay = poi == null || poi.getStayDuration() == null ? 90 : poi.getStayDuration();
        if (stay > weights.longStayThresholdMinutes()) {
            return -Math.min(weights.longStayMaxPenalty(),
                    (stay - weights.longStayThresholdMinutes()) / weights.longStayPenaltyStepMinutes());
        }
        return 0D;
    }

    private ScoreBreakdown resolveExternalSourceScore(AlgorithmWeightsSnapshot weights, Poi poi) {
        if (poi == null || !isExternalPoi(poi)) {
            return new ScoreBreakdown(0D, Map.of());
        }
        Map<String, Double> components = new LinkedHashMap<>();
        double score = 0D;

        double realtime = weights.externalRealtimeBaseBonus();
        components.put("externalRealtime", realtime);
        score += realtime;

        double completeness = Math.max(0D, Math.min(1D, resolveExternalCompleteness(poi)));
        double completenessScore = completeness * weights.externalDataCompletenessBonus();
        if (completenessScore != 0D) {
            components.put("externalDataCompleteness", completenessScore);
            score += completenessScore;
        }

        if (Boolean.TRUE.equals(poi.getExternalBusinessDetailsProvided())) {
            components.put("externalBusinessDetails", weights.externalBusinessDetailsBonus());
            score += weights.externalBusinessDetailsBonus();
        } else {
            components.put("externalBusinessFallback", -weights.externalBusinessFallbackPenalty());
            score -= weights.externalBusinessFallbackPenalty();
        }

        return new ScoreBreakdown(score, components);
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

    private boolean isExternalPoi(Poi poi) {
        return poi != null && "external".equalsIgnoreCase(nullToEmpty(poi.getSourceType()));
    }

    private boolean isLocalPoi(Poi poi) {
        if (poi == null || isExternalPoi(poi)) {
            return false;
        }
        String sourceType = nullToEmpty(poi.getSourceType());
        return !"departure".equalsIgnoreCase(sourceType);
    }

    private double resolveExternalCompleteness(Poi poi) {
        if (poi == null) {
            return 0D;
        }
        if (poi.getExternalDataCompleteness() != null) {
            return poi.getExternalDataCompleteness();
        }
        double signals = 0D;
        double present = 0D;

        signals++;
        if (StringUtils.hasText(poi.getExternalId())) {
            present++;
        }
        signals++;
        if (StringUtils.hasText(poi.getAddress())) {
            present++;
        }
        signals++;
        if (StringUtils.hasText(poi.getDistrict())) {
            present++;
        }
        signals++;
        if (StringUtils.hasText(poi.getCategory())) {
            present++;
        }
        signals++;
        if (poi.getOpenTime() != null && poi.getCloseTime() != null) {
            present++;
        }
        signals++;
        if (poi.getAvgCost() != null) {
            present++;
        }
        signals++;
        if (poi.getStayDuration() != null) {
            present++;
        }
        return signals == 0D ? 0D : present / signals;
    }
}

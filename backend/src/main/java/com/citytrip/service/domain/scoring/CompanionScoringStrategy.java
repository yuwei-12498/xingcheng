package com.citytrip.service.domain.scoring;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.config.AlgorithmWeightsProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class CompanionScoringStrategy implements PoiScoringStrategy {

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

    private final AlgorithmWeightProvider weightProvider;

    @Autowired
    public CompanionScoringStrategy(AlgorithmWeightProvider weightProvider) {
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
    }

    public CompanionScoringStrategy() {
        this(null);
    }

    @Override
    public ScoreBreakdown score(GenerateReqDTO request, Poi poi) {
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        Map<String, Double> components = new LinkedHashMap<>();
        double score = 0D;
        if (poi != null
                && request != null
                && StringUtils.hasText(request.getCompanionType())
                && StringUtils.hasText(poi.getSuitableFor())
                && matchesCompanionPreference(request, poi)) {
            components.put("companion", weights.companionMatchScore());
            score += weights.companionMatchScore();
        }
        if (isMultiPersonTrip(request)) {
            if (isGroupFriendlyPoi(poi)) {
                components.put("groupFit", weights.groupTravelFitScore());
                score += weights.groupTravelFitScore();
            } else if (isSoloFocusedPoi(poi)) {
                components.put("groupMismatch", -weights.groupTravelMismatchPenalty());
                score -= weights.groupTravelMismatchPenalty();
            }
        }
        return new ScoreBreakdown(score, components);
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
}

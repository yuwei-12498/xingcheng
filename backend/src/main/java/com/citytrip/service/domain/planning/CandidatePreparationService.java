package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.domain.scoring.ScoreBreakdown;
import com.citytrip.service.PoiService;
import com.citytrip.service.domain.scoring.DefaultPoiScoringStrategy;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class CandidatePreparationService {

    public static final int CANDIDATE_LIMIT = 18;

    private final PoiService poiService;
    private final ItineraryRequestNormalizer requestNormalizer;
    private final DefaultPoiScoringStrategy scoringStrategy;

    public CandidatePreparationService(PoiService poiService,
                                       ItineraryRequestNormalizer requestNormalizer,
                                       DefaultPoiScoringStrategy scoringStrategy) {
        this.poiService = poiService;
        this.requestNormalizer = requestNormalizer == null ? new ItineraryRequestNormalizer() : requestNormalizer;
        this.scoringStrategy = scoringStrategy == null
                ? new DefaultPoiScoringStrategy(this.requestNormalizer)
                : scoringStrategy;
    }

    public List<Poi> prepareCandidates(List<Poi> source, GenerateReqDTO req, boolean applyLimit) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        GenerateReqDTO normalized = requestNormalizer.normalize(req);
        List<Poi> filtered = source.stream()
                .filter(Objects::nonNull)
                .filter(poi -> scoringStrategy.matchesWeatherConstraint(normalized, poi))
                .filter(poi -> scoringStrategy.matchesWalkingConstraint(normalized, poi))
                .collect(Collectors.toCollection(ArrayList::new));
        filtered = applyIntentCategoryGuardrails(filtered, normalized);
        poiService.enrichOperatingStatus(filtered, scoringStrategy.resolveTripDate(normalized));
        filtered.forEach(poi -> {
            ScoreBreakdown breakdown = scoringStrategy.score(normalized, poi);
            poi.setTempScore(breakdown.total());
            poi.setTempScoreBreakdown(breakdown.components());
        });
        filtered.sort((left, right) -> {
            int byScore = Double.compare(right.getTempScore(), left.getTempScore());
            if (byScore != 0) {
                return byScore;
            }
            BigDecimal rightPriority = right.getPriorityScore() == null ? BigDecimal.ZERO : right.getPriorityScore();
            BigDecimal leftPriority = left.getPriorityScore() == null ? BigDecimal.ZERO : left.getPriorityScore();
            return rightPriority.compareTo(leftPriority);
        });
        List<Poi> available = filtered.stream()
                .filter(poi -> !Boolean.FALSE.equals(poi.getAvailableOnTripDate()))
                .collect(Collectors.toCollection(ArrayList::new));
        return applyLimit && available.size() > CANDIDATE_LIMIT
                ? new ArrayList<>(available.subList(0, CANDIDATE_LIMIT))
                : available;
    }

    private List<Poi> applyIntentCategoryGuardrails(List<Poi> candidates, GenerateReqDTO req) {
        if (candidates == null || candidates.isEmpty() || req == null) {
            return candidates == null ? Collections.emptyList() : candidates;
        }
        List<String> preferred = normalizeHints(req.getPreferredPoiCategories());
        List<String> excluded = normalizeHints(req.getExcludedPoiCategories());
        if (preferred.isEmpty() && excluded.isEmpty()) {
            return candidates;
        }

        boolean hasPreferredHits = !preferred.isEmpty()
                && candidates.stream().anyMatch(poi -> matchesAnyHint(poi, preferred));
        boolean hasNonExcluded = excluded.isEmpty()
                || candidates.stream().anyMatch(poi -> !matchesAnyHint(poi, excluded) || matchesMustVisit(req, poi));

        List<Poi> guarded = new ArrayList<>();
        for (Poi poi : candidates) {
            boolean mustVisit = matchesMustVisit(req, poi);
            boolean preferredHit = matchesAnyHint(poi, preferred);
            boolean excludedHit = matchesAnyHint(poi, excluded);
            if (excludedHit && !mustVisit && hasNonExcluded) {
                continue;
            }
            if (hasPreferredHits && !preferredHit && !mustVisit) {
                continue;
            }
            guarded.add(poi);
        }
        return guarded.isEmpty() ? candidates : guarded;
    }

    private List<String> normalizeHints(List<String> hints) {
        if (hints == null || hints.isEmpty()) {
            return List.of();
        }
        Set<String> normalized = new LinkedHashSet<>();
        for (String hint : hints) {
            String value = normalizeText(hint);
            if (StringUtils.hasText(value)) {
                normalized.add(value);
            }
        }
        return new ArrayList<>(normalized);
    }

    private boolean matchesAnyHint(Poi poi, List<String> hints) {
        if (poi == null || hints == null || hints.isEmpty()) {
            return false;
        }
        String haystack = normalizeText(String.join(" ",
                nullToEmpty(poi.getName()),
                nullToEmpty(poi.getCategory()),
                nullToEmpty(poi.getTags()),
                nullToEmpty(poi.getDescription())));
        if (!StringUtils.hasText(haystack)) {
            return false;
        }
        return hints.stream().anyMatch(haystack::contains);
    }

    private boolean matchesMustVisit(GenerateReqDTO req, Poi poi) {
        if (req == null || poi == null || req.getMustVisitPoiNames() == null || req.getMustVisitPoiNames().isEmpty()) {
            return false;
        }
        String haystack = normalizeText(String.join(" ",
                nullToEmpty(poi.getName()),
                nullToEmpty(poi.getCategory()),
                nullToEmpty(poi.getTags()),
                nullToEmpty(poi.getDescription())));
        if (!StringUtils.hasText(haystack)) {
            return false;
        }
        for (String keyword : req.getMustVisitPoiNames()) {
            String normalizedKeyword = normalizeText(keyword);
            if (StringUtils.hasText(normalizedKeyword)
                    && (haystack.contains(normalizedKeyword) || normalizedKeyword.contains(haystack))) {
                return true;
            }
        }
        return false;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？!?:：；、\"'“”‘’()（）\\[\\]{}<>《》·`~_-]+", "");
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}

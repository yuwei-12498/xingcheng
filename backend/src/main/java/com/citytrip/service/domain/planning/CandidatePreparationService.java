package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.domain.scoring.ScoreBreakdown;
import com.citytrip.service.PoiService;
import com.citytrip.service.domain.scoring.DefaultPoiScoringStrategy;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
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
}

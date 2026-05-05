package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Locale;
import java.util.Map;

@Component
public class WalkingScoringStrategy implements PoiScoringStrategy {

    private final AlgorithmWeightProvider weightProvider;

    @Autowired
    public WalkingScoringStrategy(AlgorithmWeightProvider weightProvider) {
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
    }

    public WalkingScoringStrategy() {
        this(null);
    }

    @Override
    public ScoreBreakdown score(GenerateReqDTO request, Poi poi) {
        if (poi != null && walkingRank(request == null ? null : request.getWalkingLevel()) >= walkingRank(poi.getWalkingLevel())) {
            double score = weightProvider.current().walkingFitScore();
            return new ScoreBreakdown(score, Map.of("walking", score));
        }
        return new ScoreBreakdown(0D, Map.of());
    }

    public boolean matchesWalkingConstraint(GenerateReqDTO req, Poi poi) {
        return poi == null || walkingRank(req == null ? null : req.getWalkingLevel()) >= walkingRank(poi.getWalkingLevel()) - 1;
    }

    public int walkingRank(String value) {
        if (!StringUtils.hasText(value)) {
            return 2;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        if (normalized.contains("\u4f4e") || normalized.contains("low") || normalized.contains("light")) {
            return 1;
        }
        if (normalized.contains("\u9ad8") || normalized.contains("high") || normalized.contains("heavy")) {
            return 3;
        }
        return 2;
    }
}

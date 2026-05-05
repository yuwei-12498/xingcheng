package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Map;

@Component
public class WeatherScoringStrategy implements PoiScoringStrategy {

    private final AlgorithmWeightProvider weightProvider;

    @Autowired
    public WeatherScoringStrategy(AlgorithmWeightProvider weightProvider) {
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
    }

    public WeatherScoringStrategy() {
        this(null);
    }

    @Override
    public ScoreBreakdown score(GenerateReqDTO request, Poi poi) {
        if (poi != null
                && request != null
                && Boolean.TRUE.equals(request.getIsRainy())
                && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly()))) {
            double score = weightProvider.current().rainFriendlyScore();
            return new ScoreBreakdown(score, Map.of("rain", score));
        }
        return new ScoreBreakdown(0D, Map.of());
    }

    public boolean matchesWeatherConstraint(GenerateReqDTO req, Poi poi) {
        return req == null
                || !Boolean.TRUE.equals(req.getIsRainy())
                || (poi != null && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly())));
    }
}

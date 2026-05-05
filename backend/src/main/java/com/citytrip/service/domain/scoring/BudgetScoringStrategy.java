package com.citytrip.service.domain.scoring;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.Map;

@Component
public class BudgetScoringStrategy implements PoiScoringStrategy {

    private final AlgorithmWeightProvider weightProvider;

    @Autowired
    public BudgetScoringStrategy(AlgorithmWeightProvider weightProvider) {
        this.weightProvider = weightProvider == null
                ? new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties())
                : weightProvider;
    }

    public BudgetScoringStrategy() {
        this(null);
    }

    @Override
    public ScoreBreakdown score(GenerateReqDTO request, Poi poi) {
        double score = resolveBudgetScore(request, poi);
        return score == 0D
                ? new ScoreBreakdown(0D, Map.of())
                : new ScoreBreakdown(score, Map.of("budget", score));
    }

    public double resolveBudgetScore(GenerateReqDTO normalized, Poi poi) {
        if (normalized == null || poi == null || !StringUtils.hasText(normalized.getBudgetLevel()) || poi.getAvgCost() == null) {
            return 0D;
        }
        AlgorithmWeightsSnapshot weights = weightProvider.current();
        double cost = poi.getAvgCost().doubleValue();
        String budget = normalized.getBudgetLevel();
        if (budget.contains("\u4f4e") || budget.equalsIgnoreCase("low")) {
            if (cost <= 20) {
                return weights.lowBudgetCheapScore();
            }
            if (cost > 100) {
                return -weights.lowBudgetExpensivePenalty();
            }
            if (cost > 50) {
                return -weights.lowBudgetMediumPenalty();
            }
        } else if (budget.contains("\u4e2d") || budget.equalsIgnoreCase("medium")) {
            if (cost > 300) {
                return -weights.mediumBudgetVeryExpensivePenalty();
            }
            if (cost > 150) {
                return -weights.mediumBudgetExpensivePenalty();
            }
        } else if (budget.contains("\u9ad8") || budget.equalsIgnoreCase("high")) {
            if (cost >= 100) {
                return weights.highBudgetPremiumScore();
            }
        }
        return 0D;
    }
}

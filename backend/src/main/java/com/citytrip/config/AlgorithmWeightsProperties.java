package com.citytrip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "app.algorithm.weights")
public class AlgorithmWeightsProperties {
    private double priorityDefaultScore = 7.5D;
    private double priorityMultiplier = 2.5D;
    /**
     * Local seed POIs are curated and usually have high priority_score. Keep this below 1.0
     * so real-time external POIs can compete without being force-inserted into every route.
     */
    private double localPriorityMultiplier = 0.82D;
    private double themeMatchScore = 3.5D;
    private double routeScoreWeight = 6.0D;
    private double routeTravelPenaltyWeight = 1.0D;
    private double routeWaitPenaltyWeight = 0.5D;
    private double routeCrowdPenaltyWeight = 4.0D;
    private double companionMatchScore = 2.5D;
    private double groupTravelFitScore = 3.0D;
    private double groupTravelMismatchPenalty = 1.5D;
    private double mustVisitScore = 120.0D;
    private double nightAvailableScore = 2.0D;
    private double rainFriendlyScore = 1.5D;
    private double walkingFitScore = 1.0D;
    private double lowBudgetCheapScore = 2.0D;
    private double lowBudgetExpensivePenalty = 15.0D;
    private double lowBudgetMediumPenalty = 8.0D;
    private double mediumBudgetVeryExpensivePenalty = 12.0D;
    private double mediumBudgetExpensivePenalty = 5.0D;
    private double highBudgetPremiumScore = 2.0D;
    private double statusStalePenalty = 2.0D;
    private int longStayThresholdMinutes = 180;
    private double longStayMaxPenalty = 3.0D;
    private double longStayPenaltyStepMinutes = 45.0D;
    private double missingBusinessHoursPenalty = 1.0D;
    private double candidateCrowdScoreWeight = 1.5D;
    private double minimumPoiScore = 1.0D;
    private double externalRealtimeBaseBonus = 1.2D;
    private double externalDataCompletenessBonus = 2.4D;
    private double externalBusinessDetailsBonus = 1.6D;
    private double externalBusinessFallbackPenalty = 1.4D;
    private double firstLegTimePenaltyWeight = 0.9D;
    private double firstLegDistancePenaltyWeight = 4.8D;
    private double firstLegTransferPenaltyWeight = 7.0D;
    private double startModeUnknownPenalty = 2.0D;
    private double startModeLowWalkingPenalty = 1.5D;
    private double startModeBikePenalty = 1.5D;
    private double startModeBusExtraPenalty = 1.5D;
    private double startModeTaxiExtraPenalty = 2.5D;
    private double startModeOtherPenalty = 3.0D;
    private double startDistanceSoftThresholdKm = 2.5D;
    private double startDistanceSoftThresholdWeight = 2.4D;
    private double startDistanceMediumThresholdKm = 5.0D;
    private double startDistanceMediumBasePenalty = 10.0D;
    private double startDistanceMediumWeight = 3.6D;
    private double startDistanceHardThresholdKm = 9.0D;
    private double startDistanceHardBasePenalty = 20.0D;
    private double startDistanceHardWeight = 5.2D;
    private int startTimeSoftThresholdMinutes = 15;
    private double startTimeSoftThresholdWeight = 0.9D;
    private int startTimeMediumThresholdMinutes = 28;
    private double startTimeMediumBasePenalty = 8.0D;
    private double startTimeMediumWeight = 1.25D;
    private int startTimeHardThresholdMinutes = 40;
    private double startTimeHardBasePenalty = 16.0D;
    private double startTimeHardWeight = 1.8D;
}

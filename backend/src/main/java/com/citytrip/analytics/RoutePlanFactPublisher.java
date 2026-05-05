package com.citytrip.analytics;

import com.citytrip.analytics.command.RoutePlanFactTrackCommand;
import com.citytrip.analytics.event.RoutePlanFactTrackedEvent;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ItineraryVO;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDateTime;

@Component
public class RoutePlanFactPublisher {

    private final ApplicationEventPublisher eventPublisher;
    private final RouteRecommendationTelemetryLogger telemetryLogger;

    public RoutePlanFactPublisher(ApplicationEventPublisher eventPublisher,
                                  RouteRecommendationTelemetryLogger telemetryLogger) {
        this.eventPublisher = eventPublisher;
        this.telemetryLogger = telemetryLogger;
    }

    public void publish(Long userId,
                        Long itineraryId,
                        String planSource,
                        GenerateReqDTO request,
                        ItineraryVO itinerary,
                        int rawCandidateCount,
                        int filteredCandidateCount,
                        int finalCandidateCount,
                        int maxStops,
                        int generatedRouteCount,
                        int displayedOptionCount,
                        boolean success,
                        String failReason,
                        String algorithmVersion,
                        String recallStrategy,
                        LocalDateTime planningStartedAt) {
        LocalDateTime finishedAt = LocalDateTime.now();
        RoutePlanFactTrackCommand command = new RoutePlanFactTrackCommand();
        command.setUserId(userId);
        command.setItineraryId(itineraryId);
        command.setPlanSource(planSource);
        command.setRequest(request);
        command.setItinerary(itinerary);
        command.setRawCandidateCount(rawCandidateCount);
        command.setFilteredCandidateCount(filteredCandidateCount);
        command.setFinalCandidateCount(finalCandidateCount);
        command.setMaxStops(maxStops);
        command.setGeneratedRouteCount(generatedRouteCount);
        command.setDisplayedOptionCount(displayedOptionCount);
        command.setSuccessFlag(success);
        command.setFailReason(failReason);
        command.setAlgorithmVersion(algorithmVersion);
        command.setRecallStrategy(recallStrategy);
        command.setPlanningStartedAt(planningStartedAt);
        command.setPlanningFinishedAt(finishedAt);
        if (planningStartedAt != null) {
            command.setCostMs((int) Math.max(0L, Duration.between(planningStartedAt, finishedAt).toMillis()));
        }
        telemetryLogger.logRecommendation(command);
        eventPublisher.publishEvent(new RoutePlanFactTrackedEvent(command));
    }
}

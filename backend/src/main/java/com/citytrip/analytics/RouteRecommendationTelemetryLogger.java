package com.citytrip.analytics;

import com.citytrip.analytics.command.RoutePlanFactTrackCommand;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Component
public class RouteRecommendationTelemetryLogger {

    private static final Logger telemetryLog = LoggerFactory.getLogger("route.recommendation.telemetry");

    private final ObjectMapper objectMapper;

    public RouteRecommendationTelemetryLogger(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void logRecommendation(RoutePlanFactTrackCommand command) {
        if (command == null) {
            return;
        }
        try {
            telemetryLog.info(objectMapper.writeValueAsString(toPayload(command)));
        } catch (JsonProcessingException ex) {
            telemetryLog.warn("Failed to serialize route recommendation telemetry. reason={}", ex.getMessage());
        }
    }

    private Map<String, Object> toPayload(RoutePlanFactTrackCommand command) {
        ItineraryVO itinerary = command.getItinerary();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("eventType", "route_recommendation");
        payload.put("schemaVersion", "route-recommendation-v1");
        payload.put("userId", command.getUserId());
        payload.put("itineraryId", command.getItineraryId());
        payload.put("planSource", command.getPlanSource());
        payload.put("algorithmVersion", command.getAlgorithmVersion());
        payload.put("recallStrategy", command.getRecallStrategy());
        payload.put("rawCandidateCount", command.getRawCandidateCount());
        payload.put("filteredCandidateCount", command.getFilteredCandidateCount());
        payload.put("finalCandidateCount", command.getFinalCandidateCount());
        payload.put("generatedRouteCount", command.getGeneratedRouteCount());
        payload.put("displayedOptionCount", command.getDisplayedOptionCount());
        payload.put("selectedOptionKey", itinerary == null ? null : itinerary.getSelectedOptionKey());
        payload.put("success", command.getSuccessFlag());
        payload.put("failReason", command.getFailReason());
        payload.put("planningStartedAt", command.getPlanningStartedAt());
        payload.put("planningFinishedAt", command.getPlanningFinishedAt());
        payload.put("costMs", command.getCostMs());
        payload.put("options", toOptions(itinerary));
        return payload;
    }

    private List<Map<String, Object>> toOptions(ItineraryVO itinerary) {
        if (itinerary == null || itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            return List.of();
        }
        return itinerary.getOptions().stream()
                .filter(option -> option != null)
                .map(this::toOptionPayload)
                .toList();
    }

    private Map<String, Object> toOptionPayload(ItineraryOptionVO option) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("optionKey", option.getOptionKey());
        payload.put("signature", option.getSignature());
        payload.put("routeUtility", option.getRouteUtility());
        payload.put("criticScore", option.getCriticScore());
        payload.put("totalDuration", option.getTotalDuration());
        payload.put("totalCost", option.getTotalCost());
        payload.put("totalTravelTime", option.getTotalTravelTime());
        payload.put("businessRiskScore", option.getBusinessRiskScore());
        payload.put("themeMatchCount", option.getThemeMatchCount());
        payload.put("featureVector", option.getFeatureVector());
        payload.put("nodeCount", option.getNodes() == null ? 0 : option.getNodes().size());
        return payload;
    }
}

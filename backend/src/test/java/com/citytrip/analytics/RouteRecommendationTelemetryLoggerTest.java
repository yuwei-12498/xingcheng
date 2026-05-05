package com.citytrip.analytics;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.citytrip.analytics.command.RoutePlanFactTrackCommand;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.RouteFeatureVectorVO;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RouteRecommendationTelemetryLoggerTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void logsStructuredRecommendationPayloadWithFeatureVector() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("route.recommendation.telemetry");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            RouteRecommendationTelemetryLogger telemetryLogger = new RouteRecommendationTelemetryLogger(objectMapper);
            telemetryLogger.logRecommendation(buildCommand());

            assertThat(appender.list).hasSize(1);
            JsonNode payload = objectMapper.readTree(appender.list.get(0).getFormattedMessage());
            assertThat(payload.path("eventType").asText()).isEqualTo("route_recommendation");
            assertThat(payload.path("schemaVersion").asText()).isEqualTo("route-recommendation-v1");
            assertThat(payload.path("selectedOptionKey").asText()).isEqualTo("balanced");
            assertThat(payload.path("options")).hasSize(1);
            JsonNode featureVector = payload.path("options").get(0).path("featureVector");
            assertThat(featureVector.path("signature").asText()).isEqualTo("1-2-3");
            assertThat(featureVector.path("totalCostEstimated").decimalValue()).isEqualByComparingTo("80");
            assertThat(featureVector.path("scoreBreakdown").path("utility").doubleValue()).isEqualTo(12.5D);
        } finally {
            logger.detachAppender(appender);
        }
    }

    private RoutePlanFactTrackCommand buildCommand() {
        RoutePlanFactTrackCommand command = new RoutePlanFactTrackCommand();
        command.setUserId(101L);
        command.setItineraryId(501L);
        command.setPlanSource("generate");
        command.setAlgorithmVersion("route-optimizer-v2");
        command.setSuccessFlag(true);
        command.setItinerary(buildItinerary());
        return command;
    }

    private ItineraryVO buildItinerary() {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setSignature("1-2-3");
        option.setRouteUtility(12.5D);
        option.setFeatureVector(buildFeatureVector());
        itinerary.setOptions(List.of(option));
        return itinerary;
    }

    private RouteFeatureVectorVO buildFeatureVector() {
        RouteFeatureVectorVO vector = new RouteFeatureVectorVO();
        vector.setSignature("1-2-3");
        vector.setStopCount(3);
        vector.setTotalCostEstimated(BigDecimal.valueOf(80));
        vector.setRouteUtility(12.5D);
        vector.setScoreBreakdown(Map.of("utility", 12.5D));
        return vector;
    }
}

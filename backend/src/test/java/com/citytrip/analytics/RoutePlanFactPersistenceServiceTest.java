package com.citytrip.analytics;

import com.citytrip.analytics.command.RoutePlanFactTrackCommand;
import com.citytrip.mapper.RouteNodeFactMapper;
import com.citytrip.mapper.RoutePlanFactMapper;
import com.citytrip.model.entity.RoutePlanFact;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.RouteFeatureVectorVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RoutePlanFactPersistenceServiceTest {

    private final RoutePlanFactMapper routePlanFactMapper = mock(RoutePlanFactMapper.class);
    private final RouteNodeFactMapper routeNodeFactMapper = mock(RouteNodeFactMapper.class);
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final RoutePlanFactPersistenceService service = new RoutePlanFactPersistenceService(
            routePlanFactMapper,
            routeNodeFactMapper,
            objectMapper
    );

    @Test
    void persistsSelectedAndCandidateFeatureJsonForLtrTraining() throws Exception {
        when(routePlanFactMapper.insert(any(RoutePlanFact.class))).thenAnswer(invocation -> {
            RoutePlanFact fact = invocation.getArgument(0, RoutePlanFact.class);
            fact.setId(9001L);
            return 1;
        });

        service.persist(buildCommand());

        ArgumentCaptor<RoutePlanFact> captor = ArgumentCaptor.forClass(RoutePlanFact.class);
        verify(routePlanFactMapper).insert(captor.capture());
        RoutePlanFact fact = captor.getValue();
        assertThat(fact.getSelectedRouteFeatureJson()).contains("\"signature\":\"1-2-3\"");
        assertThat(objectMapper.readTree(fact.getSelectedRouteFeatureJson()).path("totalCostEstimated").decimalValue())
                .isEqualByComparingTo("80");
        assertThat(objectMapper.readTree(fact.getOptionsFeatureJson())).hasSize(2);
        assertThat(fact.getOptionsFeatureJson()).contains("balanced", "cost_saver", "featureVector");
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
        itinerary.setOptions(List.of(
                buildOption("balanced", "1-2-3", 12.5D, BigDecimal.valueOf(80)),
                buildOption("cost_saver", "1-4-3", 9.0D, BigDecimal.valueOf(35))
        ));
        return itinerary;
    }

    private ItineraryOptionVO buildOption(String optionKey, String signature, Double utility, BigDecimal totalCost) {
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey(optionKey);
        option.setSignature(signature);
        option.setRouteUtility(utility);
        option.setFeatureVector(buildFeatureVector(signature, utility, totalCost));
        return option;
    }

    private RouteFeatureVectorVO buildFeatureVector(String signature, Double utility, BigDecimal totalCost) {
        RouteFeatureVectorVO vector = new RouteFeatureVectorVO();
        vector.setSignature(signature);
        vector.setStopCount(3);
        vector.setTotalCostEstimated(totalCost);
        vector.setRouteUtility(utility);
        vector.setScoreBreakdown(Map.of("utility", utility, "totalCostEstimated", totalCost));
        return vector;
    }
}

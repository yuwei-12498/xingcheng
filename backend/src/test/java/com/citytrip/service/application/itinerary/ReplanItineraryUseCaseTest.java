package com.citytrip.service.application.itinerary;

import com.citytrip.analytics.RoutePlanFactPublisher;
import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.model.dto.ReplanReqDTO;
import com.citytrip.model.dto.ReplanRespDTO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.domain.planning.PlanningPoiQueryService;
import com.citytrip.service.domain.policy.MaxStopsPolicy;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ReplanItineraryUseCaseTest {

    @Test
    void returnsGracefulResponseWhenCurrentNodesAreMissing() {
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        PlanningPoiQueryService planningPoiQueryService = mock(PlanningPoiQueryService.class);
        ItineraryComparisonAssembler itineraryComparisonAssembler = mock(ItineraryComparisonAssembler.class);
        ItineraryAiDecorationService itineraryAiDecorationService = mock(ItineraryAiDecorationService.class);
        SavedItineraryCommandService savedItineraryCommandService = mock(SavedItineraryCommandService.class);
        ItineraryQueryService itineraryQueryService = mock(ItineraryQueryService.class);
        RoutePlanFactPublisher routePlanFactPublisher = mock(RoutePlanFactPublisher.class);
        MaxStopsPolicy maxStopsPolicy = mock(MaxStopsPolicy.class);

        ReplanItineraryUseCase useCase = new ReplanItineraryUseCase(
                routeOptimizer,
                planningPoiQueryService,
                itineraryComparisonAssembler,
                itineraryAiDecorationService,
                savedItineraryCommandService,
                itineraryQueryService,
                routePlanFactPublisher,
                maxStopsPolicy
        );

        ReplanRespDTO result = useCase.replan(9L, 10L, new ReplanReqDTO());

        assertThat(result.getSuccess()).isFalse();
        assertThat(result.getChanged()).isFalse();
        assertThat(result.getMessage()).isEqualTo("当前没有可重新规划的行程。");
        verifyNoInteractions(
                routeOptimizer,
                planningPoiQueryService,
                itineraryComparisonAssembler,
                itineraryAiDecorationService,
                savedItineraryCommandService,
                itineraryQueryService,
                routePlanFactPublisher,
                maxStopsPolicy
        );
    }
}

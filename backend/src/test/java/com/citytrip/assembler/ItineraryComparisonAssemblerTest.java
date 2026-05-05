package com.citytrip.assembler;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.planning.RouteAnalysisService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryComparisonAssemblerTest {

    @Test
    void buildComparedItineraryKeepsTopCandidateRoutesForAiCritic() {
        RouteAnalysisService routeAnalysisService = mock(RouteAnalysisService.class);
        ItineraryComparisonAssembler assembler = new ItineraryComparisonAssembler(routeAnalysisService);

        GenerateReqDTO req = new GenerateReqDTO();
        req.setTripDate("2026-04-22");

        ItineraryRouteOptimizer.RouteOption routeOne = new ItineraryRouteOptimizer.RouteOption(
                List.of(createPoi(1L, "青城山")),
                "1",
                98.0D
        );
        ItineraryRouteOptimizer.RouteOption routeTwo = new ItineraryRouteOptimizer.RouteOption(
                List.of(createPoi(2L, "IFS 国金中心")),
                "2",
                92.0D
        );
        ItineraryRouteOptimizer.RouteOption routeThree = new ItineraryRouteOptimizer.RouteOption(
                List.of(createPoi(3L, "锦里")),
                "3",
                88.0D
        );

        when(routeAnalysisService.analyzeRoute(eq(routeOne), eq(req), anyMap()))
                .thenReturn(buildAnalysis(routeOne, "青城山适合作为当天主线"));
        when(routeAnalysisService.analyzeRoute(eq(routeTwo), eq(req), anyMap()))
                .thenReturn(buildAnalysis(routeTwo, "IFS 更适合商圈漫游"));
        when(routeAnalysisService.analyzeRoute(eq(routeThree), eq(req), anyMap()))
                .thenReturn(buildAnalysis(routeThree, "锦里适合作为轻量收尾"));

        ItineraryVO itinerary = assembler.buildComparedItinerary(
                List.of(routeOne, routeTwo, routeThree),
                req,
                Map.of(),
                null,
                Set.of()
        );

        assertThat(itinerary.getOptions()).hasSize(3);
        assertThat(itinerary.getSelectedOptionKey()).isEqualTo("balanced");
        assertThat(itinerary.getOptions().get(0).getFeatureVector()).isNotNull();
        assertThat(itinerary.getOptions().get(0).getFeatureVector().getTotalCostEstimated()).isEqualByComparingTo("80");
        assertThat(itinerary.getOptions().get(0).getFeatureVector().getScoreBreakdown()).containsKey("utility");
        assertThat(itinerary.getRecommendReason()).isNotBlank();
        assertThat(itinerary.getTips()).contains("3 套可执行方案");
        assertThat(itinerary.getTips()).contains("切换");
    }

    private RouteAnalysisService.RouteAnalysis buildAnalysis(ItineraryRouteOptimizer.RouteOption route, String reason) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiId(route.path().get(0).getId());
        node.setPoiName(route.path().get(0).getName());
        node.setCategory("scenic");
        node.setDistrict("成都");
        node.setStartTime("09:00");
        node.setEndTime("10:30");
        node.setStayDuration(90);
        node.setTravelTime(0);
        node.setTravelTransportMode("walk");
        node.setTravelDistanceKm(BigDecimal.valueOf(1.2D));
        node.setCost(BigDecimal.valueOf(80));
        node.setSysReason(reason);

        return new RouteAnalysisService.RouteAnalysis(
                route,
                List.of(node),
                90,
                BigDecimal.valueOf(80),
                0,
                0,
                1,
                1,
                0,
                0,
                0,
                1,
                List.of("出发前注意营业状态")
        );
    }

    private Poi createPoi(Long id, String name) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setCategory("scenic");
        poi.setDistrict("成都");
        poi.setAvgCost(BigDecimal.valueOf(80));
        return poi;
    }
}

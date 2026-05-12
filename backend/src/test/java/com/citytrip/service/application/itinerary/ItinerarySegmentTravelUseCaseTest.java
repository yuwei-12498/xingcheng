package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.SegmentTravelReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.TravelModeRequest;
import com.citytrip.service.domain.planning.SegmentRouteGuideService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.impl.AmapTravelTimeServiceImpl;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItinerarySegmentTravelUseCaseTest {

    @Test
    void calculateShouldFetchDetailedTravelGuideForRequestedSegmentAndSaveSameItinerary() {
        SavedItineraryRepository savedItineraryRepository = mock(SavedItineraryRepository.class);
        SavedItineraryCommandService savedItineraryCommandService = mock(SavedItineraryCommandService.class);
        AmapTravelTimeServiceImpl amapTravelTimeService = mock(AmapTravelTimeServiceImpl.class);
        SavedItineraryCodec savedItineraryCodec = new SavedItineraryCodec(new ObjectMapper());
        ItinerarySegmentTravelUseCase useCase = new ItinerarySegmentTravelUseCase(
                savedItineraryRepository,
                savedItineraryCodec,
                savedItineraryCommandService,
                amapTravelTimeService,
                new SegmentRouteGuideService()
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setDeparturePlaceName("??");
        request.setDepartureLatitude(30.65D);
        request.setDepartureLongitude(104.06D);
        request.setStartTime("09:00");

        ItineraryNodeVO firstNode = node(1L, 1, 1, "A", 30.652D, 104.062D);
        firstNode.setTravelTime(10);
        firstNode.setStayDuration(60);
        ItineraryNodeVO secondNode = node(2L, 1, 2, "B", 30.66D, 104.072D);
        secondNode.setStayDuration(90);
        ItineraryNodeVO optionFirstNode = node(1L, 1, 1, "A", 30.652D, 104.062D);
        optionFirstNode.setTravelTime(10);
        optionFirstNode.setStayDuration(60);
        ItineraryNodeVO optionSecondNode = node(2L, 1, 2, "B", 30.66D, 104.072D);
        optionSecondNode.setStayDuration(90);
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setNodes(List.of(optionFirstNode, optionSecondNode));
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setId(99L);
        itinerary.setNodes(List.of(firstNode, secondNode));
        itinerary.setOptions(List.of(option));

        SavedItinerary entity = new SavedItinerary();
        entity.setId(99L);
        entity.setUserId(7L);
        entity.setRequestJson(savedItineraryCodec.writeJson(request));
        entity.setItineraryJson(savedItineraryCodec.writeJson(itinerary));
        when(savedItineraryRepository.requireOwned(7L, 99L)).thenReturn(entity);
        when(amapTravelTimeService.estimateTravelLeg(any(), any(), eq(TravelModeRequest.AUTO))).thenReturn(new TravelTimeService.TravelLegEstimate(
                16,
                BigDecimal.valueOf(2.84D),
                "??+??",
                List.of(point(30.652D, 104.062D), point(30.66D, 104.072D))
        ));
        when(savedItineraryCommandService.save(eq(7L), eq(99L), any(GenerateReqDTO.class), any(ItineraryVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(3));

        ItineraryVO updated = useCase.calculate(7L, 99L, 1);

        assertThat(updated.getNodes().get(1).getTravelTime()).isEqualTo(16);
        assertThat(updated.getNodes().get(1).getTravelDistanceKm()).isEqualByComparingTo("2.8");
        assertThat(updated.getNodes().get(1).getTravelTransportMode()).isEqualTo("??+??");
        assertThat(updated.getNodes().get(1).getSegmentRouteGuide()).isNotNull();
        assertThat(updated.getNodes().get(1).getSegmentRouteGuide().getRecommendedTransportMode()).isEqualTo("??+??");
        assertThat(updated.getNodes().get(1).getRoutePathPoints()).hasSize(2);
        assertThat(updated.getNodes().get(0).getStartTime()).isEqualTo("09:10");
        assertThat(updated.getNodes().get(0).getEndTime()).isEqualTo("10:10");
        assertThat(updated.getNodes().get(1).getStartTime()).isEqualTo("10:26");
        assertThat(updated.getNodes().get(1).getEndTime()).isEqualTo("11:56");
        assertThat(updated.getOptions().get(0).getNodes().get(1).getSegmentRouteGuide()).isNotNull();
        assertThat(updated.getOptions().get(0).getNodes().get(1).getRoutePathPoints()).hasSize(2);
        assertThat(updated.getOptions().get(0).getNodes().get(1).getStartTime()).isEqualTo("10:26");
        verify(savedItineraryCommandService).save(eq(7L), eq(99L), any(GenerateReqDTO.class), any(ItineraryVO.class));
    }

    @Test
    void calculateShouldUseDepartureForFirstStopOfEachDay() {
        SavedItineraryRepository savedItineraryRepository = mock(SavedItineraryRepository.class);
        SavedItineraryCommandService savedItineraryCommandService = mock(SavedItineraryCommandService.class);
        AmapTravelTimeServiceImpl amapTravelTimeService = mock(AmapTravelTimeServiceImpl.class);
        SavedItineraryCodec savedItineraryCodec = new SavedItineraryCodec(new ObjectMapper());
        ItinerarySegmentTravelUseCase useCase = new ItinerarySegmentTravelUseCase(
                savedItineraryRepository,
                savedItineraryCodec,
                savedItineraryCommandService,
                amapTravelTimeService,
                new SegmentRouteGuideService()
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setDeparturePlaceName("??");
        request.setDepartureLatitude(30.65D);
        request.setDepartureLongitude(104.06D);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setId(99L);
        itinerary.setNodes(List.of(
                node(1L, 1, 1, "Day1-A", 30.652D, 104.062D),
                node(2L, 1, 2, "Day1-B", 30.66D, 104.072D),
                node(3L, 2, 1, "Day2-A", 30.67D, 104.08D)
        ));

        SavedItinerary entity = new SavedItinerary();
        entity.setId(99L);
        entity.setUserId(7L);
        entity.setRequestJson(savedItineraryCodec.writeJson(request));
        entity.setItineraryJson(savedItineraryCodec.writeJson(itinerary));
        when(savedItineraryRepository.requireOwned(7L, 99L)).thenReturn(entity);
        when(amapTravelTimeService.estimateTravelLeg(any(), any(), eq(TravelModeRequest.AUTO))).thenReturn(new TravelTimeService.TravelLegEstimate(
                18,
                BigDecimal.valueOf(6.2D),
                "??"
        ));
        when(savedItineraryCommandService.save(eq(7L), eq(99L), any(GenerateReqDTO.class), any(ItineraryVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(3));

        ItineraryVO updated = useCase.calculate(7L, 99L, 2);

        assertThat(updated.getNodes().get(2).getDepartureTravelTime()).isEqualTo(18);
        assertThat(updated.getNodes().get(2).getDepartureDistanceKm()).isEqualByComparingTo("6.2");
        assertThat(updated.getNodes().get(2).getDepartureTransportMode()).isEqualTo("??");
        verify(amapTravelTimeService).estimateTravelLeg(
                argThat(from -> from != null && "??".equals(from.getName())),
                argThat(to -> to != null && Long.valueOf(3L).equals(to.getId())),
                eq(TravelModeRequest.AUTO)
        );
    }

    @Test
    void calculateShouldKeepAutoRecommendationWhileApplyingManualTaxiOverride() {
        SavedItineraryRepository savedItineraryRepository = mock(SavedItineraryRepository.class);
        SavedItineraryCommandService savedItineraryCommandService = mock(SavedItineraryCommandService.class);
        AmapTravelTimeServiceImpl amapTravelTimeService = mock(AmapTravelTimeServiceImpl.class);
        SavedItineraryCodec savedItineraryCodec = new SavedItineraryCodec(new ObjectMapper());
        ItinerarySegmentTravelUseCase useCase = new ItinerarySegmentTravelUseCase(
                savedItineraryRepository,
                savedItineraryCodec,
                savedItineraryCommandService,
                amapTravelTimeService,
                new SegmentRouteGuideService()
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setDeparturePlaceName("??");
        request.setDepartureLatitude(30.65D);
        request.setDepartureLongitude(104.06D);
        request.setStartTime("09:00");

        ItineraryNodeVO firstNode = node(1L, 1, 1, "A", 30.652D, 104.062D);
        firstNode.setTravelTime(10);
        firstNode.setStayDuration(60);
        ItineraryNodeVO secondNode = node(2L, 1, 2, "B", 30.66D, 104.072D);
        secondNode.setStayDuration(90);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setId(99L);
        itinerary.setNodes(List.of(firstNode, secondNode));

        SavedItinerary entity = new SavedItinerary();
        entity.setId(99L);
        entity.setUserId(7L);
        entity.setRequestJson(savedItineraryCodec.writeJson(request));
        entity.setItineraryJson(savedItineraryCodec.writeJson(itinerary));
        when(savedItineraryRepository.requireOwned(7L, 99L)).thenReturn(entity);
        when(amapTravelTimeService.estimateTravelLeg(any(), any(), eq(TravelModeRequest.AUTO)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(18, BigDecimal.valueOf(3.2D), "??+??"));
        when(amapTravelTimeService.estimateTravelLeg(any(), any(), eq(TravelModeRequest.TAXI)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(9, BigDecimal.valueOf(3.2D), "??"));
        when(savedItineraryCommandService.save(eq(7L), eq(99L), any(GenerateReqDTO.class), any(ItineraryVO.class)))
                .thenAnswer(invocation -> invocation.getArgument(3));

        ItineraryVO updated = useCase.calculate(7L, 99L, 1, new SegmentTravelReqDTO("taxi"));

        assertThat(updated.getNodes().get(1).getTravelTransportMode()).isEqualTo("??");
        assertThat(updated.getNodes().get(1).getSegmentRouteGuide().getRecommendedTransportMode())
                .isEqualTo("??+??");
        assertThat(updated.getNodes().get(1).getSegmentRouteGuide().getTransportMode())
                .isEqualTo("??");
        verify(amapTravelTimeService).estimateTravelLeg(any(), any(), eq(TravelModeRequest.AUTO));
        verify(amapTravelTimeService).estimateTravelLeg(any(), any(), eq(TravelModeRequest.TAXI));
    }

    private ItineraryNodeVO node(Long poiId, int dayNo, int stepOrder, String name, double latitude, double longitude) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiId(poiId);
        node.setDayNo(dayNo);
        node.setStepOrder(stepOrder);
        node.setPoiName(name);
        node.setLatitude(BigDecimal.valueOf(latitude));
        node.setLongitude(BigDecimal.valueOf(longitude));
        return node;
    }

    private GeoPoint point(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}

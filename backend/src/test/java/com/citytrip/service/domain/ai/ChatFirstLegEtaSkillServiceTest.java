package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatFirstLegEtaSkillServiceTest {

    @Test
    void shouldUseGeoRouteEstimateForFirstLeg() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        when(geoSearchService.estimateTravel(any(GeoPoint.class), any(GeoPoint.class), eq("Chengdu"), eq(null)))
                .thenReturn(Optional.of(new GeoRouteEstimate(18, BigDecimal.valueOf(4.2), "driving")));
        ChatFirstLegEtaSkillService service = new ChatFirstLegEtaSkillService(geoSearchService);

        ChatReqDTO req = buildReq("How long to first stop?");
        ChatRouteContextSkillService.RouteContext routeContext = buildRouteContext();

        ChatFirstLegEtaSkillService.FirstLegEstimate estimate = service.estimate(req, routeContext);
        ChatFirstLegEtaSkillService.SkillResult result = service.tryHandle(buildReq("How long to first stop right now?"), routeContext);

        assertThat(estimate).isNotNull();
        assertThat(estimate.estimatedMinutes()).isEqualTo(18);
        assertThat(estimate.estimatedDistanceKm()).isEqualByComparingTo("4.2");
        assertThat(estimate.transportMode()).isNotBlank();
        assertThat(estimate.source()).isEqualTo("geo-route-api");

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("geo-route-api");
        assertThat(result.answer()).contains("Kuanzhai Alley", "18", "4.2");
        assertThat(result.usedSkills()).contains("FirstLegPreciseETASkill");
    }

    @Test
    void shouldFallbackToRouteContextWhenGeoUnavailable() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        when(geoSearchService.estimateTravel(any(GeoPoint.class), any(GeoPoint.class), eq("Chengdu"), eq(null)))
                .thenReturn(Optional.empty());
        ChatFirstLegEtaSkillService service = new ChatFirstLegEtaSkillService(geoSearchService);

        ChatReqDTO req = buildReq("How to get to the first stop?");
        ChatReqDTO.ChatRouteNode firstNode = new ChatReqDTO.ChatRouteNode();
        firstNode.setPoiName("Kuanzhai Alley");
        firstNode.setLatitude(BigDecimal.valueOf(30.67));
        firstNode.setLongitude(BigDecimal.valueOf(104.04));
        firstNode.setDepartureTravelTime(14);
        firstNode.setDepartureDistanceKm(BigDecimal.valueOf(2.1));
        firstNode.setDepartureTransportMode("metro");

        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "A",
                "summary",
                List.of(firstNode)
        );

        ChatFirstLegEtaSkillService.FirstLegEstimate estimate = service.estimate(req, routeContext);

        assertThat(estimate).isNotNull();
        assertThat(estimate.estimatedMinutes()).isEqualTo(14);
        assertThat(estimate.estimatedDistanceKm()).isEqualByComparingTo("2.1");
        assertThat(estimate.transportMode()).isNotBlank();
        assertThat(estimate.source()).isEqualTo("route-context");
    }

    @Test
    void shouldProvideTimeWindowAndMultiModePlanForFirstLeg() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        when(geoSearchService.estimateTravel(any(GeoPoint.class), any(GeoPoint.class), eq("Chengdu"), nullable(String.class)))
                .thenAnswer(invocation -> {
                    String preferredMode = invocation.getArgument(3);
                    if ("walk".equals(preferredMode)) {
                        return Optional.of(new GeoRouteEstimate(42, BigDecimal.valueOf(2.8), "walking"));
                    }
                    if ("bus".equals(preferredMode)) {
                        return Optional.of(new GeoRouteEstimate(26, BigDecimal.valueOf(5.3), "bus"));
                    }
                    if ("metro".equals(preferredMode)) {
                        return Optional.of(new GeoRouteEstimate(18, BigDecimal.valueOf(6.0), "metro"));
                    }
                    if ("taxi".equals(preferredMode)) {
                        return Optional.of(new GeoRouteEstimate(11, BigDecimal.valueOf(6.4), "taxi"));
                    }
                    return Optional.of(new GeoRouteEstimate(20, BigDecimal.valueOf(6.2), "transit"));
                });

        ChatFirstLegEtaSkillService service = new ChatFirstLegEtaSkillService(geoSearchService);
        ChatReqDTO req = buildReq("From here to first stop, which mode is best?");

        ChatReqDTO.ChatRouteNode firstNode = new ChatReqDTO.ChatRouteNode();
        firstNode.setPoiName("Wuhou Shrine");
        firstNode.setLatitude(BigDecimal.valueOf(30.6518));
        firstNode.setLongitude(BigDecimal.valueOf(104.0476));
        firstNode.setStartTime(LocalTime.now().plusMinutes(20).format(DateTimeFormatter.ofPattern("HH:mm")));

        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "B",
                "summary",
                List.of(firstNode)
        );

        ChatFirstLegEtaSkillService.SkillResult result = service.tryHandle(req, routeContext);
        List<String> evidence = service.buildEvidence(req, routeContext);

        assertThat(result).isNotNull();
        assertThat(result.source()).isEqualTo("geo-route-api");
        assertThat(result.answer()).contains("Wuhou Shrine");
        assertThat(result.usedSkills()).contains("FirstLegPreciseETASkill");

        assertThat(evidence).anyMatch(item -> item.startsWith("first_stop=Wuhou Shrine"));
        assertThat(evidence).anyMatch(item -> item.startsWith("first_leg_time_window_min="));
        assertThat(evidence).anyMatch(item -> item.startsWith("first_leg_option_1="));
        assertThat(evidence).anyMatch(item -> item.startsWith("first_leg_recommend="));
    }

    private ChatReqDTO buildReq(String question) {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("Chengdu");
        context.setUserLat(30.66D);
        context.setUserLng(104.06D);
        req.setContext(context);
        return req;
    }

    private ChatRouteContextSkillService.RouteContext buildRouteContext() {
        ChatReqDTO.ChatRouteNode firstNode = new ChatReqDTO.ChatRouteNode();
        firstNode.setPoiName("Kuanzhai Alley");
        firstNode.setLatitude(BigDecimal.valueOf(30.67));
        firstNode.setLongitude(BigDecimal.valueOf(104.04));
        return new ChatRouteContextSkillService.RouteContext("A", "summary", List.of(firstNode));
    }
}
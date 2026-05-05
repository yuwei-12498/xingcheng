package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatSegmentTransportSkillServiceTest {

    @Test
    void shouldRenderPerSegmentTransportFromCurrentRouteContext() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        when(geoSearchService.estimateTravel(any(GeoPoint.class), any(GeoPoint.class), eq("Chengdu"), eq(null)))
                .thenReturn(Optional.of(new GeoRouteEstimate(12, BigDecimal.valueOf(1.5), "walking")));
        ChatFirstLegEtaSkillService firstLegEtaSkillService = new ChatFirstLegEtaSkillService(geoSearchService);
        ChatSegmentTransportSkillService service = new ChatSegmentTransportSkillService(firstLegEtaSkillService);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("Show transport mode for each leg");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("Chengdu");
        context.setUserLat(30.66D);
        context.setUserLng(104.06D);
        req.setContext(context);

        ChatReqDTO.ChatRouteNode n1 = new ChatReqDTO.ChatRouteNode();
        n1.setPoiName("Kuanzhai Alley");
        n1.setLatitude(BigDecimal.valueOf(30.67));
        n1.setLongitude(BigDecimal.valueOf(104.04));

        ChatReqDTO.ChatRouteNode n2 = new ChatReqDTO.ChatRouteNode();
        n2.setPoiName("Wuhou Shrine");
        n2.setTravelTransportMode("metro");
        n2.setTravelTime(22);
        n2.setTravelDistanceKm(BigDecimal.valueOf(4.6));

        ChatReqDTO.ChatRouteNode n3 = new ChatReqDTO.ChatRouteNode();
        n3.setPoiName("Taikoo Li");
        n3.setTravelTransportMode("taxi");
        n3.setTravelTime(16);
        n3.setTravelDistanceKm(BigDecimal.valueOf(5.8));

        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "B",
                "city culture line",
                List.of(n1, n2, n3)
        );

        ChatSegmentTransportSkillService.SkillResult result = service.tryHandle(req, routeContext);

        assertThat(result).isNotNull();
        assertThat(result.answer()).contains("Kuanzhai Alley", "Wuhou Shrine", "Taikoo Li");
        assertThat(result.answer()).contains("12", "22", "16");
        assertThat(result.usedSkills()).contains("SegmentTransportSkill", "FirstLegPreciseETASkill");
        assertThat(result.source()).isEqualTo("geo-route-api");

        List<String> evidence = service.buildEvidence(routeContext, firstLegEtaSkillService.estimate(req, routeContext));
        assertThat(evidence).contains("segment_count=3");
        assertThat(evidence).anyMatch(item -> item.startsWith("segment_1="));
        assertThat(evidence).anyMatch(item -> item.startsWith("segment_2="));
        assertThat(evidence).anyMatch(item -> item.startsWith("segment_3="));
    }
}
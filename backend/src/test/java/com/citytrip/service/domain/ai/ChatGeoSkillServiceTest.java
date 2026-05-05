package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ChatGeoSkillServiceTest {

    private GeoSearchService geoSearchService;
    private PlaceDisambiguationService placeDisambiguationService;
    private ChatGeoSkillService service;

    @BeforeEach
    void setUp() {
        geoSearchService = mock(GeoSearchService.class);
        placeDisambiguationService = mock(PlaceDisambiguationService.class);
        service = new ChatGeoSkillService(geoSearchService, placeDisambiguationService);
    }

    @Test
    void shouldReturnEmptyWhenQuestionHasNoGeoIntent() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("推荐一个适合情侣约会的晚饭地点");

        ChatGeoSkillService.GeoFactsResult result = service.collectFacts(req, List.of());

        assertThat(result.geoIntent()).isFalse();
        assertThat(result.facts()).isEmpty();
        assertThat(result.clarificationQuestion()).isNull();
        verifyNoInteractions(placeDisambiguationService, geoSearchService);
    }

    @Test
    void shouldReturnClarificationQuestionWhenDisambiguationConfidenceIsLow() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("IFS 在哪，怎么去");

        PlaceDisambiguationService.PlaceResolution resolution = new PlaceDisambiguationService.PlaceResolution(
                null,
                List.of(),
                0.2D,
                true,
                "你指的是 A 还是 B？"
        );
        when(placeDisambiguationService.disambiguate(anyString(), isNull(), isNull())).thenReturn(resolution);

        ChatGeoSkillService.GeoFactsResult result = service.collectFacts(req, List.of());

        assertThat(result.geoIntent()).isTrue();
        assertThat(result.facts()).isEmpty();
        assertThat(result.clarificationQuestion()).isEqualTo("你指的是 A 还是 B？");
        verifyNoInteractions(geoSearchService);
    }

    @Test
    void shouldMergeLocalAndExternalFactsForGeoQuestion() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("春熙路附近有什么，离我多远");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        context.setUserLat(30.657D);
        context.setUserLng(104.065D);
        req.setContext(context);

        Poi local = new Poi();
        local.setName("春熙路");
        local.setCategory("商圈");
        local.setCityName("成都");
        local.setDistrict("锦江");
        local.setLatitude(BigDecimal.valueOf(30.657));
        local.setLongitude(BigDecimal.valueOf(104.065));

        PlaceDisambiguationService.ResolvedPlace best = new PlaceDisambiguationService.ResolvedPlace(
                "春熙路",
                "成都",
                "锦江",
                "商圈",
                BigDecimal.valueOf(30.657),
                BigDecimal.valueOf(104.065),
                "external",
                0.92D
        );
        when(placeDisambiguationService.disambiguate(any(), any(), any()))
                .thenReturn(new PlaceDisambiguationService.PlaceResolution(
                        best,
                        List.of(best),
                        0.9D,
                        false,
                        null
                ));

        GeoPoiCandidate nearby = new GeoPoiCandidate();
        nearby.setName("太古里");
        nearby.setCategory("商圈");
        nearby.setCityName("成都");
        nearby.setDistrict("锦江");
        nearby.setLatitude(BigDecimal.valueOf(30.654));
        nearby.setLongitude(BigDecimal.valueOf(104.079));
        nearby.setSource("nearby");

        when(geoSearchService.searchNearby(any(), eq("成都"), eq(null), anyInt(), anyInt()))
                .thenReturn(List.of(nearby));
        when(geoSearchService.searchByKeyword(anyString(), eq("成都"), anyInt()))
                .thenReturn(List.of(nearby));

        ChatGeoSkillService.GeoFactsResult result = service.collectFacts(req, List.of(local));

        assertThat(result.geoIntent()).isTrue();
        assertThat(result.clarificationQuestion()).isNull();
        assertThat(result.facts()).isNotEmpty();
        assertThat(result.facts().stream().map(ChatGeoSkillService.GeoFact::name))
                .contains("春熙路", "太古里");
    }

    @Test
    void shouldIncludeRouteNodesFromChatContextAsFacts() {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("help me check this route");

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("Chengdu");
        context.setUserLat(30.66D);
        context.setUserLng(104.06D);

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
        node.setPoiName("Kuanzhai Alley");
        node.setCategory("district");
        node.setDistrict("Qingyang");
        node.setLatitude(BigDecimal.valueOf(30.67D));
        node.setLongitude(BigDecimal.valueOf(104.04D));
        itinerary.setNodes(List.of(node));
        context.setItinerary(itinerary);

        req.setContext(context);

        ChatGeoSkillService.GeoFactsResult result = service.collectFacts(req, List.of());

        assertThat(result.geoIntent()).isFalse();
        assertThat(result.facts().stream().map(ChatGeoSkillService.GeoFact::name)).contains("Kuanzhai Alley");
        assertThat(result.facts().stream().map(ChatGeoSkillService.GeoFact::source)).contains("itinerary-route");
        verifyNoInteractions(placeDisambiguationService, geoSearchService);
    }

}

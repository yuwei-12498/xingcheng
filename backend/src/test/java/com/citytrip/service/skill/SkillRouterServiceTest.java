package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.application.itinerary.ChatItineraryEditWorkflowService;
import com.citytrip.service.application.itinerary.ChatReplacementWorkflowService;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SkillRouterServiceTest {

    @Test
    void nearbyHotelSkillShouldSupportHotelQuestion() {
        NearbyHotelSkill skill = new NearbyHotelSkill(
                mock(GeoSearchService.class),
                mock(PoiService.class),
                mock(PlaceDisambiguationService.class)
        );

        assertThat(skill.supports(chatReq("推荐成都大学附近酒店", false))).isTrue();
    }

    @Test
    void nearbyPoiSkillShouldSupportNearbySightQuestion() {
        NearbyPoiSkill skill = new NearbyPoiSkill(
                mock(GeoSearchService.class),
                mock(PoiService.class),
                mock(PlaceDisambiguationService.class)
        );

        assertThat(skill.supports(chatReq("推荐成都大学附近景点", false))).isTrue();
    }

    @Test
    void poiSearchSkillShouldSupportVisitQuestion() {
        PoiSearchSkill skill = new PoiSearchSkill(mock(PoiService.class));

        assertThat(skill.supports(chatReq("我想去成都大学", false))).isTrue();
    }

    @Test
    void itineraryReplaceSkillShouldSupportReplaceQuestionWhenItineraryExists() {
        ChatReplacementWorkflowService workflowService = mock(ChatReplacementWorkflowService.class);
        ItineraryReplaceSkill skill = new ItineraryReplaceSkill(workflowService);

        assertThat(skill.supports(chatReq("把第二站换成安静一点的咖啡馆", true))).isTrue();
    }

    @Test
    void skillRouterShouldPickNearbyHotelSkillForHotelQuestion() {
        NearbyHotelSkill nearbyHotelSkill = new NearbyHotelSkill(
                mock(GeoSearchService.class),
                mock(PoiService.class),
                mock(PlaceDisambiguationService.class)
        );
        SkillRouterService router = new SkillRouterService(List.of(
                new RouteContextSkill(),
                new ItineraryEditSkill(mock(ChatItineraryEditWorkflowService.class)),
                new ItineraryReplaceSkill(mock(ChatReplacementWorkflowService.class)),
                nearbyHotelSkill,
                new NearbyPoiSkill(mock(GeoSearchService.class), mock(PoiService.class), mock(PlaceDisambiguationService.class)),
                new PoiSearchSkill(mock(PoiService.class))
        ));

        Optional<ChatSkillPayloadVO> routed = router.route(chatReq("推荐成都大学附近酒店", false));

        assertThat(routed).isPresent();
        assertThat(routed.get().getSkillName()).isEqualTo("nearby_hotel");
    }

    private ChatReqDTO chatReq(String question, boolean withItinerary) {
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        if (withItinerary) {
            ChatReqDTO.ChatRouteNode node1 = new ChatReqDTO.ChatRouteNode();
            node1.setPoiId(1L);
            node1.setPoiName("文殊院");
            node1.setCategory("宗教文化");
            node1.setDistrict("青羊区");
            node1.setLatitude(new BigDecimal("30.6765"));
            node1.setLongitude(new BigDecimal("104.0753"));

            ChatReqDTO.ChatRouteNode node2 = new ChatReqDTO.ChatRouteNode();
            node2.setPoiId(2L);
            node2.setPoiName("武侯祠");
            node2.setCategory("文化古迹");
            node2.setDistrict("武侯区");
            node2.setLatitude(new BigDecimal("30.6466"));
            node2.setLongitude(new BigDecimal("104.0430"));

            ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
            itinerary.setItineraryId(99L);
            itinerary.setSummary("测试路线");
            itinerary.setNodes(List.of(node1, node2));
            context.setItinerary(itinerary);
        }
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        req.setContext(context);
        return req;
    }
}

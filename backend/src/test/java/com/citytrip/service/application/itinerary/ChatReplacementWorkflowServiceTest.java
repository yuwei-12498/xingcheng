package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatReplacementWorkflowServiceTest {

    @Test
    void handle_shouldAskWhichStopToReplaceWhenOnlyDestinationIsProvided() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("成都大学", "成都", 8))
                .thenReturn(List.of(poi("成都大学", "高校", "vivo-geo")));

        ChatSkillPayloadVO payload = service.handle(chatReq("我想去成都大学玩", "session-1", "成都", routeNode("圣灯公园"), routeNode("建设路小吃街")));

        assertThat(payload.getMessageType()).isEqualTo("workflow");
        assertThat(payload.getWorkflowType()).isEqualTo("itinerary_replace");
        assertThat(payload.getStatus()).isEqualTo("clarification_required");
        assertThat(payload.getFallbackMessage()).contains("想换掉哪一站");
        assertThat(payload.getFallbackMessage()).contains("圣灯公园");
        assertThat(payload.getFallbackMessage()).contains("建设路小吃街");
    }

    @Test
    void handle_shouldBuildOneToOneProposalAndPersistPendingProposal() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("成都大学", "成都", 8))
                .thenReturn(List.of(
                        poi("成都大学", "高校", "vivo-geo"),
                        poi("成都大学望江校区", "高校", "vivo-geo")
                ));

        ChatSkillPayloadVO payload = service.handle(chatReq("把圣灯公园换成成都大学", "session-1", "成都", routeNode("圣灯公园"), routeNode("建设路小吃街")));

        assertThat(payload.getMessageType()).isEqualTo("workflow");
        assertThat(payload.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(payload.getProposalToken()).isNotBlank();
        assertThat(payload.getClientSessionId()).isEqualTo("session-1");
        assertThat(payload.getFallbackMessage()).contains("圣灯公园");
        assertThat(payload.getFallbackMessage()).contains("成都大学");
        assertThat(payload.getItineraryEditDraft()).isNotNull();
        assertThat(payload.getItineraryEditDraft().getOperations())
                .extracting(ItineraryEditOperationDTO::getType)
                .containsExactly("remove_node", "insert_inline_custom_poi");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getKey)
                .contains("confirm_replacement", "regenerate_replacement", "decline_replacement");
        assertThat(store.getPendingProposal("session-1", payload.getProposalToken())).isPresent();
    }

    @Test
    void handle_shouldBuildProposalWhenOrdinalStopIsSpecified() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("安静一点的咖啡馆", "成都", 8))
                .thenReturn(List.of(
                        poi("墨菲的咖啡馆", "咖啡馆", "vivo-geo"),
                        poi("阿达的咖啡馆", "咖啡馆", "vivo-geo")
                ));

        ChatSkillPayloadVO payload = service.handle(chatReq(
                "把第二站换成安静一点的咖啡馆",
                "session-ordinal",
                "成都",
                routeNode("成都博物馆", 1),
                routeNode("宽窄巷子", 2),
                routeNode("武侯祠", 3)
        ));

        assertThat(payload.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(payload.getFallbackMessage()).contains("宽窄巷子");
        assertThat(payload.getFallbackMessage()).contains("墨菲的咖啡馆");
        assertThat(payload.getReplacementProposal()).isNotNull();
        assertThat(payload.getReplacementProposal().getTargetPoiNames()).containsExactly("宽窄巷子");
    }

    @Test
    void handle_shouldPreferNearbyInterestMatchedCandidatesAndFilterFarOnes() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("安静一点的咖啡馆", "成都", 8))
                .thenReturn(List.of(
                        poiWithLatLng("远郊咖啡庄园", "咖啡馆", "vivo-geo", "30.900000", "104.500000"),
                        poiWithLatLng("街角咖啡馆", "咖啡馆", "vivo-geo", "30.671000", "104.121000"),
                        poiWithLatLng("人民公园", "公园", "vivo-geo", "30.668800", "104.118900")
                ));

        ChatSkillPayloadVO payload = service.handle(chatReq(
                "把第二站换成安静一点的咖啡馆",
                "session-nearby",
                "成都",
                routeNodeWithLatLng("成都博物馆", 1, "30.670000", "104.120000"),
                routeNodeWithLatLng("宽窄巷子", 2, "30.670000", "104.120000"),
                routeNodeWithLatLng("武侯祠", 3, "30.670000", "104.120000")
        ));

        assertThat(payload.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(payload.getFallbackMessage()).contains("街角咖啡馆");
        assertThat(payload.getResults())
                .extracting(ChatSkillPayloadVO.ResultItem::getName)
                .doesNotContain("远郊咖啡庄园")
                .contains("街角咖啡馆");
    }

    @Test
    void handle_shouldReturnAlternativeCandidateForRegenerateAction() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("成都大学", "成都", 8))
                .thenReturn(List.of(
                        poi("成都大学", "高校", "vivo-geo"),
                        poi("成都大学望江校区", "高校", "vivo-geo")
                ));

        ChatSkillPayloadVO first = service.handle(chatReq("把圣灯公园换成成都大学", "session-1", "成都", routeNode("圣灯公园")));

        ChatReqDTO regenerateReq = chatReq("换个方案", "session-1", "成都", routeNode("圣灯公园"));
        regenerateReq.getAction().setType("regenerate_replacement");
        regenerateReq.getAction().setProposalToken(first.getProposalToken());

        ChatSkillPayloadVO second = service.handle(regenerateReq);

        assertThat(second.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(second.getFallbackMessage()).contains("成都大学望江校区");
        assertThat(second.getProposalToken()).isNotEqualTo(first.getProposalToken());
    }

    @Test
    void handle_shouldCarryResolvedGeoFieldsInInsertDraft() {
        PoiService poiService = mock(PoiService.class);
        ChatReplacementSessionStore store = new ChatReplacementSessionStore();
        ChatReplacementWorkflowService service = new ChatReplacementWorkflowService(poiService, store);

        when(poiService.searchLive("TargetCampus", "Chengdu", 8))
                .thenReturn(List.of(new PoiSearchResultVO(
                        "TargetCampus",
                        "No. 2025 Chengluo Avenue",
                        "University",
                        new BigDecimal("30.650035"),
                        new BigDecimal("104.187516"),
                        "Chengdu",
                        "CD",
                        "vivo-geo"
                )));

        ChatSkillPayloadVO payload = service.handle(chatReq("把 Stop-A 换成 TargetCampus", "session-2", "Chengdu", routeNode("Stop-A")));

        ItineraryEditOperationDTO.CustomPoiDraft draft = payload.getItineraryEditDraft()
                .getOperations()
                .stream()
                .filter(operation -> "insert_inline_custom_poi".equals(operation.getType()))
                .findFirst()
                .orElseThrow()
                .getCustomPoiDraft();

        assertThat(draft.getAddress()).isEqualTo("No. 2025 Chengluo Avenue");
        assertThat(draft.getLatitude()).isEqualByComparingTo("30.650035");
        assertThat(draft.getLongitude()).isEqualByComparingTo("104.187516");
        assertThat(draft.getGeoSource()).isEqualTo("vivo-geo");
    }

    private ChatReqDTO chatReq(String question,
                               String clientSessionId,
                               String cityName,
                               ChatReqDTO.ChatRouteNode... nodes) {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);

        ChatReqDTO.ChatAction action = new ChatReqDTO.ChatAction();
        action.setClientSessionId(clientSessionId);
        req.setAction(action);

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setItineraryId(88L);
        itinerary.setNodes(List.of(nodes));

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName(cityName);
        context.setItinerary(itinerary);
        req.setContext(context);
        return req;
    }

    private ChatReqDTO.ChatRouteNode routeNode(String name) {
        return routeNode(name, 1);
    }

    private ChatReqDTO.ChatRouteNode routeNode(String name, int stepOrder) {
        return routeNodeWithLatLng(name, stepOrder, "30.670000", "104.120000");
    }

    private ChatReqDTO.ChatRouteNode routeNodeWithLatLng(String name,
                                                         int stepOrder,
                                                         String lat,
                                                         String lng) {
        ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
        node.setNodeKey("node-" + Math.abs(name.hashCode()));
        node.setDayNo(1);
        node.setStepOrder(stepOrder);
        node.setPoiId((long) Math.abs(name.hashCode()));
        node.setPoiName(name);
        node.setStayDuration(90);
        node.setCategory("景点");
        node.setDistrict("成华区");
        node.setLatitude(new BigDecimal(lat));
        node.setLongitude(new BigDecimal(lng));
        return node;
    }

    private PoiSearchResultVO poi(String name, String category, String source) {
        return poiWithLatLng(name, category, source, "30.670000", "104.120000");
    }

    private PoiSearchResultVO poiWithLatLng(String name,
                                            String category,
                                            String source,
                                            String lat,
                                            String lng) {
        return new PoiSearchResultVO(
                name,
                name + "地址",
                category,
                new BigDecimal(lat),
                new BigDecimal(lng),
                "成都",
                "510100",
                source
        );
    }
}

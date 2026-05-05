package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.application.itinerary.ChatItineraryEditWorkflowService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class ItineraryEditSkillTest {

    @Test
    void supportsShouldRecognizeShortJoinPoiRequest() {
        ItineraryEditSkill skill = new ItineraryEditSkill(mock(ChatItineraryEditWorkflowService.class));

        assertThat(skill.supports(req("加入IFS就行了"))).isTrue();
    }

    @Test
    void supportsShouldRecognizeStopCountChangeWithAddIntent() {
        ItineraryEditSkill skill = new ItineraryEditSkill(mock(ChatItineraryEditWorkflowService.class));

        assertThat(skill.supports(req("我要从4站变成5站，加入IFS"))).isTrue();
    }

    @Test
    void supportsShouldRecognizeNaturalVisitWishAsEdit() {
        ItineraryEditSkill skill = new ItineraryEditSkill(mock(ChatItineraryEditWorkflowService.class));

        assertThat(skill.supports(req("我想去成都动物园"))).isTrue();
        assertThat(skill.supports(req("顺便带上宽窄巷子吧"))).isTrue();
    }

    private ChatReqDTO req(String question) {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
        node.setPoiName("建设路小吃街");
        node.setNodeKey("node-a");
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setNodes(List.of(node));
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setItinerary(itinerary);
        req.setContext(context);
        return req;
    }
}

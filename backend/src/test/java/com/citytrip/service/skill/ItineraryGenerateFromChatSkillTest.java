package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.application.itinerary.ChatItineraryGenerateDraftService;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.same;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryGenerateFromChatSkillTest {

    @Test
    void supportsResultPageGenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "就按刚才聊的偏好生成路线"))).isTrue();
    }

    @Test
    void supportsResultPagePureRegenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "重新生成路线"))).isTrue();
    }

    @Test
    void rejectsResultPageReplacementRegenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "把宽窄巷子替换成锦里后重新生成路线"))).isFalse();
    }

    @Test
    void rejectsResultPageDeletionRegenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "删除最后一站并重新生成路线"))).isFalse();
    }

    @Test
    void rejectsResultPageTimeAdjustmentRegenerateIntent() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "把出发时间调整到10:00后重新生成路线"))).isFalse();
    }

    @Test
    void rejectsNonResultPage() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("home", "就按刚才聊的偏好生成路线"))).isFalse();
    }

    @Test
    void rejectsConfirmationWithoutGenerateActionVerb() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));

        assertThat(skill.supports(request("result", "按这个路线走会不会太累？"))).isFalse();
    }

    @Test
    void rejectsActionDrivenReplacementRegenerate() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));
        ChatReqDTO req = request("result", "重新生成");
        ChatReqDTO.ChatAction action = new ChatReqDTO.ChatAction();
        action.setType("regenerate_replacement");
        req.setAction(action);

        assertThat(skill.supports(req)).isFalse();
    }

    @Test
    void rejectsNullContextAndQuestion() {
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(mock(ChatItineraryGenerateDraftService.class));
        ChatReqDTO noContext = new ChatReqDTO();
        noContext.setQuestion("生成路线");
        ChatReqDTO noQuestion = request("result", null);

        assertThat(skill.supports(null)).isFalse();
        assertThat(skill.supports(noContext)).isFalse();
        assertThat(skill.supports(noQuestion)).isFalse();
    }

    @Test
    void executeDelegatesToDraftService() {
        ChatItineraryGenerateDraftService draftService = mock(ChatItineraryGenerateDraftService.class);
        ItineraryGenerateFromChatSkill skill = new ItineraryGenerateFromChatSkill(draftService);
        ChatReqDTO req = request("result", "重新生成");
        ChatSkillPayloadVO expected = new ChatSkillPayloadVO();
        when(draftService.buildDraft(same(req))).thenReturn(expected);

        ChatSkillPayloadVO actual = skill.execute(req);

        assertThat(actual).isSameAs(expected);
        verify(draftService).buildDraft(same(req));
    }

    private ChatReqDTO request(String pageType, String question) {
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setPageType(pageType);
        req.setContext(context);
        return req;
    }
}

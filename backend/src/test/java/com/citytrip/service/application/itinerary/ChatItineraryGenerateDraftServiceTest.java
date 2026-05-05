package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.SmartFillVO;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatItineraryGenerateDraftServiceTest {

    @Test
    void shouldBuildWorkflowPayloadFromRecentResultPageConversation() {
        SmartFillUseCase smartFillUseCase = mock(SmartFillUseCase.class);
        SmartFillVO parsed = new SmartFillVO();
        parsed.setCityName("成都");
        parsed.setTripDays(2.0D);
        parsed.setStartTime("10:00");
        parsed.setEndTime("20:30");
        parsed.setBudgetLevel("高");
        parsed.setThemes(List.of("美食", "文化", "美食"));
        parsed.setMustVisitPoiNames(List.of("成都博物馆", "宽窄巷子"));
        parsed.setDepartureText("太古里");
        parsed.setSummary(List.of("想加入博物馆和夜景", "预算更高"));
        when(smartFillUseCase.parse(argThat(req -> req != null && req.getText().contains("最近对话"))))
                .thenReturn(parsed);

        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(smartFillUseCase);
        ChatReqDTO req = resultPageRequestWithBase();
        req.setRecentMessages(List.of(
                message("assistant", "当前路线偏休闲，下午安排宽窄巷子。"),
                message("user", "我还想加入成都博物馆，晚上看夜景，预算可以高一点。")
        ));
        req.setQuestion("把这些调整生成一条新路线");

        ChatSkillPayloadVO payload = service.buildDraft(req);

        assertThat(payload.getSkillName()).isEqualTo("itinerary_generate");
        assertThat(payload.getMessageType()).isEqualTo("workflow");
        assertThat(payload.getWorkflowType()).isEqualTo("itinerary_generate");
        assertThat(payload.getWorkflowState()).isEqualTo("proposal_ready");
        assertThat(payload.getStatus()).isEqualTo("proposal_ready");
        assertThat(payload.getIntent()).isEqualTo("itinerary_generate");
        assertThat(payload.getSource()).isEqualTo("result_page_conversation");
        assertThat(payload.getFallbackMessage()).contains("我把这次对话整理成");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getKey)
                .containsExactly("confirm_itinerary_generate", "continue_itinerary_generate");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getLabel)
                .containsExactly("生成路线", "继续补充");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getStyle)
                .containsExactly("primary", "secondary");

        GenerateReqDTO draft = payload.getGenerateDraft();
        assertThat(draft.getCityName()).isEqualTo("成都");
        assertThat(draft.getTripDays()).isEqualTo(2.0D);
        assertThat(draft.getTripDate()).isEqualTo("2026-05-02");
        assertThat(draft.getStartTime()).isEqualTo("10:00");
        assertThat(draft.getEndTime()).isEqualTo("20:30");
        assertThat(draft.getBudgetLevel()).isEqualTo("高");
        assertThat(draft.getTotalBudget()).isEqualTo(800.0D);
        assertThat(draft.getThemes()).containsExactly("休闲", "美食", "文化");
        assertThat(draft.getMustVisitPoiNames()).containsExactly("宽窄巷子", "成都博物馆");
        assertThat(draft.getDeparturePlaceName()).isEqualTo("太古里");
        assertThat(draft.getDepartureLatitude()).isEqualTo(30.657D);
        assertThat(draft.getDepartureLongitude()).isEqualTo(104.081D);
        assertThat(payload.getGenerateSummary()).contains("想加入博物馆和夜景", "预算更高");
        assertThat(payload.getEvidence()).anyMatch(item -> item.contains("recentMessages=2"));

        verify(smartFillUseCase).parse(argThat((SmartFillReqDTO smartReq) ->
                smartReq.getText().contains("成都")
                        && smartReq.getText().contains("宽窄巷子")
                        && smartReq.getText().contains("成都博物馆")
                        && smartReq.getText().length() <= 1000));
    }

    @Test
    void shouldKeepBaseTripDateWhenParsedTripDateIsNotIsoDate() {
        SmartFillUseCase smartFillUseCase = mock(SmartFillUseCase.class);
        SmartFillVO parsed = new SmartFillVO();
        parsed.setTripDate("\u660e\u5929");
        when(smartFillUseCase.parse(argThat(req -> req != null))).thenReturn(parsed);

        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(smartFillUseCase);

        ChatSkillPayloadVO payload = service.buildDraft(resultPageRequestWithBase());

        assertThat(payload.getGenerateDraft().getTripDate()).isEqualTo("2026-05-02");
    }

    @Test
    void shouldClampExtractionTextToSmartFillRequestLimit() {
        SmartFillUseCase smartFillUseCase = mock(SmartFillUseCase.class);
        when(smartFillUseCase.parse(argThat(req -> req != null))).thenReturn(new SmartFillVO());
        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(smartFillUseCase);
        ChatReqDTO req = resultPageRequestWithBase();
        List<ChatReqDTO.ChatMessage> messages = new ArrayList<>();
        for (int i = 0; i < 20; i++) {
            messages.add(message("user", "message-" + i + "-" + "x".repeat(780)));
        }
        req.setRecentMessages(messages);
        req.setQuestion("q".repeat(1200));

        service.buildDraft(req);

        ArgumentCaptor<SmartFillReqDTO> captor = ArgumentCaptor.forClass(SmartFillReqDTO.class);
        verify(smartFillUseCase).parse(captor.capture());
        assertThat(captor.getValue().getText()).hasSizeLessThanOrEqualTo(1000);
    }

    @Test
    void shouldRequestClarificationWhenBaseRequestIsMissingRequiredFields() {
        SmartFillUseCase smartFillUseCase = mock(SmartFillUseCase.class);
        ChatItineraryGenerateDraftService service = new ChatItineraryGenerateDraftService(smartFillUseCase);
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("帮我重新生成路线");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        GenerateReqDTO originalReq = new GenerateReqDTO();
        originalReq.setCityName("成都");
        originalReq.setTripDate("2026-05-02");
        originalReq.setStartTime("09:00");
        context.setOriginalReq(originalReq);
        req.setContext(context);

        ChatSkillPayloadVO payload = service.buildDraft(req);

        assertThat(payload.getSkillName()).isEqualTo("itinerary_generate");
        assertThat(payload.getStatus()).isEqualTo("clarification_required");
        assertThat(payload.getGenerateDraft()).isNull();
        assertThat(payload.getFallbackMessage()).contains("当前路线缺少基础参数");
        assertThat(payload.getActions()).extracting(ChatSkillPayloadVO.ActionItem::getKey)
                .containsExactly("continue_itinerary_generate");
    }

    private static ChatReqDTO resultPageRequestWithBase() {
        GenerateReqDTO base = new GenerateReqDTO();
        base.setCityName("成都");
        base.setCityCode("510100");
        base.setTripDays(1.0D);
        base.setTripDate("2026-05-02");
        base.setStartTime("09:00");
        base.setEndTime("18:00");
        base.setBudgetLevel("中");
        base.setTotalBudget(800.0D);
        base.setThemes(List.of("休闲", "美食"));
        base.setMustVisitPoiNames(List.of("宽窄巷子"));
        base.setDeparturePlaceName("春熙路");
        base.setDepartureLatitude(30.657D);
        base.setDepartureLongitude(104.081D);

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setPageType("result");
        context.setOriginalReq(base);

        ChatReqDTO req = new ChatReqDTO();
        req.setContext(context);
        return req;
    }

    private static ChatReqDTO.ChatMessage message(String role, String content) {
        ChatReqDTO.ChatMessage message = new ChatReqDTO.ChatMessage();
        message.setRole(role);
        message.setContent(content);
        return message;
    }
}

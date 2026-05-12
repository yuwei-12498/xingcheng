package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ai.runtime.AiChatAugmentationContext;
import com.citytrip.service.ai.runtime.AiChatAugmentationService;
import com.citytrip.service.domain.ai.ChatEvidenceSkillService;
import com.citytrip.service.domain.ai.ChatPoiSkillService;
import com.citytrip.service.skill.SkillRouterService;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentCaptor.forClass;
import org.mockito.ArgumentCaptor;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RealChatGatewayServiceTest {

    @Test
    void streamAnswer_shouldReturnLocalWorkflowProposalWithoutCallingModel() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder safePromptBuilder = mock(SafePromptBuilder.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        SkillRouterService skillRouterService = mock(SkillRouterService.class);

        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().getChat().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "skillRouterService", skillRouterService);

        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName("itinerary_edit");
        payload.setMessageType("workflow");
        payload.setWorkflowType("itinerary_edit");
        payload.setWorkflowState("proposal_ready");
        payload.setFallbackMessage("本次将这样修改：\n1. 把宽窄巷子停留调整为 60 分钟\n如你同意，我就应用到当前行程。");

        when(skillRouterService.route(any(ChatReqDTO.class))).thenReturn(Optional.of(payload));

        List<String> streamedTokens = new ArrayList<>();
        ChatVO result = service.streamAnswer(editRequest(), streamedTokens::add);

        assertThat(result.getAnswer()).isEqualTo(payload.getFallbackMessage());
        assertThat(result.getSkillPayload()).isSameAs(payload);
        assertThat(streamedTokens).containsExactly(payload.getFallbackMessage());
        verifyNoInteractions(gatewayClient);
        verifyNoInteractions(safePromptBuilder);
        verifyNoInteractions(chatPoiSkillService);
    }

    @Test
    void answerQuestionShouldUseOnlineFollowUpTipsWhenAvailable() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder safePromptBuilder = mock(SafePromptBuilder.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        RealLlmGatewayService realLlmGatewayService = mock(RealLlmGatewayService.class);

        LlmProperties properties = buildReadyProperties();

        when(safePromptBuilder.buildChatSystemPrompt()).thenReturn("system");
        when(safePromptBuilder.buildChatUserPrompt(
                any(ChatReqDTO.class),
                anyList(),
                anyList(),
                any(AiChatAugmentationContext.class)
        )).thenReturn("user");
        when(chatPoiSkillService.loadRelevantPois(any(ChatReqDTO.class))).thenReturn(List.of());
        when(gatewayClient.request(any(), any(), any())).thenReturn("给你一条建议");
        when(realLlmGatewayService.generateChatFollowUpTips("成都夜游怎么安排", "成都"))
                .thenReturn(List.of("晚上哪一站更适合夜景？", "哪一段最适合打车？", "雨天要不要换点位？", "这条不该保留"));

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "realLlmGatewayService", realLlmGatewayService);

        ChatVO result = service.answerQuestion(chatRequest("成都夜游怎么安排", "成都"));

        assertThat(result.getRelatedTips()).containsExactly(
                "晚上哪一站更适合夜景？",
                "哪一段最适合打车？",
                "雨天要不要换点位？"
        );
    }

    @Test
    void answerQuestionShouldFallbackToTemplateTipsWhenOnlineTipsFail() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder safePromptBuilder = mock(SafePromptBuilder.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        RealLlmGatewayService realLlmGatewayService = mock(RealLlmGatewayService.class);

        LlmProperties properties = buildReadyProperties();

        when(safePromptBuilder.buildChatSystemPrompt()).thenReturn("system");
        when(safePromptBuilder.buildChatUserPrompt(
                any(ChatReqDTO.class),
                anyList(),
                anyList(),
                any(AiChatAugmentationContext.class)
        )).thenReturn("user");
        when(chatPoiSkillService.loadRelevantPois(any(ChatReqDTO.class))).thenReturn(List.of());
        when(gatewayClient.request(any(), any(), any())).thenReturn("下雨天建议走室内路线");
        when(realLlmGatewayService.generateChatFollowUpTips("成都雨天怎么玩", "成都"))
                .thenThrow(new IllegalStateException("text model down"));

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "realLlmGatewayService", realLlmGatewayService);

        ChatVO result = service.answerQuestion(chatRequest("成都雨天怎么玩", "成都"));

        assertThat(result.getRelatedTips()).containsExactly(
                "雨天成都有哪些室内可逛点？",
                "雨天路线怎么减少步行？"
        );
    }

    @Test
    void answerQuestionShouldSkipLivePoiPrefetchWhenPoiLiveFeatureDisabled() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder safePromptBuilder = mock(SafePromptBuilder.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        com.citytrip.service.impl.vivo.VivoFunctionCallingService vivoFunctionCallingService =
                mock(com.citytrip.service.impl.vivo.VivoFunctionCallingService.class);

        LlmProperties properties = buildReadyProperties();
        properties.getFeatures().setPoiLiveEnabled(false);

        when(safePromptBuilder.buildChatSystemPrompt()).thenReturn("system");
        when(safePromptBuilder.buildChatUserPrompt(
                any(ChatReqDTO.class),
                anyList(),
                anyList(),
                any(AiChatAugmentationContext.class)
        )).thenReturn("user");
        when(chatPoiSkillService.loadRelevantPois(any(ChatReqDTO.class))).thenReturn(List.of());
        when(gatewayClient.request(any(), any(), any())).thenReturn("附近有不少地方可以去");
        when(vivoFunctionCallingService.shouldEnterToolLoop(any())).thenReturn(false);

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "vivoFunctionCallingService", vivoFunctionCallingService);

        service.answerQuestion(chatRequest("太古里附近有什么好玩的", "成都"));

        verify(vivoFunctionCallingService, never()).executeToolCall(any(), any());
    }

    @Test
    void answerQuestionShouldSkipToolLoopWhenFeatureDisabled() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder safePromptBuilder = mock(SafePromptBuilder.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        com.citytrip.service.impl.vivo.VivoFunctionCallingService vivoFunctionCallingService =
                mock(com.citytrip.service.impl.vivo.VivoFunctionCallingService.class);

        LlmProperties properties = buildReadyProperties();
        properties.getFeatures().setToolLoopEnabled(false);

        when(safePromptBuilder.buildChatSystemPrompt()).thenReturn("system");
        when(safePromptBuilder.buildChatUserPrompt(
                any(ChatReqDTO.class),
                anyList(),
                anyList(),
                any(AiChatAugmentationContext.class)
        )).thenReturn("user");
        when(chatPoiSkillService.loadRelevantPois(any(ChatReqDTO.class))).thenReturn(List.of());
        when(gatewayClient.request(any(), any(), any())).thenReturn("普通回答");

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                null
        );
        ReflectionTestUtils.setField(service, "vivoFunctionCallingService", vivoFunctionCallingService);

        service.answerQuestion(chatRequest("给我推荐路线", "成都"));

        verify(vivoFunctionCallingService, never()).shouldEnterToolLoop(any());
    }

    @Test
    void answerQuestionShouldInjectRagToolAndMcpContextIntoRealModelPrompt() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        ChatPoiSkillService chatPoiSkillService = mock(ChatPoiSkillService.class);
        AiChatAugmentationService augmentationService = mock(AiChatAugmentationService.class);

        LlmProperties properties = buildReadyProperties();
        SafePromptBuilder safePromptBuilder = new SafePromptBuilder();

        AiChatAugmentationContext augmentation = AiChatAugmentationContext.of(
                List.of("city-guide: 成都只是样例，系统应按用户指定城市处理。"),
                List.of("{\"tool\":\"search_poi\",\"status\":\"ok\",\"results\":[{\"name\":\"万象城\"}]}"),
                List.of("{\"capability\":\"geo.search\",\"status\":\"ok\",\"results\":[{\"name\":\"成都万象城\"}]}"),
                List.of("rag:city-guide", "tool:search_poi", "mcp:geo.search")
        );
        when(augmentationService.build(any(ChatReqDTO.class), any(), eq(true), eq(true))).thenReturn(augmentation);
        when(chatPoiSkillService.loadRelevantPois(any(ChatReqDTO.class))).thenReturn(List.of());
        when(gatewayClient.request(any(), any(), any())).thenReturn("可以优先按室内商圈和公交接驳安排。");

        RealChatGatewayService service = new RealChatGatewayService(
                gatewayClient,
                properties,
                safePromptBuilder,
                chatPoiSkillService,
                null,
                null,
                null,
                null,
                null,
                new ChatEvidenceSkillService()
        );
        ReflectionTestUtils.setField(service, "aiChatAugmentationService", augmentationService);

        ChatVO result = service.answerQuestion(chatRequest("万象城附近有什么推荐？", "成都"));

        ArgumentCaptor<List<OpenAiGatewayClient.OpenAiMessage>> messagesCaptor = forClass(List.class);
        verify(gatewayClient).request(any(), any(), messagesCaptor.capture());
        List<OpenAiGatewayClient.OpenAiMessage> messages = messagesCaptor.getValue();
        assertThat(messages.get(0).getContent()).contains("系统面向多城市", "成都只是样例城市");
        assertThat(messages.get(1).getContent())
                .contains("【可参考的城市信息】", "city-guide")
                .contains("【刚查到的信息】", "search_poi")
                .contains("【路线核对信息】", "geo.search")
                .doesNotContain("【RAG参考资料】")
                .doesNotContain("【工具结果】")
                .doesNotContain("【MCP证据】");
        assertThat(messages).anySatisfy(message ->
                assertThat(message.getContent()).contains("刚查到的信息").contains("search_poi"));
        assertThat(result.getEvidence()).contains("skills=RAG,FunctionCalling,MCP", "rag:city-guide", "tool:search_poi", "mcp:geo.search");
    }

    private LlmProperties buildReadyProperties() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().setModel("gpt-5.4");
        properties.getOpenai().getChat().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().getChat().setModel("gpt-5.4");
        return properties;
    }

    private ChatReqDTO chatRequest(String question, String cityName) {
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName(cityName);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        req.setContext(context);
        return req;
    }

    private ChatReqDTO editRequest() {
        ChatReqDTO.ChatRouteNode node = new ChatReqDTO.ChatRouteNode();
        node.setNodeKey("node-1");
        node.setPoiName("宽窄巷子");
        node.setDayNo(1);
        node.setStepOrder(1);
        node.setStayDuration(90);

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setItineraryId(99L);
        itinerary.setNodes(List.of(node));

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        context.setItinerary(itinerary);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("把宽窄巷子少玩半小时");
        req.setContext(context);
        return req;
    }
}

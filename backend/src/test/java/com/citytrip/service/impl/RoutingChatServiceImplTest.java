package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ChatService;
import com.citytrip.service.ai.adapter.LangChainChatServiceAdapter;
import com.citytrip.service.ai.config.AiPlatformConfig;
import com.citytrip.service.ai.orchestrator.AiExecutionContextFactory;
import com.citytrip.service.ai.orchestrator.AiSceneRouter;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.application.community.CommunitySemanticSearchService;
import com.citytrip.service.impl.vivo.VivoEmbeddingClient;
import com.citytrip.service.impl.vivo.VivoRerankClient;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RoutingChatServiceImplTest {

    @Test
    void answerQuestionShouldPreferAiAdapterWhenProvided() {
        LlmProperties properties = new LlmProperties();
        properties.getFeatures().setChatOnlineEnabled(true);

        RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
        MockChatServiceImpl mockChatService = mock(MockChatServiceImpl.class);
        ChatService aiAdapter = new ChatService() {
            @Override
            public ChatVO answerQuestion(ChatReqDTO req) {
                ChatVO vo = new ChatVO();
                vo.setAnswer("adapter-answer");
                return vo;
            }

            @Override
            public ChatStatusVO getStatus() {
                return new ChatStatusVO();
            }
        };

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(aiAdapter, realChatService, mockChatService, properties);

        ChatVO result = service.answerQuestion(new ChatReqDTO());

        assertThat(result.getAnswer()).isEqualTo("adapter-answer");
        verifyNoInteractions(realChatService);
        verifyNoInteractions(mockChatService);
    }

    @Test
    void answerQuestionShouldUseMockWhenChatOnlineFeatureDisabled() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().getChat().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().getChat().setModel("gpt-5.4");
        properties.getFeatures().setChatOnlineEnabled(false);

        RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
        MockChatServiceImpl mockChatService = mock(MockChatServiceImpl.class);
        ChatVO fallback = new ChatVO();
        fallback.setAnswer("mock-answer");
        when(mockChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(fallback);

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(realChatService, mockChatService, properties);
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("帮我规划路线");

        ChatVO result = service.answerQuestion(req);

        assertThat(result.getAnswer()).isEqualTo("mock-answer");
        verifyNoInteractions(realChatService);
    }

    @Test
    void answerQuestionShouldNotExposeGatewayErrorWhenFallbackDisabled() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.setFallbackToMock(false);
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://token-plan-cn.xiaomimimo.com/v1");
        properties.getOpenai().getChat().setBaseUrl("https://token-plan-cn.xiaomimimo.com/v1");
        properties.getOpenai().getChat().setModel("mimo-v2-omni");
        properties.getFeatures().setChatOnlineEnabled(true);

        RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
        MockChatServiceImpl mockChatService = mock(MockChatServiceImpl.class);
        when(realChatService.answerQuestion(any(ChatReqDTO.class)))
                .thenThrow(new IllegalStateException("Model request failed. model=mimo-v2-omni, endpoint=https://token-plan-cn.xiaomimimo.com/v1/chat/completions, reason=OpenAI message content is empty"));

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(realChatService, mockChatService, properties);

        ChatVO result = service.answerQuestion(new ChatReqDTO());

        assertThat(result.getAnswer()).contains("没有拿到有效回答");
        assertThat(result.getAnswer()).doesNotContain("endpoint=");
        assertThat(result.getAnswer()).doesNotContain("OPENAI");
    }

    @Test
    void streamAnswerShouldReturnFriendlyFallbackWhenNoTokenWasEmitted() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.setFallbackToMock(false);
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://token-plan-cn.xiaomimimo.com/v1");
        properties.getOpenai().getChat().setBaseUrl("https://token-plan-cn.xiaomimimo.com/v1");
        properties.getOpenai().getChat().setModel("mimo-v2-omni");
        properties.getFeatures().setChatOnlineEnabled(true);

        RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
        MockChatServiceImpl mockChatService = mock(MockChatServiceImpl.class);
        when(realChatService.streamAnswer(any(ChatReqDTO.class), any()))
                .thenThrow(new IllegalStateException("OpenAI message content is empty"));

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(realChatService, mockChatService, properties);

        ChatVO result = service.streamAnswer(new ChatReqDTO(), token -> {
        });

        assertThat(result.getAnswer()).contains("没有拿到有效回答");
        assertThat(result.getAnswer()).doesNotContain("OpenAI message content is empty");
    }

    @Test
    void getStatusShouldExposeExtendedReadinessFields() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getTool().setModel("Volc-DeepSeek-V3.2");

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(null, new MockChatServiceImpl(), properties);
        ChatStatusVO status = service.getStatus();

        assertThat(status.isToolReady()).isTrue();
        assertThat(status.getWarnings()).isNotNull();
    }

    @Test
    void getStatusShouldNotReportSemanticReadyForStubVectorClients() {
        LlmProperties properties = new LlmProperties();
        properties.setProvider("real");
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api-ai.vivo.com.cn/v1");
        properties.getOpenai().getChat().setModel("Doubao-Seed-2.0-mini");
        properties.getOpenai().getTool().setModel("Volc-DeepSeek-V3.2");

        RoutingChatServiceImpl service = new RoutingChatServiceImpl(null, new MockChatServiceImpl(), properties);
        CommunitySemanticSearchService semanticSearchService = new CommunitySemanticSearchService(
                new VivoEmbeddingClient(),
                new VivoRerankClient()
        );
        ReflectionTestUtils.setField(service, "communitySemanticSearchService", semanticSearchService);

        ChatStatusVO status = service.getStatus();

        assertThat(status.isEmbeddingReady()).isFalse();
        assertThat(status.isRerankReady()).isFalse();
    }

    @Test
    void springShouldWireRoutingChatServiceWithLangChainAdapterBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            LlmProperties properties = new LlmProperties();
            context.registerBean(LlmProperties.class, () -> properties);
            RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
            RealLlmGatewayService realLlmService = mock(RealLlmGatewayService.class);
            MockChatServiceImpl mockChatService = mock(MockChatServiceImpl.class);
            ChatVO delegated = new ChatVO();
            delegated.setAnswer("真实模型主答：可以先去成都博物馆，再去宽窄巷子。");
            when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(delegated);
            context.registerBean(RealChatGatewayService.class, () -> realChatService);
            context.registerBean(RealLlmGatewayService.class, () -> realLlmService);
            context.registerBean(MockChatServiceImpl.class, () -> mockChatService);
            context.registerBean(AiPlatformConfig.class);
            context.registerBean(AiSceneRouter.class);
            context.registerBean(AiExecutionContextFactory.class);
            context.registerBean(LangChainAiOrchestrator.class);
            context.registerBean(LangChainChatServiceAdapter.class);
            context.registerBean(RoutingChatServiceImpl.class, () -> new RoutingChatServiceImpl(realChatService, mockChatService, properties));

            assertThatCode(context::refresh).doesNotThrowAnyException();

            RoutingChatServiceImpl service = context.getBean(RoutingChatServiceImpl.class);
            ChatReqDTO req = new ChatReqDTO();
            req.setQuestion("帮我推荐成都博物馆和万象城");

            ChatVO result = service.answerQuestion(req);

            assertThat(result).isNotNull();
            assertThat(result.getAnswer()).isEqualTo("真实模型主答：可以先去成都博物馆，再去宽窄巷子。");
            verify(realChatService).answerQuestion(any(ChatReqDTO.class));
        }
    }
}

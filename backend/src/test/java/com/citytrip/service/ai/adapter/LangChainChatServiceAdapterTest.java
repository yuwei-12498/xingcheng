package com.citytrip.service.ai.adapter;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ai.orchestrator.AiSceneRouter;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.CityGuideRetriever;
import com.citytrip.service.ai.rag.PoiAliasResolver;
import com.citytrip.service.ai.rag.QueryRewriteService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangChainChatServiceAdapterTest {

    @Test
    void answerQuestionShouldDelegateMainAnswerToRealChatServiceWhenAvailable() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO realAnswer = new ChatVO();
        realAnswer.setAnswer("真实模型回答：建议先去杜甫草堂，再去成都博物馆。");
        realAnswer.setRelatedTips(List.of("原有提示"));
        realAnswer.setEvidence(List.of("route_path=杜甫草堂->成都博物馆"));
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(realAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(context -> List.of(
                        new com.citytrip.service.ai.rag.RetrievalDocument("city-guide", "杜甫草堂工作日早段更安静")
                ))),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("杜甫草堂怎么安排更顺？");

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).isEqualTo("真实模型回答：建议先去杜甫草堂，再去成都博物馆。");
        assertThat(result.getRelatedTips()).contains("原有提示");
        assertThat(result.getEvidence()).contains("route_path=杜甫草堂->成都博物馆");
        assertThat(result.getEvidence()).anyMatch(item -> item.contains("杜甫草堂工作日早段更安静"));
    }

    @Test
    void answerQuestionShouldNotFallBackToTemplateWhenRealModelReturnsEmptyAnswer() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO emptyAnswer = new ChatVO();
        emptyAnswer.setAnswer("   ");
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(emptyAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new CityGuideRetriever())),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("万象城搜不到怎么办");

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).contains("没有拿到有效回答");
        assertThat(result.getAnswer()).doesNotContain("统一模型中台建议");
        assertThat(result.getAnswer()).doesNotContain("我先按你的问题理解为");
    }

    @Test
    void streamAnswerShouldNotFallBackToTemplateWhenRealModelReturnsEmptyAnswer() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO emptyAnswer = new ChatVO();
        emptyAnswer.setAnswer("");
        when(realChatService.streamAnswer(any(ChatReqDTO.class), any())).thenReturn(emptyAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new CityGuideRetriever())),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("怎么走更合理");
        List<String> tokens = new ArrayList<>();

        ChatVO result = adapter.streamAnswer(req, tokens::add);

        assertThat(result.getAnswer()).contains("没有拿到有效回答");
        assertThat(result.getAnswer()).doesNotContain("统一模型中台建议");
        assertThat(result.getAnswer()).doesNotContain("我先按你的问题理解为");
        assertThat(tokens).isEmpty();
    }

    @Test
    void streamAnswerShouldReturnSanitizedUnavailableMessageWhenRealModelThrowsBeforeStreaming() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        when(realChatService.streamAnswer(any(ChatReqDTO.class), any()))
                .thenThrow(new IllegalStateException("Model request failed. endpoint=https://example.test/v1/chat/completions"));

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new CityGuideRetriever())),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("继续帮我调整路线");

        ChatVO result = adapter.streamAnswer(req, token -> {
        });

        assertThat(result.getAnswer()).contains("没有拿到有效回答");
        assertThat(result.getAnswer()).doesNotContain("endpoint=");
        assertThat(result.getAnswer()).doesNotContain("统一模型中台建议");
    }

    @Test
    void answerQuestionShouldNotPretendToChatWhenRealChatServiceIsNotWired() {
        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new CityGuideRetriever())),
                new QueryRewriteService(new PoiAliasResolver())
        );
        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("万象城搜不到怎么办");

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).contains("真实回答链路");
        assertThat(result.getAnswer()).doesNotContain("统一模型中台建议");
        assertThat(result.getAnswer()).doesNotContain("我先按你的问题理解为");
        assertThat(result.getRelatedTips()).isNotEmpty();
        assertThat(result.getEvidence()).isEmpty();
    }


    @Test
    void streamAnswerShouldNotPretendToChatWhenRealChatServiceIsNotWired() {
        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(context -> List.of(
                        new com.citytrip.service.ai.rag.RetrievalDocument("route-guide", "\u718a\u732b\u57fa\u5730\u5efa\u8bae\u65e9\u5230\uff0c\u4e1c\u90ca\u8bb0\u5fc6\u66f4\u9002\u5408\u653e\u5728\u4e0b\u5348")
                ))),
                new QueryRewriteService(new PoiAliasResolver())
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("\u8fd9\u6761\u8def\u7ebf\u600e\u4e48\u5b89\u6392\u66f4\u987a");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        ChatReqDTO.ChatRouteNode first = new ChatReqDTO.ChatRouteNode();
        first.setPoiName("\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730");
        ChatReqDTO.ChatRouteNode second = new ChatReqDTO.ChatRouteNode();
        second.setPoiName("\u4e1c\u90ca\u8bb0\u5fc6");
        itinerary.setNodes(List.of(first, second));
        context.setItinerary(itinerary);
        context.setCityName("\u6210\u90fd");
        req.setContext(context);

        ChatVO result = adapter.streamAnswer(req, token -> {
        });

        assertThat(result.getAnswer()).contains("真实回答链路");
        assertThat(result.getAnswer()).doesNotContain("统一模型中台建议");
        assertThat(result.getAnswer()).doesNotContain("我先按你的问题理解为");
    }

    @Test
    void answerQuestionShouldDelegateTransportRouteQuestionToRealChatService() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO realAnswer = new ChatVO();
        realAnswer.setAnswer("\u771f\u5b9e\u6a21\u578b\u56de\u7b54\uff1a\u8fd9\u6bb5\u5efa\u8bae\u7528\u5730\u94c1+\u6b65\u884c\uff0c\u5927\u7ea6 35 \u5206\u949f\u3002");
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(realAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of()),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("\u8fd9\u6bb5\u600e\u4e48\u8d70\u6bd4\u8f83\u65b9\u4fbf");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        ChatReqDTO.ChatRouteNode first = new ChatReqDTO.ChatRouteNode();
        first.setPoiName("\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730");
        ChatReqDTO.ChatRouteNode second = new ChatReqDTO.ChatRouteNode();
        second.setPoiName("\u4e1c\u90ca\u8bb0\u5fc6");
        second.setTravelTransportMode("\u5730\u94c1+\u6b65\u884c");
        second.setTravelTime(35);
        itinerary.setNodes(List.of(first, second));
        context.setItinerary(itinerary);
        context.setCityName("\u6210\u90fd");
        req.setContext(context);

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).containsAnyOf("\u5730\u94c1", "\u6b65\u884c", "35");
        assertThat(result.getAnswer()).doesNotContain("\u7edf\u4e00\u6a21\u578b\u4e2d\u53f0\u5efa\u8bae");
    }

    @Test
    void answerQuestionShouldDelegateReplacementQuestionToRealChatService() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO realAnswer = new ChatVO();
        realAnswer.setAnswer("\u771f\u5b9e\u6a21\u578b\u56de\u7b54\uff1a\u53ef\u4ee5\u5148\u628a\u52a8\u7269\u56ed\u6362\u6210\u6210\u90fd\u81ea\u7136\u535a\u7269\u9986\u3002");
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(realAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of()),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("\u8fd9\u4e2a\u70b9\u80fd\u4e0d\u80fd\u6362\u4e00\u4e2a");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatRecentPoi alternative = new ChatReqDTO.ChatRecentPoi();
        alternative.setPoiName("\u6210\u90fd\u81ea\u7136\u535a\u7269\u9986");
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        ChatReqDTO.ChatRouteNode first = new ChatReqDTO.ChatRouteNode();
        first.setPoiName("\u6210\u90fd\u52a8\u7269\u56ed");
        itinerary.setNodes(List.of(first));
        context.setItinerary(itinerary);
        context.setRecentPois(List.of(alternative));
        context.setCityName("\u6210\u90fd");
        req.setContext(context);

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).containsAnyOf("\u81ea\u7136\u535a\u7269\u9986", "\u6362\u6210");
        assertThat(result.getAnswer()).doesNotContain("\u7edf\u4e00\u6a21\u578b\u4e2d\u53f0\u5efa\u8bae");
    }

    @Test
    void answerQuestionShouldDelegateRhythmQuestionToRealChatService() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO realAnswer = new ChatVO();
        realAnswer.setAnswer("\u771f\u5b9e\u6a21\u578b\u56de\u7b54\uff1a\u8fd9\u6761\u8def\u7ebf\u8282\u594f\u504f\u6ee1\uff0c\u53ef\u4ee5\u653e\u6162\u4e00\u70b9\u3002");
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(realAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of()),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("\u6211\u611f\u89c9\u8fd9\u6761\u8def\u7ebf\u592a\u8d76\u4e86");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setTotalDuration(540);
        ChatReqDTO.ChatRouteNode first = new ChatReqDTO.ChatRouteNode();
        first.setPoiName("\u5bbd\u7a84\u5df7\u5b50");
        ChatReqDTO.ChatRouteNode second = new ChatReqDTO.ChatRouteNode();
        second.setPoiName("\u6210\u90fd\u535a\u7269\u9986");
        itinerary.setNodes(List.of(first, second));
        context.setItinerary(itinerary);
        context.setCityName("\u6210\u90fd");
        req.setContext(context);

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).containsAnyOf("\u653e\u6162", "\u5220\u6389", "\u8282\u594f");
        assertThat(result.getAnswer()).doesNotContain("\u7edf\u4e00\u6a21\u578b\u4e2d\u53f0\u5efa\u8bae");
    }

    @Test
    void answerQuestionShouldPassChatContextIntoAiExecutionContext() {
        com.citytrip.service.impl.RealChatGatewayService realChatService = mock(com.citytrip.service.impl.RealChatGatewayService.class);
        ChatVO realAnswer = new ChatVO();
        realAnswer.setAnswer("真实模型回答：可以继续沿着商圈夜景路线走。");
        when(realChatService.answerQuestion(any(ChatReqDTO.class))).thenReturn(realAnswer);

        LangChainChatServiceAdapter adapter = new LangChainChatServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(context -> {
                    assertThat(context.getUserId()).isEqualTo(91L);
                    assertThat(context.getCityName()).isEqualTo("成都");
                    assertThat(context.getItineraryId()).isEqualTo(42L);
                    assertThat(context.getRouteSummary()).isEqualTo("商业区夜景路线");
                    assertThat(context.getRecentMessages()).contains("上一轮先去太古里");
                    assertThat(context.getRecentPoiNames()).contains("IFS");
                    return List.of();
                })),
                new QueryRewriteService(new PoiAliasResolver()),
                realChatService
        );

        ChatReqDTO.ChatMessage message = new ChatReqDTO.ChatMessage();
        message.setRole("user");
        message.setContent("上一轮先去太古里");

        ChatReqDTO.ChatRecentPoi recentPoi = new ChatReqDTO.ChatRecentPoi();
        recentPoi.setPoiName("IFS");

        ChatReqDTO.ChatItineraryContext itinerary = new ChatReqDTO.ChatItineraryContext();
        itinerary.setItineraryId(42L);
        itinerary.setSummary("商业区夜景路线");

        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCurrentUserId(91L);
        context.setCityName("成都");
        context.setItinerary(itinerary);
        context.setRecentPois(List.of(recentPoi));

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("这条路线晚上怎么安排更顺？");
        req.setContext(context);
        req.setRecentMessages(List.of(message));

        ChatVO result = adapter.answerQuestion(req);

        assertThat(result.getAnswer()).contains("真实模型回答");
    }

}

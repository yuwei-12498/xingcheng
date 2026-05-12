package com.citytrip.service.ai.adapter;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ChatService;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.QueryRewriteService;
import com.citytrip.service.ai.rag.RetrievalDocument;
import com.citytrip.service.impl.RealChatGatewayService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public class LangChainChatServiceAdapter implements ChatService {
    private static final String MODEL_UNAVAILABLE_ANSWER =
            "刚才没有拿到有效回答，请稍后重试或换个说法继续。";
    private static final String REAL_CHAT_NOT_WIRED_ANSWER =
            "当前聊天服务还没接好真实回答链路；我不会用本地模板假装回答。请检查 RealChatGatewayService 注入和 llm 配置。";

    private final LangChainAiOrchestrator orchestrator;
    private final AiRetrieverFacade retrieverFacade;
    private final QueryRewriteService queryRewriteService;
    private final RealChatGatewayService realChatService;

    public LangChainChatServiceAdapter(LangChainAiOrchestrator orchestrator) {
        this(orchestrator, null, null, null);
    }

    public LangChainChatServiceAdapter(LangChainAiOrchestrator orchestrator,
                                       AiRetrieverFacade retrieverFacade,
                                       QueryRewriteService queryRewriteService) {
        this(orchestrator, retrieverFacade, queryRewriteService, null);
    }

    @Autowired
    public LangChainChatServiceAdapter(LangChainAiOrchestrator orchestrator,
                                       AiRetrieverFacade retrieverFacade,
                                       QueryRewriteService queryRewriteService,
                                       RealChatGatewayService realChatService) {
        this.orchestrator = orchestrator;
        this.retrieverFacade = retrieverFacade;
        this.queryRewriteService = queryRewriteService;
        this.realChatService = realChatService;
    }

    @Override
    public ChatVO answerQuestion(ChatReqDTO req) {
        String question = req == null ? "" : req.getQuestion();
        String cityName = resolveCityName(req);
        AiExecutionContext context = AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput(question)
                .userId(resolveCurrentUserId(req))
                .cityName(cityName)
                .itineraryId(resolveItineraryId(req))
                .recentMessages(resolveRecentMessages(req))
                .recentPoiNames(resolveRecentPoiNames(req))
                .routeSummary(resolveRouteSummary(req))
                .build();
        orchestrator.resolveScene(context);

        List<RetrievalDocument> documents = retrieverFacade == null ? List.of() : retrieverFacade.retrieve(context);
        List<String> rewriteTips = queryRewriteService == null ? List.of() : queryRewriteService.rewrite(question, cityName);
        if (realChatService != null) {
            try {
                ChatVO delegated = realChatService.answerQuestion(req);
                if (hasAnswer(delegated)) {
                    delegated.setRelatedTips(mergeDistinct(delegated.getRelatedTips(), rewriteTips, 6));
                    delegated.setEvidence(mergeDistinct(
                            delegated.getEvidence(),
                            documents.stream().map(RetrievalDocument::content).toList(),
                            8
                    ));
                    return delegated;
                }
            } catch (RuntimeException ex) {
                return buildModelUnavailableResponse(rewriteTips);
            }
            return buildModelUnavailableResponse(rewriteTips);
        }
        return buildRealChatNotWiredResponse(rewriteTips);
    }

    @Override
    public ChatVO streamAnswer(ChatReqDTO req, Consumer<String> tokenConsumer) {
        String question = req == null ? "" : req.getQuestion();
        String cityName = resolveCityName(req);
        AiExecutionContext context = AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput(question)
                .userId(resolveCurrentUserId(req))
                .cityName(cityName)
                .itineraryId(resolveItineraryId(req))
                .recentMessages(resolveRecentMessages(req))
                .recentPoiNames(resolveRecentPoiNames(req))
                .routeSummary(resolveRouteSummary(req))
                .build();
        orchestrator.resolveScene(context);

        List<RetrievalDocument> documents = retrieverFacade == null ? List.of() : retrieverFacade.retrieve(context);
        List<String> rewriteTips = queryRewriteService == null ? List.of() : queryRewriteService.rewrite(question, cityName);
        if (realChatService != null) {
            AtomicBoolean emittedAnyToken = new AtomicBoolean(false);
            Consumer<String> guardedConsumer = token -> {
                if (StringUtils.hasText(token)) {
                    emittedAnyToken.set(true);
                }
                if (tokenConsumer != null) {
                    tokenConsumer.accept(token);
                }
            };
            try {
                ChatVO delegated = realChatService.streamAnswer(req, guardedConsumer);
                if (hasAnswer(delegated) || emittedAnyToken.get()) {
                    if (delegated == null) {
                        delegated = new ChatVO();
                    }
                    if (hasAnswer(delegated)) {
                        delegated.setRelatedTips(mergeDistinct(delegated.getRelatedTips(), rewriteTips, 6));
                        delegated.setEvidence(mergeDistinct(
                                delegated.getEvidence(),
                                documents.stream().map(RetrievalDocument::content).toList(),
                                8
                        ));
                    }
                    return delegated;
                }
            } catch (RuntimeException ex) {
                if (emittedAnyToken.get()) {
                    throw ex;
                }
                return buildModelUnavailableResponse(rewriteTips);
            }
            return buildModelUnavailableResponse(rewriteTips);
        }
        return buildRealChatNotWiredResponse(rewriteTips);
    }

    private boolean hasAnswer(ChatVO vo) {
        return vo != null && StringUtils.hasText(vo.getAnswer());
    }

    private ChatVO buildModelUnavailableResponse(List<String> rewriteTips) {
        ChatVO vo = new ChatVO();
        vo.setAnswer(MODEL_UNAVAILABLE_ANSWER);
        vo.setRelatedTips(mergeDistinct(List.of("换个说法重试", "继续修改当前路线"), rewriteTips, 4));
        vo.setEvidence(List.of());
        return vo;
    }

    private ChatVO buildRealChatNotWiredResponse(List<String> rewriteTips) {
        ChatVO vo = new ChatVO();
        vo.setAnswer(REAL_CHAT_NOT_WIRED_ANSWER);
        vo.setRelatedTips(mergeDistinct(List.of("检查模型配置", "重新发起真实模型请求"), rewriteTips, 4));
        vo.setEvidence(List.of());
        return vo;
    }

    @Override
    public ChatStatusVO getStatus() {
        ChatStatusVO vo = new ChatStatusVO();
        vo.setProvider(realChatService == null ? "ai-orchestrator" : "ai-orchestrator+real");
        vo.setConfigured(realChatService != null);
        vo.setMessage(realChatService == null
                ? "问答编排已启动，但真实回答服务还没接好；本地模板聊天已关闭。"
                : "问答编排已启动，当前会直接走真实回答服务。");
        return vo;
    }

    private String resolveCityName(ChatReqDTO req) {
        if (req != null && req.getContext() != null && StringUtils.hasText(req.getContext().getCityName())) {
            return req.getContext().getCityName().trim();
        }
        return "\u6210\u90fd";
    }

    private Long resolveItineraryId(ChatReqDTO req) {
        if (req == null || req.getContext() == null || req.getContext().getItinerary() == null) {
            return null;
        }
        return req.getContext().getItinerary().getItineraryId();
    }

    private Long resolveCurrentUserId(ChatReqDTO req) {
        if (req == null || req.getContext() == null) {
            return null;
        }
        return req.getContext().getCurrentUserId();
    }

    private String resolveRouteSummary(ChatReqDTO req) {
        if (req == null || req.getContext() == null || req.getContext().getItinerary() == null) {
            return null;
        }
        String summary = req.getContext().getItinerary().getSummary();
        return StringUtils.hasText(summary) ? summary.trim() : null;
    }

    private List<String> resolveRecentMessages(ChatReqDTO req) {
        if (req == null || req.getRecentMessages() == null || req.getRecentMessages().isEmpty()) {
            return List.of();
        }
        return req.getRecentMessages().stream()
                .filter(message -> message != null && StringUtils.hasText(message.getContent()))
                .map(message -> message.getContent().trim())
                .toList();
    }

    private List<String> resolveRecentPoiNames(ChatReqDTO req) {
        if (req == null
                || req.getContext() == null
                || req.getContext().getRecentPois() == null
                || req.getContext().getRecentPois().isEmpty()) {
            return List.of();
        }
        return req.getContext().getRecentPois().stream()
                .filter(poi -> poi != null && StringUtils.hasText(poi.getPoiName()))
                .map(poi -> poi.getPoiName().trim())
                .toList();
    }

    private List<String> mergeDistinct(List<String> primary, List<String> secondary, int limit) {
        Set<String> merged = new LinkedHashSet<>();
        if (primary != null) {
            primary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (secondary != null) {
            secondary.stream().filter(StringUtils::hasText).map(String::trim).forEach(merged::add);
        }
        if (merged.isEmpty()) {
            return List.of();
        }
        List<String> result = new ArrayList<>(merged);
        if (limit > 0 && result.size() > limit) {
            return List.copyOf(result.subList(0, limit));
        }
        return List.copyOf(result);
    }

}

package com.citytrip.service.impl;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.ChatService;
import com.citytrip.service.ai.adapter.LangChainChatServiceAdapter;
import com.citytrip.service.application.community.CommunitySemanticSearchService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Primary
@Service
public class RoutingChatServiceImpl implements ChatService {
    private static final Logger log = LoggerFactory.getLogger(RoutingChatServiceImpl.class);

    private final ChatService aiAdapter;
    private final RealChatGatewayService realChatService;
    private final MockChatServiceImpl mockChatService;
    private final LlmProperties llmProperties;
    @Autowired(required = false)
    private LangChainChatServiceAdapter injectedAiAdapter;
    @Autowired(required = false)
    private GeoSearchProperties geoSearchProperties;
    @Autowired(required = false)
    private CommunitySemanticSearchService communitySemanticSearchService;

    @Autowired
    public RoutingChatServiceImpl(RealChatGatewayService realChatService,
                                  MockChatServiceImpl mockChatService,
                                  LlmProperties llmProperties) {
        this(null, realChatService, mockChatService, llmProperties);
    }

    RoutingChatServiceImpl(ChatService aiAdapter,
                           RealChatGatewayService realChatService,
                           MockChatServiceImpl mockChatService,
                           LlmProperties llmProperties) {
        this.aiAdapter = aiAdapter;
        this.realChatService = realChatService;
        this.mockChatService = mockChatService;
        this.llmProperties = llmProperties;
    }

    @Override
    public ChatVO answerQuestion(ChatReqDTO req) {
        if (resolveAiAdapter() != null && llmProperties.getAiPlatform().getOrchestrator().isEnabled()) {
            return resolveAiAdapter().answerQuestion(req);
        }
        if (!llmProperties.getFeatures().isChatOnlineEnabled()) {
            log.info("Chat online feature is disabled, using local rule-based provider");
            return mockChatService.answerQuestion(req);
        }
        if (llmProperties.isMockOnly()) {
            log.info("Chat service is forced to use local rule-based provider");
            return mockChatService.answerQuestion(req);
        }

        if (!llmProperties.canTryRealChat()) {
            String reason = String.join("; ", llmProperties.getRealChatConfigIssues());
            if (llmProperties.isRealOnly()) {
                return buildErrorResponse("Real model config is invalid: " + reason);
            }
            log.info("Real chat model is not available, falling back to local rule-based provider. provider={}, reason={}",
                    llmProperties.getProvider(), reason);
            return mockChatService.answerQuestion(req);
        }

        try {
            LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
            log.info("Chat service is using real model first. provider={}, model={}",
                    llmProperties.getProvider(), chatOptions.getModel());
            ChatVO result = realChatService.answerQuestion(req);
            if (result == null || result.getAnswer() == null || result.getAnswer().trim().isEmpty()) {
                throw new IllegalStateException("Real model returned empty answer");
            }
            return result;
        } catch (Exception e) {
            if (llmProperties.isFallbackToMock()) {
                log.warn("Real chat model failed, falling back to local rule-based provider. reason={}", e.getMessage(), e);
                return mockChatService.answerQuestion(req);
            }
            log.error("Real chat model failed and fallback is disabled. reason={}", e.getMessage(), e);
            return buildErrorResponse();
        }
    }

    @Override
    public ChatVO streamAnswer(ChatReqDTO req, Consumer<String> tokenConsumer) {
        if (resolveAiAdapter() != null && llmProperties.getAiPlatform().getOrchestrator().isEnabled()) {
            return resolveAiAdapter().streamAnswer(req, tokenConsumer);
        }
        if (!llmProperties.getFeatures().isChatOnlineEnabled()) {
            log.info("Chat online feature is disabled, using local rule-based stream");
            return mockChatService.streamAnswer(req, tokenConsumer);
        }
        if (llmProperties.isMockOnly()) {
            return mockChatService.streamAnswer(req, tokenConsumer);
        }

        if (!llmProperties.canTryRealChat()) {
            String reason = String.join("; ", llmProperties.getRealChatConfigIssues());
            if (llmProperties.isRealOnly()) {
                return buildErrorResponse("Real model config is invalid: " + reason);
            }
            log.info("Real chat model is not available, falling back to local rule-based stream. provider={}, reason={}",
                    llmProperties.getProvider(), reason);
            return mockChatService.streamAnswer(req, tokenConsumer);
        }

        AtomicBoolean emittedAnyToken = new AtomicBoolean(false);
        Consumer<String> guardedConsumer = token -> {
            if (token != null && !token.isEmpty()) {
                emittedAnyToken.set(true);
                if (tokenConsumer != null) {
                    tokenConsumer.accept(token);
                }
            }
        };

        try {
            return realChatService.streamAnswer(req, guardedConsumer);
        } catch (Exception e) {
            if (llmProperties.isFallbackToMock() && !emittedAnyToken.get()) {
                log.warn("Real chat stream failed before any token, falling back to local rule-based provider. reason={}", e.getMessage(), e);
                return mockChatService.streamAnswer(req, tokenConsumer);
            }
            if (!emittedAnyToken.get()) {
                log.error("Real chat stream failed before any token and fallback is disabled. reason={}", e.getMessage(), e);
                return buildErrorResponse();
            }
            if (llmProperties.isFallbackToMock()) {
                log.warn("Real chat stream failed after partial output; local rule-based fallback skipped. reason={}", e.getMessage(), e);
            } else {
                log.error("Real chat stream failed and fallback is disabled. reason={}", e.getMessage(), e);
            }
            throw e instanceof RuntimeException ? (RuntimeException) e : new RuntimeException(e);
        }
    }

    @Override
    public ChatStatusVO getStatus() {
        LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
        boolean chatOnlineEnabled = llmProperties.getFeatures().isChatOnlineEnabled();
        ChatStatusVO vo = new ChatStatusVO();
        vo.setProvider(llmProperties.getProvider());
        vo.setConfigured(chatOnlineEnabled && llmProperties.canTryRealChat());
        vo.setRealModelAvailable(chatOnlineEnabled && llmProperties.canTryRealChat());
        vo.setFallbackToMock(llmProperties.isFallbackToMock());
        vo.setTimeoutSeconds(llmProperties.resolveReadTimeoutSeconds());
        vo.setModel(chatOptions.getModel());
        vo.setBaseUrl(chatOptions.getBaseUrl());
        vo.setToolReady(llmProperties.getFeatures().isToolLoopEnabled() && llmProperties.canTryRealTool());
        vo.setGeoReady(isGeoReady());
        vo.setEmbeddingReady(isEmbeddingReady());
        vo.setRerankReady(isRerankReady());
        vo.setWarnings(llmProperties.getRealModelConfigWarnings());

        if (!chatOnlineEnabled) {
            vo.setMessage("在线问答开关未开启，当前会使用本地规则兜底。");
            return vo;
        }

        if (llmProperties.isMockOnly()) {
            vo.setMessage("当前被配置为只使用本地规则兜底。");
            return vo;
        }

        List<String> issues = llmProperties.getRealChatConfigIssues();
        if (!issues.isEmpty()) {
            vo.setMessage("真实回答配置还不完整：" + String.join("; ", issues));
            return vo;
        }

        List<String> warnings = llmProperties.getRealModelConfigWarnings();
        if (!warnings.isEmpty()) {
            vo.setWarnings(warnings);
            vo.setMessage("真实回答配置可用，但还有提示：" + String.join("; ", warnings));
            return vo;
        }

        if (llmProperties.isFallbackToMock()) {
            vo.setMessage(buildReadyMessage("真实回答优先，本地规则兜底已开启。"));
            return vo;
        }

        vo.setMessage(buildReadyMessage("真实回答优先，本地规则兜底未开启。"));
        return vo;
    }


    private ChatService resolveAiAdapter() {
        return aiAdapter != null ? aiAdapter : injectedAiAdapter;
    }

    private boolean isGeoReady() {
        if (!llmProperties.getFeatures().isPoiLiveEnabled()) {
            return false;
        }
        return geoSearchProperties != null
                && geoSearchProperties.isEnabled()
                && StringUtils.hasText(geoSearchProperties.getBaseUrl());
    }

    private boolean isEmbeddingReady() {
        if (!llmProperties.getFeatures().isEmbeddingOnlineEnabled()) {
            return false;
        }
        return communitySemanticSearchService != null && communitySemanticSearchService.isEmbeddingReady();
    }

    private boolean isRerankReady() {
        if (!llmProperties.getFeatures().isRerankOnlineEnabled()) {
            return false;
        }
        return communitySemanticSearchService != null && communitySemanticSearchService.isRerankReady();
    }

    private boolean isSemanticReady() {
        if (!llmProperties.getFeatures().isSemanticOnlineEnabled()) {
            return false;
        }
        return communitySemanticSearchService != null && communitySemanticSearchService.isSemanticModelReady();
    }

    private String buildReadyMessage(String fallbackMessage) {
        if (llmProperties.canTryRealTool() && isGeoReady() && isSemanticReady()) {
            return "问答、查询、定位和语义检索都已接好";
        }
        return fallbackMessage;
    }

    private ChatVO buildErrorResponse(String message) {
        return buildErrorResponse();
    }

    private ChatVO buildErrorResponse() {
        ChatVO vo = new ChatVO();
        vo.setAnswer("刚才没有拿到有效回答。我已经记录这个异常了，你可以直接换个说法继续，例如：把 IFS 加到建设路小吃街后面，或把路线扩展到 5 站。");
        vo.setRelatedTips(List.of("继续修改当前路线", "换个说法重试"));
        return vo;
    }
}

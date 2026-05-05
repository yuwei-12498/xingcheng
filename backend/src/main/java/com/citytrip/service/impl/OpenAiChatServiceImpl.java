package com.citytrip.service.impl;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatStatusVO;
import com.citytrip.model.vo.ChatVO;
import com.citytrip.service.application.community.CommunitySemanticSearchService;
import com.citytrip.service.ChatService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.function.Consumer;

@Service
public class OpenAiChatServiceImpl implements ChatService {

    private final RealChatGatewayService realChatGatewayService;
    private final LlmProperties llmProperties;
    @Autowired(required = false)
    private GeoSearchProperties geoSearchProperties;
    @Autowired(required = false)
    private CommunitySemanticSearchService communitySemanticSearchService;

    public OpenAiChatServiceImpl(RealChatGatewayService realChatGatewayService,
                                 LlmProperties llmProperties) {
        this.realChatGatewayService = realChatGatewayService;
        this.llmProperties = llmProperties;
    }

    @Override
    public ChatVO answerQuestion(ChatReqDTO req) {
        if (!llmProperties.canTryRealChat()) {
            throw new IllegalStateException("OpenAI real model is not configured");
        }
        return realChatGatewayService.answerQuestion(req);
    }

    @Override
    public ChatVO streamAnswer(ChatReqDTO req, Consumer<String> tokenConsumer) {
        if (!llmProperties.canTryRealChat()) {
            throw new IllegalStateException("OpenAI real model is not configured");
        }
        return realChatGatewayService.streamAnswer(req, tokenConsumer);
    }

    @Override
    public ChatStatusVO getStatus() {
        LlmProperties.ResolvedOpenAiOptions chatOptions = llmProperties.getOpenai().resolveChatOptions();
        ChatStatusVO vo = new ChatStatusVO();
        vo.setProvider("real");
        vo.setConfigured(llmProperties.canTryRealChat());
        vo.setRealModelAvailable(llmProperties.canTryRealChat());
        vo.setFallbackToMock(llmProperties.isFallbackToMock());
        vo.setTimeoutSeconds(llmProperties.getTimeoutSeconds());
        vo.setModel(chatOptions.getModel());
        vo.setBaseUrl(chatOptions.getBaseUrl());
        vo.setToolReady(llmProperties.canTryRealTool());
        vo.setGeoReady(isGeoReady());
        vo.setEmbeddingReady(isEmbeddingReady());
        vo.setRerankReady(isRerankReady());
        vo.setWarnings(llmProperties.getRealModelConfigWarnings());
        vo.setMessage(buildStatusMessage());
        return vo;
    }

    private boolean isGeoReady() {
        return geoSearchProperties != null
                && geoSearchProperties.isEnabled()
                && StringUtils.hasText(geoSearchProperties.getBaseUrl());
    }

    private boolean isEmbeddingReady() {
        return communitySemanticSearchService != null && communitySemanticSearchService.isEmbeddingReady();
    }

    private boolean isRerankReady() {
        return communitySemanticSearchService != null && communitySemanticSearchService.isRerankReady();
    }

    private boolean isSemanticReady() {
        return communitySemanticSearchService != null && communitySemanticSearchService.isSemanticModelReady();
    }

    private String buildStatusMessage() {
        if (!llmProperties.canTryRealChat()) {
            return "Real model config is incomplete.";
        }
        if (llmProperties.canTryRealTool() && isGeoReady() && isSemanticReady()) {
            return "vivo chat/tool/geo/semantic ready";
        }
        return "Real model config looks valid.";
    }
}

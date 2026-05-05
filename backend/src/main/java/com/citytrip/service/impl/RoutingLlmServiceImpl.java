package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Supplier;

@Primary
@Service
public class RoutingLlmServiceImpl implements LlmService {
    private static final Logger log = LoggerFactory.getLogger(RoutingLlmServiceImpl.class);

    private final RealLlmGatewayService realLlmService;
    private final MockLlmServiceImpl mockLlmService;
    private final LlmProperties llmProperties;

    public RoutingLlmServiceImpl(RealLlmGatewayService realLlmService,
                                 MockLlmServiceImpl mockLlmService,
                                 LlmProperties llmProperties) {
        this.realLlmService = realLlmService;
        this.mockLlmService = mockLlmService;
        this.llmProperties = llmProperties;
    }

    @Override
    public String generateRouteWarmTip(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return routeCall(
                () -> realLlmService.generateRouteWarmTip(userReq, nodes),
                () -> mockLlmService.generateRouteWarmTip(userReq, nodes),
                "generateRouteWarmTip"
        );
    }

    @Override
    public String explainOptionRecommendation(GenerateReqDTO userReq, ItineraryOptionVO option) {
        return routeCall(
                () -> realLlmService.explainOptionRecommendation(userReq, option),
                () -> mockLlmService.explainOptionRecommendation(userReq, option),
                "explainOptionRecommendation"
        );
    }

    @Override
    public RouteCriticDecisionVO criticSelectItineraryOption(GenerateReqDTO userReq, List<ItineraryOptionVO> options) {
        return routeCallGeneric(
                () -> realLlmService.criticSelectItineraryOption(userReq, options),
                () -> mockLlmService.criticSelectItineraryOption(userReq, options),
                "criticSelectItineraryOption"
        );
    }

    @Override
    public String explainPoiChoice(GenerateReqDTO userReq, ItineraryNodeVO node) {
        return routeCall(
                () -> realLlmService.explainPoiChoice(userReq, node),
                () -> mockLlmService.explainPoiChoice(userReq, node),
                "explainPoiChoice"
        );
    }

    @Override
    public SmartFillVO parseSmartFill(String text, List<String> poiNameHints) {
        return routeCallGeneric(
                () -> realLlmService.parseSmartFill(text, poiNameHints),
                () -> mockLlmService.parseSmartFill(text, poiNameHints),
                "parseSmartFill"
        );
    }

    @Override
    public DepartureLegEstimateVO estimateDepartureLeg(GenerateReqDTO userReq, ItineraryNodeVO firstNode) {
        return routeCallGeneric(
                () -> realLlmService.estimateDepartureLeg(userReq, firstNode),
                () -> mockLlmService.estimateDepartureLeg(userReq, firstNode),
                "estimateDepartureLeg"
        );
    }

    @Override
    public SegmentTransportAnalysisVO analyzeSegmentTransport(GenerateReqDTO userReq, ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        return routeCallGeneric(
                () -> realLlmService.analyzeSegmentTransport(userReq, fromNode, toNode),
                () -> mockLlmService.analyzeSegmentTransport(userReq, fromNode, toNode),
                "analyzeSegmentTransport"
        );
    }

    @Override
    public ItineraryRouteDecorationVO decorateRouteExperience(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return routeCallGeneric(
                () -> realLlmService.decorateRouteExperience(userReq, nodes),
                () -> mockLlmService.decorateRouteExperience(userReq, nodes),
                "decorateRouteExperience"
        );
    }

    private String routeCall(TextSupplier realSupplier, TextSupplier mockSupplier, String scene) {
        return routeCallGeneric(realSupplier::get, mockSupplier::get, scene);
    }

    private <T> T routeCallGeneric(Supplier<T> realSupplier, Supplier<T> mockSupplier, String scene) {
        if (llmProperties.isMockOnly()) {
            log.info("行程文案服务当前使用 Mock 模型, scene={}", scene);
            return mockSupplier.get();
        }

        boolean canTryReal = llmProperties.canTryRealText();
        if (!canTryReal) {
            if (llmProperties.isRealOnly()) {
                return handleFailureOrFallback(mockSupplier, scene, "API Key 未配置或真实模型未启用", null);
            }
            log.info("行程文案服务当前使用 Mock 模型, provider={}, scene={}, reason=未检测到可用真实模型配置",
                    llmProperties.getProvider(), scene);
            return mockSupplier.get();
        }

        try {
            log.info("行程文案服务优先使用真实模型, provider={}, model={}, scene={}",
                    llmProperties.getProvider(), llmProperties.getOpenai().resolveTextOptions().getModel(), scene);
            T result = realSupplier.get();
            if (result == null) {
                throw new IllegalStateException("真实模型返回空结果");
            }
            return result;
        } catch (Exception e) {
            return handleFailureOrFallback(mockSupplier, scene, e.getMessage(), e);
        }
    }

    private <T> T handleFailureOrFallback(Supplier<T> mockSupplier, String scene, String reason, Exception e) {
        if (llmProperties.isFallbackToMock()) {
            log.warn("行程文案服务发生降级，改用 Mock。scene={}, reason={}", scene, reason, e);
            return mockSupplier.get();
        }
        log.error("行程文案服务真实模型调用失败，且未启用降级。scene={}, reason={}", scene, reason, e);
        throw new RuntimeException("真实大模型调用失败，且未启用 Mock 降级: " + reason, e);
    }

    @FunctionalInterface
    private interface TextSupplier {
        String get();
    }
}

package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import com.citytrip.service.ai.adapter.LangChainLlmServiceAdapter;
import com.citytrip.service.ai.config.AiPlatformConfig;
import com.citytrip.service.ai.orchestrator.AiExecutionContextFactory;
import com.citytrip.service.ai.orchestrator.AiSceneRouter;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verifyNoInteractions;

class RoutingLlmServiceImplTest {

    @Test
    void parseSmartFillShouldPreferAiAdapterWhenProvided() {
        LlmProperties properties = new LlmProperties();

        RealLlmGatewayService realLlmService = mock(RealLlmGatewayService.class);
        MockLlmServiceImpl mockLlmService = mock(MockLlmServiceImpl.class);
        LlmService aiAdapter = new LlmService() {
            @Override
            public String generateRouteWarmTip(com.citytrip.model.dto.GenerateReqDTO userReq, List<com.citytrip.model.vo.ItineraryNodeVO> nodes) {
                return "";
            }

            @Override
            public String explainOptionRecommendation(com.citytrip.model.dto.GenerateReqDTO userReq, com.citytrip.model.vo.ItineraryOptionVO option) {
                return "";
            }

            @Override
            public String explainPoiChoice(com.citytrip.model.dto.GenerateReqDTO userReq, com.citytrip.model.vo.ItineraryNodeVO node) {
                return "";
            }

            @Override
            public SmartFillVO parseSmartFill(String text, List<String> poiNameHints) {
                SmartFillVO vo = new SmartFillVO();
                vo.setCityName("\u6210\u90fd");
                return vo;
            }

            @Override
            public com.citytrip.model.vo.DepartureLegEstimateVO estimateDepartureLeg(com.citytrip.model.dto.GenerateReqDTO userReq, com.citytrip.model.vo.ItineraryNodeVO firstNode) {
                return null;
            }

            @Override
            public com.citytrip.model.vo.SegmentTransportAnalysisVO analyzeSegmentTransport(com.citytrip.model.dto.GenerateReqDTO userReq, com.citytrip.model.vo.ItineraryNodeVO fromNode, com.citytrip.model.vo.ItineraryNodeVO toNode) {
                return null;
            }
        };

        RoutingLlmServiceImpl service = new RoutingLlmServiceImpl(aiAdapter, realLlmService, mockLlmService, properties);

        SmartFillVO result = service.parseSmartFill("\u6211\u60f3\u53bb\u4e07\u8c61\u57ce", List.of());

        assertThat(result.getCityName()).isEqualTo("\u6210\u90fd");
        verifyNoInteractions(realLlmService);
        verifyNoInteractions(mockLlmService);
    }

    @Test
    void springShouldWireRoutingLlmServiceWithLangChainAdapterBean() {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            LlmProperties properties = new LlmProperties();
            context.registerBean(LlmProperties.class, () -> properties);
            RealChatGatewayService realChatService = mock(RealChatGatewayService.class);
            RealLlmGatewayService realLlmService = mock(RealLlmGatewayService.class);
            MockLlmServiceImpl mockLlmService = mock(MockLlmServiceImpl.class);
            SmartFillVO delegated = new SmartFillVO();
            delegated.setCityName("\u6210\u90fd");
            delegated.setMustVisitPoiNames(List.of("\u6210\u90fd\u4e07\u8c61\u57ce"));
            when(realLlmService.parseSmartFill(any(), any())).thenReturn(delegated);
            context.registerBean(RealChatGatewayService.class, () -> realChatService);
            context.registerBean(RealLlmGatewayService.class, () -> realLlmService);
            context.registerBean(MockLlmServiceImpl.class, () -> mockLlmService);
            context.registerBean(AiPlatformConfig.class);
            context.registerBean(AiSceneRouter.class);
            context.registerBean(AiExecutionContextFactory.class);
            context.registerBean(LangChainAiOrchestrator.class);
            context.registerBean(LangChainLlmServiceAdapter.class);
            context.registerBean(RoutingLlmServiceImpl.class, () -> new RoutingLlmServiceImpl(realLlmService, mockLlmService, properties));

            assertThatCode(context::refresh).doesNotThrowAnyException();

            RoutingLlmServiceImpl service = context.getBean(RoutingLlmServiceImpl.class);
            SmartFillVO result = service.parseSmartFill("\u6211\u60f3\u53bb\u4e07\u8c61\u57ce", List.of());

            assertThat(result).isNotNull();
            assertThat(result.getCityName()).isEqualTo("\u6210\u90fd");
            assertThat(result.getMustVisitPoiNames()).contains("\u6210\u90fd\u4e07\u8c61\u57ce");
        }
    }
}

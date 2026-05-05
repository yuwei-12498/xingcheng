package com.citytrip.service.impl;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class OpenAiLlmServiceImpl implements LlmService {

    private final RealLlmGatewayService realLlmGatewayService;

    public OpenAiLlmServiceImpl(RealLlmGatewayService realLlmGatewayService) {
        this.realLlmGatewayService = realLlmGatewayService;
    }

    @Override
    public String generateRouteWarmTip(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return realLlmGatewayService.generateRouteWarmTip(userReq, nodes);
    }

    @Override
    public String explainOptionRecommendation(GenerateReqDTO userReq, ItineraryOptionVO option) {
        return realLlmGatewayService.explainOptionRecommendation(userReq, option);
    }

    @Override
    public RouteCriticDecisionVO criticSelectItineraryOption(GenerateReqDTO userReq, List<ItineraryOptionVO> options) {
        return realLlmGatewayService.criticSelectItineraryOption(userReq, options);
    }

    @Override
    public String explainPoiChoice(GenerateReqDTO userReq, ItineraryNodeVO node) {
        return realLlmGatewayService.explainPoiChoice(userReq, node);
    }

    @Override
    public SmartFillVO parseSmartFill(String text, List<String> poiNameHints) {
        return realLlmGatewayService.parseSmartFill(text, poiNameHints);
    }

    @Override
    public DepartureLegEstimateVO estimateDepartureLeg(GenerateReqDTO userReq, ItineraryNodeVO firstNode) {
        return realLlmGatewayService.estimateDepartureLeg(userReq, firstNode);
    }

    @Override
    public SegmentTransportAnalysisVO analyzeSegmentTransport(GenerateReqDTO userReq, ItineraryNodeVO fromNode, ItineraryNodeVO toNode) {
        return realLlmGatewayService.analyzeSegmentTransport(userReq, fromNode, toNode);
    }

    @Override
    public ItineraryRouteDecorationVO decorateRouteExperience(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return realLlmGatewayService.decorateRouteExperience(userReq, nodes);
    }
}

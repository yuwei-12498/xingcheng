package com.citytrip.service;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.model.vo.SmartFillVO;

import java.util.List;

public interface LlmService {

    /**
     * 根据整条路线生成一条总的温馨提示
     */
    String generateRouteWarmTip(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes);

    /**
     * 生成某条候选路线的推荐理由
     */
    String explainOptionRecommendation(GenerateReqDTO userReq, ItineraryOptionVO option);

    default RouteCriticDecisionVO criticSelectItineraryOption(GenerateReqDTO userReq, List<ItineraryOptionVO> options) {
        return null;
    }

    /**
     * 解释某一个具体点位为什么入选
     */
    String explainPoiChoice(GenerateReqDTO userReq, ItineraryNodeVO node);

    /**
     * 智能填写：将自然语言转换为表单字段
     */
    SmartFillVO parseSmartFill(String text, List<String> poiNameHints);

    /**
     * 估算“当前位置 -> 第一个景点”的首段通行方式与时长
     */
    DepartureLegEstimateVO estimateDepartureLeg(GenerateReqDTO userReq, ItineraryNodeVO firstNode);

    SegmentTransportAnalysisVO analyzeSegmentTransport(GenerateReqDTO userReq, ItineraryNodeVO fromNode, ItineraryNodeVO toNode);

    default ItineraryRouteDecorationVO decorateRouteExperience(GenerateReqDTO userReq, List<ItineraryNodeVO> nodes) {
        return null;
    }
}

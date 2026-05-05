package com.citytrip.service.domain.ai;

import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.DepartureLegEstimateVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryRouteDecorationVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.RouteNodeDecorationVO;
import com.citytrip.model.vo.RoutePathPointVO;
import com.citytrip.model.vo.RouteCriticDecisionVO;
import com.citytrip.model.vo.SegmentRouteGuideVO;
import com.citytrip.model.vo.SegmentRouteStepVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import com.citytrip.service.LlmService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.core.task.AsyncTaskExecutor;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ItineraryAiDecorationServiceTest {

    @Test
    void applyWarmTipsReturnsFallbackQuicklyWhenRouteWarmTipCallHangs() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 120L);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(buildNode("慢速路线")));

        when(llmService.generateRouteWarmTip(any(), anyList())).thenAnswer(invocation -> {
            sleep(2_000L);
            return "slow route tip";
        });

        assertTimeoutPreemptively(Duration.ofMillis(1_000), () -> service.applyWarmTips(itinerary, new GenerateReqDTO()));
        assertThat(itinerary.getTips()).isNotBlank();
        assertThat(itinerary.getTips()).isNotEqualTo("slow route tip");
    }

    @Test
    void decorateWithLlmReturnsWithoutWaitingForSlowOptionExplanation() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 120L);

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setRecommendReason("规则推荐：综合更稳。");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setRecommendReason("规则推荐：综合更稳。");
        itinerary.setOptions(List.of(option));
        GenerateReqDTO req = new GenerateReqDTO();

        when(llmService.explainOptionRecommendation(any(), any())).thenAnswer(invocation -> {
            sleep(2_000L);
            return "slow option reason";
        });
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("快速提示：按主线慢慢走。");

        ItineraryVO result = assertTimeoutPreemptively(
                Duration.ofMillis(1_000),
                () -> service.decorateWithLlm(itinerary, req)
        );

        assertThat(result.getOptions()).hasSize(1);
        assertThat(result.getOptions().get(0).getRecommendReason()).isEqualTo("规则推荐：综合更稳。");
        assertThat(result.getRecommendReason()).isEqualTo("规则推荐：综合更稳。");
        assertThat(result.getTips()).isNotBlank();
    }

    @Test
    void decorateWithLlmUsesNarrativeFallbackWhenAiOptionExplanationTimesOut() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 120L);

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setRecommendReason("规则推荐：这条路线综合得分最高。");
        option.setNodes(List.of(
                buildNode("望江楼公园"),
                buildNode("文殊院"),
                buildNode("宽窄巷子")
        ));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setRecommendReason(option.getRecommendReason());
        itinerary.setOptions(List.of(option));

        when(llmService.explainOptionRecommendation(any(), any())).thenAnswer(invocation -> {
            sleep(2_000L);
            return "slow option reason";
        });
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("快速提示：按主线慢慢走。");

        ItineraryVO result = assertTimeoutPreemptively(
                Duration.ofMillis(1_000),
                () -> service.decorateWithLlm(itinerary, new GenerateReqDTO())
        );

        assertThat(result.getOptions()).hasSize(1);
        assertThat(result.getOptions().get(0).getRecommendReason()).contains("望江楼公园");
        assertThat(result.getOptions().get(0).getRecommendReason()).doesNotContain("综合得分最高");
    }

    @Test
    void decorateWithLlmUsesAiOptionRecommendationForSelectedOptionAndItinerary() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setRecommendReason("规则推荐：这条路线综合得分最高。");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setRecommendReason("规则推荐：默认选择综合最稳的路线。");
        itinerary.setOptions(List.of(option));

        when(llmService.explainOptionRecommendation(any(), any()))
                .thenReturn("AI 结合你的偏好、预算和通行距离判断，这条路线在主题匹配与执行稳定性之间更均衡。");
        when(llmService.generateRouteWarmTip(any(), anyList()))
                .thenReturn("今天按主线慢慢走，给休息和拍照留一点机动时间。");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getOptions()).hasSize(1);
        assertThat(result.getOptions().get(0).getRecommendReason())
                .isEqualTo("AI 结合你的偏好、预算和通行距离判断，这条路线在主题匹配与执行稳定性之间更均衡。");
        assertThat(result.getRecommendReason())
                .isEqualTo("AI 结合你的偏好、预算和通行距离判断，这条路线在主题匹配与执行稳定性之间更均衡。");
        verify(llmService).explainOptionRecommendation(any(), any());
    }

    @Test
    void decorateWithLlmLetsCriticSelectAcrossCandidateOptionsAndExplainRejections() {
        LlmService llmService = mock(LlmService.class);
        ItineraryComparisonAssembler comparisonAssembler = mock(ItineraryComparisonAssembler.class);
        ItineraryAiDecorationService service = buildService(llmService, comparisonAssembler, 1_000L);

        GenerateReqDTO req = new GenerateReqDTO();
        req.setNaturalLanguageRequirement("我想少走路，预算可以稍微高一点，晚上别太赶。");

        ItineraryOptionVO balanced = new ItineraryOptionVO();
        balanced.setOptionKey("balanced");
        balanced.setRecommendReason("规则默认均衡方案");
        balanced.setNodes(List.of(buildNode("宽窄巷子")));
        balanced.setTotalCost(BigDecimal.valueOf(80));
        balanced.setTotalDuration(180);

        ItineraryOptionVO efficient = new ItineraryOptionVO();
        efficient.setOptionKey("efficient");
        efficient.setRecommendReason("规则高效方案");
        efficient.setNodes(List.of(buildNode("成都博物馆")));
        efficient.setTotalCost(BigDecimal.valueOf(120));
        efficient.setTotalDuration(150);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setOptions(List.of(balanced, efficient));
        itinerary.setNodes(balanced.getNodes());
        itinerary.setRecommendReason("规则默认均衡方案");
        itinerary.setTotalCost(balanced.getTotalCost());
        itinerary.setTotalDuration(balanced.getTotalDuration());

        RouteCriticDecisionVO decision = new RouteCriticDecisionVO();
        decision.setSelectedOptionKey("efficient");
        decision.setReason("AI Critic 认为高效方案步行更少、晚间风险更低，更符合这次自然语言偏好。");
        decision.setRejectedReasons(Map.of("balanced", "均衡方案绕行更多，晚间节奏偏紧。"));
        decision.setOptionScores(Map.of("balanced", 72.0D, "efficient", 91.0D));

        when(llmService.criticSelectItineraryOption(any(), anyList())).thenReturn(decision);
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("按高效路线走，晚间留足返程时间。");
        when(comparisonAssembler.buildComparisonTips(any(), anyList(), any()))
                .thenReturn("AI 已对 2 套候选路线做常识评估。");

        ItineraryVO result = service.decorateWithLlm(itinerary, req);

        assertThat(result.getSelectedOptionKey()).isEqualTo("efficient");
        assertThat(result.getNodes()).extracting(ItineraryNodeVO::getPoiName).containsExactly("成都博物馆");
        assertThat(result.getTotalCost()).isEqualByComparingTo("120");
        assertThat(result.getRecommendReason()).isEqualTo(decision.getReason());
        assertThat(result.getCriticReason()).isEqualTo(decision.getReason());
        assertThat(result.getRejectedOptionReasons()).containsEntry("balanced", "均衡方案绕行更多，晚间节奏偏紧。");
        assertThat(result.getOptions().get(0).getNotRecommendReason()).contains("绕行更多");
        verify(llmService).criticSelectItineraryOption(any(), anyList());
        verify(llmService, never()).explainOptionRecommendation(any(), any());
    }

    @Test
    void criticScoresAreAppliedAndSelectedOptionIsNeverMarkedRejected() {
        LlmService llmService = mock(LlmService.class);
        ItineraryComparisonAssembler comparisonAssembler = mock(ItineraryComparisonAssembler.class);
        ItineraryAiDecorationService service = buildService(llmService, comparisonAssembler, 1_000L);

        ItineraryOptionVO balanced = new ItineraryOptionVO();
        balanced.setOptionKey("balanced");
        balanced.setRecommendReason("规则默认均衡方案");
        balanced.setNodes(List.of(buildNode("宽窄巷子")));

        ItineraryOptionVO efficient = new ItineraryOptionVO();
        efficient.setOptionKey("efficient");
        efficient.setRecommendReason("规则高效方案");
        efficient.setNodes(List.of(buildNode("成都博物馆")));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setOptions(List.of(balanced, efficient));
        itinerary.setNodes(balanced.getNodes());

        RouteCriticDecisionVO decision = new RouteCriticDecisionVO();
        decision.setSelectedOptionKey("efficient");
        decision.setReason("高效方案更符合少走路偏好。");
        decision.setRejectedReasons(Map.of(
                "balanced", "均衡方案绕行略多。",
                "efficient", "模型误把已选方案写进淘汰原因。"
        ));
        decision.setOptionScores(Map.of("balanced", 70.0D, "efficient", 94.0D));

        when(llmService.criticSelectItineraryOption(any(), anyList())).thenReturn(decision);
        when(comparisonAssembler.buildComparisonTips(any(), anyList(), any()))
                .thenReturn("AI 已对候选路线做常识评估。");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getOptions().get(0).getCriticScore()).isEqualTo(70.0D);
        assertThat(result.getOptions().get(1).getCriticScore()).isEqualTo(94.0D);
        assertThat(result.getOptions().get(1).getNotRecommendReason()).isNull();
        assertThat(result.getRejectedOptionReasons()).doesNotContainKey("efficient");
        assertThat(result.getRejectedOptionReasons()).containsEntry("balanced", "均衡方案绕行略多。");
    }

    @Test
    void decorateWithLlmKeepsRuleOptionRecommendationWhenModelReturnsEnglish() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setRecommendReason("规则推荐：这条路线综合得分最高。");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("balanced");
        itinerary.setRecommendReason("规则推荐：这条路线综合得分最高。");
        itinerary.setOptions(List.of(option));

        when(llmService.explainOptionRecommendation(any(), any()))
                .thenReturn("This option is balanced and efficient for the selected preferences.");
        when(llmService.generateRouteWarmTip(any(), anyList()))
                .thenReturn("今天按主线慢慢走，给休息和拍照留一点机动时间。");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getOptions().get(0).getRecommendReason()).isEqualTo("规则推荐：这条路线综合得分最高。");
        assertThat(result.getRecommendReason()).isEqualTo("规则推荐：这条路线综合得分最高。");
        verify(llmService).explainOptionRecommendation(any(), any());
    }

    @Test
    void decorateWithLlmDoesNotGenerateHiddenPoiWarmTipsButKeepsRouteWarmTip() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(
                buildNode("青城山"),
                buildNode("IFS 国际金融中心")
        ));

        when(llmService.explainPoiChoice(any(), any())).thenReturn(null);
        when(llmService.generateRouteWarmTip(any(), anyList()))
                .thenReturn("今天先顺着主线走，拍照和休息都别压太满。");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getTips()).isEqualTo("今天先顺着主线走，拍照和休息都别压太满。");
        assertThat(result.getNodes()).hasSize(2);
        assertThat(result.getNodes().get(0).getWarmTipCandidates()).isNull();
        assertThat(result.getNodes().get(0).getSelectedWarmTip()).isNull();
        assertThat(result.getNodes().get(0).getStatusNote()).isNull();
        assertThat(result.getNodes().get(1).getWarmTipCandidates()).isNull();
        assertThat(result.getNodes().get(1).getSelectedWarmTip()).isNull();
        assertThat(result.getNodes().get(1).getStatusNote()).isNull();
    }

    @Test
    void decorateWithLlmFallsBackToChineseCopyWhenModelReturnsEnglish() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryNodeVO node = buildNode("青城山");
        node.setStatusNote("原始中文提示");
        node.setSysReason("原始中文理由");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(node));
        itinerary.setTips("原始中文温馨提示");
        itinerary.setRecommendReason("原始中文推荐理由");

        when(llmService.explainPoiChoice(any(), any())).thenReturn("This stop is great for photos and quick access.");
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("Keep moving and skip long queues.");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getTips()).doesNotContainPattern("[A-Za-z]{4,}");
        assertThat(result.getRecommendReason()).doesNotContainPattern("[A-Za-z]{4,}");
        assertThat(result.getNodes().get(0).getSysReason()).doesNotContainPattern("[A-Za-z]{4,}");
        assertThat(result.getNodes().get(0).getWarmTipCandidates()).isNull();
        assertThat(result.getNodes().get(0).getSelectedWarmTip()).isNull();
    }

    @Test
    void decorateWithLlmAddsDepartureLegEstimateForFirstNode() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        GenerateReqDTO req = new GenerateReqDTO();
        req.setDepartureLatitude(30.65901D);
        req.setDepartureLongitude(104.19765D);

        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName("宽窄巷子");
        node.setPoiId(4L);
        node.setLatitude(BigDecimal.valueOf(30.665D));
        node.setLongitude(BigDecimal.valueOf(104.053D));
        node.setTravelTime(22);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(node));

        DepartureLegEstimateVO estimate = new DepartureLegEstimateVO();
        estimate.setTransportMode("地铁+步行");
        estimate.setEstimatedMinutes(39);
        estimate.setEstimatedDistanceKm(BigDecimal.valueOf(15.8D));

        when(llmService.estimateDepartureLeg(any(), any())).thenReturn(estimate);
        when(llmService.explainPoiChoice(any(), any())).thenReturn(null);
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("今天按主线慢慢走。");

        ItineraryVO result = service.decorateWithLlm(itinerary, req);
        ItineraryNodeVO firstNode = result.getNodes().get(0);
        assertThat(firstNode.getDepartureTransportMode()).isEqualTo("地铁+步行");
        assertThat(firstNode.getDepartureTravelTime()).isEqualTo(39);
        assertThat(firstNode.getDepartureDistanceKm()).isEqualTo(BigDecimal.valueOf(15.8D).setScale(1));
    }

    @Test
    void decorateWithLlmAddsSegmentTransportNarrativeWithoutHiddenWarmTipFields() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryNodeVO first = new ItineraryNodeVO();
        first.setPoiId(1L);
        first.setPoiName("宽窄巷子");
        first.setTravelTime(10);
        first.setDepartureTravelTime(15);
        first.setDepartureTransportMode("地铁+步行");
        first.setDepartureDistanceKm(BigDecimal.valueOf(5.4D));

        ItineraryNodeVO second = new ItineraryNodeVO();
        second.setPoiId(2L);
        second.setPoiName("博物馆");
        second.setTravelTime(22);
        second.setTravelTransportMode("地铁+步行");
        second.setTravelDistanceKm(BigDecimal.valueOf(6.2D));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(first, second));

        SegmentTransportAnalysisVO segmentAnalysis = new SegmentTransportAnalysisVO();
        segmentAnalysis.setTransportMode("地铁+步行");
        segmentAnalysis.setNarrative("从上一站到这一站建议地铁接驳，少绕路也更稳。");

        when(llmService.explainPoiChoice(any(), any())).thenReturn(null);
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("今天按主线慢慢走。");
        when(llmService.analyzeSegmentTransport(any(), any(), any())).thenReturn(segmentAnalysis);

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getNodes().get(0).getWarmTipCandidates()).isNull();
        assertThat(result.getNodes().get(0).getSelectedWarmTip()).isNull();
        assertThat(result.getNodes().get(0).getStatusNote()).isNull();
        assertThat(result.getNodes().get(1).getTravelTransportMode()).isEqualTo("地铁+步行");
        assertThat(result.getNodes().get(1).getTravelNarrative()).contains("地铁接驳");
    }

    @Test
    void decorateWithLlmPrefersBatchedRouteDecorationButIgnoresNodeWarmTips() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 2_000L);

        GenerateReqDTO req = new GenerateReqDTO();
        req.setDeparturePlaceName("酒店");

        ItineraryNodeVO first = new ItineraryNodeVO();
        first.setPoiId(1L);
        first.setPoiName("宽窄巷子");
        first.setDepartureTransportMode("地铁+步行");
        first.setDepartureTravelTime(18);
        first.setDepartureDistanceKm(BigDecimal.valueOf(4.2D));

        ItineraryNodeVO second = new ItineraryNodeVO();
        second.setPoiId(2L);
        second.setPoiName("博物馆");
        second.setTravelTransportMode("骑行");
        second.setTravelTime(12);
        second.setTravelDistanceKm(BigDecimal.valueOf(2.0D));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(first, second));

        RouteNodeDecorationVO firstDecoration = new RouteNodeDecorationVO();
        firstDecoration.setIndex(0);
        firstDecoration.setTransportMode("地铁+步行");
        firstDecoration.setNarrative("从酒店到宽窄巷子先坐地铁，落地后步行进街区最顺。");

        RouteNodeDecorationVO secondDecoration = new RouteNodeDecorationVO();
        secondDecoration.setIndex(1);
        secondDecoration.setTransportMode("骑行");
        secondDecoration.setNarrative("从宽窄巷子去博物馆这段更适合骑行，节奏更连贯。");

        ItineraryRouteDecorationVO decoration = new ItineraryRouteDecorationVO();
        decoration.setRouteWarmTip("今天先走主线，别把拍照和休息都压到最后。");
        decoration.setNodes(List.of(firstDecoration, secondDecoration));

        when(llmService.decorateRouteExperience(any(), anyList())).thenReturn(decoration);

        ItineraryVO result = service.decorateWithLlm(itinerary, req);

        assertThat(result.getTips()).isEqualTo("今天先走主线，别把拍照和休息都压到最后。");
        assertThat(result.getNodes().get(0).getWarmTipCandidates()).isNull();
        assertThat(result.getNodes().get(0).getSelectedWarmTip()).isNull();
        assertThat(result.getNodes().get(0).getStatusNote()).isNull();
        assertThat(result.getNodes().get(0).getDepartureTransportMode()).isEqualTo("地铁+步行");
        assertThat(result.getNodes().get(0).getTravelNarrative()).contains("宽窄巷子");
        assertThat(result.getNodes().get(1).getTravelTransportMode()).isEqualTo("骑行");
        assertThat(result.getNodes().get(1).getTravelNarrative()).contains("博物馆");
    }

    @Test
    void decorateWithLlmNormalizesEnglishTransportModesToChineseLabels() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 2_000L);

        ItineraryNodeVO first = new ItineraryNodeVO();
        first.setPoiId(1L);
        first.setPoiName("宽窄巷子");
        first.setDepartureTravelTime(8);
        first.setDepartureDistanceKm(BigDecimal.valueOf(0.8D));

        ItineraryNodeVO second = new ItineraryNodeVO();
        second.setPoiId(2L);
        second.setPoiName("成都博物馆");
        second.setTravelTime(16);
        second.setTravelDistanceKm(BigDecimal.valueOf(2.2D));

        RouteNodeDecorationVO firstDecoration = new RouteNodeDecorationVO();
        firstDecoration.setIndex(0);
        firstDecoration.setTransportMode("walk");
        firstDecoration.setNarrative("从当前位置步行到第一站，距离短且不用绕路。");

        RouteNodeDecorationVO secondDecoration = new RouteNodeDecorationVO();
        secondDecoration.setIndex(1);
        secondDecoration.setTransportMode("bike");
        secondDecoration.setNarrative("这一段骑行更顺，能减少等车时间。");

        ItineraryRouteDecorationVO decoration = new ItineraryRouteDecorationVO();
        decoration.setNodes(List.of(firstDecoration, secondDecoration));

        when(llmService.decorateRouteExperience(any(), anyList())).thenReturn(decoration);

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(first, second));

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result.getNodes().get(0).getDepartureTransportMode()).isEqualTo("步行");
        assertThat(result.getNodes().get(0).getTravelTransportMode()).isEqualTo("步行");
        assertThat(result.getNodes().get(1).getTravelTransportMode()).isEqualTo("骑行");
        assertThat(result.getNodes().get(0).getTravelNarrative()).doesNotContain("??");
        assertThat(result.getNodes().get(1).getTravelNarrative()).doesNotContain("??");
    }

    @Test
    void decorateWithLlmPreservesSegmentRouteGuideDuringCopyAndDecoration() {
        LlmService llmService = mock(LlmService.class);
        ItineraryAiDecorationService service = buildService(llmService, mock(ItineraryComparisonAssembler.class), 500L);

        ItineraryNodeVO rootNode = buildNode("Root Stop");
        rootNode.setSegmentRouteGuide(buildSegmentRouteGuide("root-guide"));

        ItineraryNodeVO optionNode = buildNode("Option Stop");
        optionNode.setSegmentRouteGuide(buildSegmentRouteGuide("option-guide"));
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setNodes(List.of(optionNode));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(rootNode));
        itinerary.setOptions(List.of(option));

        when(llmService.explainPoiChoice(any(), any())).thenReturn(null);
        when(llmService.analyzeSegmentTransport(any(), any(), any())).thenReturn(null);
        when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("Route tip");

        ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

        assertThat(result).isNotSameAs(itinerary);
        assertThat(result.getNodes()).hasSize(1);
        assertThat(result.getNodes().get(0).getSegmentRouteGuide()).isNotNull();
        assertThat(result.getNodes().get(0).getSegmentRouteGuide()).isNotSameAs(rootNode.getSegmentRouteGuide());
        assertThat(result.getNodes().get(0).getSegmentRouteGuide().getSummary()).isEqualTo("root-guide");
        assertThat(result.getNodes().get(0).getSegmentRouteGuide().getSteps()).hasSize(1);
        assertThat(result.getNodes().get(0).getSegmentRouteGuide().getPathPoints()).hasSize(2);
        assertThat(result.getOptions()).hasSize(1);
        assertThat(result.getOptions().get(0).getNodes()).hasSize(1);
        assertThat(result.getOptions().get(0).getNodes().get(0).getSegmentRouteGuide()).isNotNull();
        assertThat(result.getOptions().get(0).getNodes().get(0).getSegmentRouteGuide().getSummary()).isEqualTo("option-guide");
    }

    private ItineraryNodeVO buildNode(String poiName) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(poiName);
        return node;
    }

    private SegmentRouteGuideVO buildSegmentRouteGuide(String summary) {
        SegmentRouteStepVO step = new SegmentRouteStepVO();
        step.setStepOrder(1);
        step.setType("walk");
        step.setInstruction("Walk to the next stop");
        step.setDistanceMeters(320);
        step.setDurationMinutes(5);
        step.setPathPoints(List.of(buildRoutePoint("30.6573", "104.0817"), buildRoutePoint("30.6581", "104.0792")));

        SegmentRouteGuideVO guide = new SegmentRouteGuideVO();
        guide.setSummary(summary);
        guide.setTransportMode("walk");
        guide.setDurationMinutes(5);
        guide.setDistanceKm(new BigDecimal("0.3"));
        guide.setDetailAvailable(true);
        guide.setSteps(List.of(step));
        guide.setPathPoints(List.of(buildRoutePoint("30.6573", "104.0817"), buildRoutePoint("30.6581", "104.0792")));
        guide.setSource("provider");
        return guide;
    }

    private RoutePathPointVO buildRoutePoint(String latitude, String longitude) {
        RoutePathPointVO point = new RoutePathPointVO();
        point.setLatitude(new BigDecimal(latitude));
        point.setLongitude(new BigDecimal(longitude));
        return point;
    }

    private ItineraryAiDecorationService buildService(LlmService llmService,
                                                      ItineraryComparisonAssembler itineraryComparisonAssembler,
                                                      long timeoutMs) {
        return new ItineraryAiDecorationService(
                llmService,
                new ObjectMapper(),
                itineraryComparisonAssembler,
                buildExecutor(),
                timeoutMs
        );
    }

    private AsyncTaskExecutor buildExecutor() {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
        executor.setCorePoolSize(0);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(0);
        executor.setKeepAliveSeconds(5);
        executor.setAllowCoreThreadTimeOut(true);
        executor.setThreadNamePrefix("test-itinerary-ai-");
        executor.initialize();
        return executor;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrupted during test setup", ex);
        }
    }
}

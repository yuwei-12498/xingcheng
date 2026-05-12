package com.citytrip.service.ai.adapter;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.ai.orchestrator.AiSceneRouter;
import com.citytrip.service.ai.orchestrator.LangChainAiOrchestrator;
import com.citytrip.service.ai.rag.AiRetrieverFacade;
import com.citytrip.service.ai.rag.PoiKnowledgeRetriever;
import com.citytrip.service.ai.rag.RetrievalDocument;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LangChainLlmServiceAdapterTest {

    @Test
    void generateRouteWarmTipShouldDelegateToRealLlmServiceWhenAvailable() {
        com.citytrip.service.impl.RealLlmGatewayService realLlmService = mock(com.citytrip.service.impl.RealLlmGatewayService.class);
        when(realLlmService.generateRouteWarmTip(any(), any())).thenReturn("真实模型提示：去青城山注意防滑，量力而行。");

        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                null,
                realLlmService
        );

        GenerateReqDTO req = new GenerateReqDTO();
        req.setIsRainy(true);

        String tip = adapter.generateRouteWarmTip(req, List.of(
                buildNode("青城山"),
                buildNode("都江堰景区")
        ));

        assertThat(tip).isEqualTo("真实模型提示：去青城山注意防滑，量力而行。");
    }

    @Test
    void parseSmartFillShouldDelegateToRealLlmServiceWhenAvailable() {
        com.citytrip.service.impl.RealLlmGatewayService realLlmService = mock(com.citytrip.service.impl.RealLlmGatewayService.class);
        SmartFillVO delegated = new SmartFillVO();
        delegated.setCityName("成都");
        delegated.setMustVisitPoiNames(List.of("成都万象城"));
        when(realLlmService.parseSmartFill(any(), any())).thenReturn(delegated);

        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                null,
                realLlmService
        );

        SmartFillVO result = adapter.parseSmartFill("我想去万象城", List.of("成都万象城"));

        assertThat(result).isSameAs(delegated);
        assertThat(result.getMustVisitPoiNames()).containsExactly("成都万象城");
    }

    @Test
    void parseSmartFillShouldCorrectDelegatedCityAndBudgetWithUserText() {
        com.citytrip.service.impl.RealLlmGatewayService realLlmService = mock(com.citytrip.service.impl.RealLlmGatewayService.class);
        SmartFillVO delegated = new SmartFillVO();
        delegated.setCityName("成都");
        delegated.setBudgetLevel(null);
        delegated.setTotalBudget(null);
        delegated.setBudgetTight(false);
        delegated.setSummary(List.of("成都样例"));
        when(realLlmService.parseSmartFill(any(), any())).thenReturn(delegated);

        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                null,
                realLlmService
        );

        SmartFillVO result = adapter.parseSmartFill(
                "杭州一天预算200元，少走路，想逛博物馆和西湖",
                List.of("杭州西湖", "杭州博物馆")
        );

        assertThat(result).isSameAs(delegated);
        assertThat(result.getCityName()).isEqualTo("杭州");
        assertThat(result.getTotalBudget()).isEqualTo(200D);
        assertThat(result.getBudgetLevel()).isEqualTo("中");
        assertThat(result.getBudgetTight()).isTrue();
        assertThat(result.getSummary()).anyMatch(item -> item.contains("预算：200元"));
    }

    @Test
    void generateRouteWarmTipShouldReferenceActualRouteNodesInChinese() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );
        GenerateReqDTO req = new GenerateReqDTO();
        req.setIsNight(false);

        String tip = adapter.generateRouteWarmTip(req, List.of(
                buildNode("成都动物园"),
                buildNode("成都万象城")
        ));

        assertThat(tip).containsAnyOf("动物园", "万象城");
        assertThat(tip).doesNotContain(AiScene.ROUTE_WARM_TIP.name());
        assertThat(tip).hasSizeLessThanOrEqualTo(40);
        assertThat(tip).containsPattern("[\\u4e00-\\u9fff]");
    }

    @Test
    void generateRouteWarmTipShouldPreferWeatherOrNightRemindersWhenRelevant() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );
        GenerateReqDTO req = new GenerateReqDTO();
        req.setIsRainy(true);

        String tip = adapter.generateRouteWarmTip(req, List.of(
                buildNode("青城山"),
                buildNode("都江堰景区")
        ));

        assertThat(tip).containsAnyOf("雨", "防滑", "补水");
        assertThat(tip).hasSizeLessThanOrEqualTo(40);
    }

    @Test
    void explainOptionRecommendationShouldMentionActualStopsAndRouteTradeoff() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );
        GenerateReqDTO req = new GenerateReqDTO();
        req.setWalkingLevel("低");

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setTitle("轻松逛");
        option.setTotalDuration(360);
        option.setTotalCost(BigDecimal.valueOf(168));
        option.setNodes(List.of(
                buildNode("成都自然博物馆"),
                buildNode("成都万象城"),
                buildNode("东郊记忆")
        ));

        String explanation = adapter.explainOptionRecommendation(req, option);

        assertThat(explanation).containsAnyOf("自然博物馆", "万象城", "东郊记忆");
        assertThat(explanation).containsAnyOf("少走", "轻松", "顺路", "节奏");
        assertThat(explanation).doesNotContain(AiScene.OPTION_EXPLANATION.name());
    }

    @Test
    void explainPoiChoiceShouldUseNodeFactsInsteadOfPlaceholder() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );
        GenerateReqDTO req = new GenerateReqDTO();
        req.setThemes(List.of("文化"));

        ItineraryNodeVO node = buildNode("成都博物馆");
        node.setCategory("博物馆");
        node.setDistrict("青羊区");
        node.setSysReason("主题匹配高，适合作为中段核心站点");
        node.setScoreBreakdown(Map.of("theme", 8.5, "walking", 2.0));

        String explanation = adapter.explainPoiChoice(req, node);

        assertThat(explanation).containsAnyOf("成都博物馆", "博物馆", "青羊区");
        assertThat(explanation).containsAnyOf("文化", "主题", "中段", "顺路");
        assertThat(explanation).doesNotContain(AiScene.POI_EXPLANATION.name());
    }


    @Test
    void explainOptionRecommendationShouldAppendRagEvidenceWhenAvailable() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(context -> List.of(
                        new RetrievalDocument("city-guide", "成都自然博物馆周一闭馆，适合提前确认开放时间")
                )))
        );
        GenerateReqDTO req = new GenerateReqDTO();
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setNodes(List.of(
                buildNode("成都自然博物馆"),
                buildNode("东郊记忆")
        ));

        String explanation = adapter.explainOptionRecommendation(req, option);

        assertThat(explanation).contains("开放时间");
        assertThat(explanation).contains("成都自然博物馆");
    }

    @Test
    void explainPoiChoiceShouldAppendRagEvidenceWhenAvailable() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(context -> List.of(
                        new RetrievalDocument("poi-knowledge", "熊猫基地早高峰排队明显，建议尽量早到")
                )))
        );
        ItineraryNodeVO node = buildNode("成都大熊猫繁育研究基地");

        String explanation = adapter.explainPoiChoice(new GenerateReqDTO(), node);

        assertThat(explanation).contains("建议尽量早到");
        assertThat(explanation).containsAnyOf("熊猫基地", "大熊猫");
    }

    @Test
    void explainOptionRecommendationShouldUseBuiltInPoiKnowledgeEvidence() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new PoiKnowledgeRetriever()))
        );
        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setNodes(List.of(
                buildNode("成都自然博物馆"),
                buildNode("东郊记忆")
        ));

        String explanation = adapter.explainOptionRecommendation(new GenerateReqDTO(), option);

        assertThat(explanation).containsAnyOf("周一闭馆", "开放时间");
        assertThat(explanation).contains("自然博物馆");
    }

    @Test
    void explainPoiChoiceShouldUseBuiltInPoiKnowledgeEvidence() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                new AiRetrieverFacade(List.of(new PoiKnowledgeRetriever()))
        );
        ItineraryNodeVO node = buildNode("青城山");

        String explanation = adapter.explainPoiChoice(new GenerateReqDTO(), node);

        assertThat(explanation).containsAnyOf("防滑", "补水", "台阶");
        assertThat(explanation).contains("青城山");
    }

    @Test
    void parseSmartFillShouldExtractCanonicalMustVisitThemesAndWalkingPreference() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO result = adapter.parseSmartFill(
                "我想去万象城和自然博物馆，不想太累，想逛文化和购物路线",
                List.of("成都万象城", "成都自然博物馆", "东郊记忆")
        );

        assertThat(result.getCityName()).isEqualTo("成都");
        assertThat(result.getMustVisitPoiNames()).contains("成都万象城", "成都自然博物馆");
        assertThat(result.getThemes()).contains("文化", "购物", "休闲");
        assertThat(result.getWalkingLevel()).isEqualTo("低");
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都万象城"));
    }

    @Test
    void parseSmartFillShouldExtractDateTimeBudgetAndTripDays() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO result = adapter.parseSmartFill(
                "5月12日早上9点出发，预算300，玩一天，想去成都博物馆",
                List.of("成都博物馆", "成都自然博物馆")
        );

        assertThat(result.getCityName()).isEqualTo("成都");
        assertThat(result.getTripDate()).isEqualTo("2026-05-12");
        assertThat(result.getStartTime()).isEqualTo("09:00");
        assertThat(result.getTripDays()).isEqualTo(1.0D);
        assertThat(result.getBudgetLevel()).isEqualTo("中");
        assertThat(result.getMustVisitPoiNames()).contains("成都博物馆");
    }

    @Test
    void parseSmartFillFallbackShouldKeepExplicitCityAndExactBudget() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO result = adapter.parseSmartFill(
                "杭州一天预算200元，少走路，想逛博物馆和西湖",
                List.of("杭州西湖", "杭州博物馆")
        );

        assertThat(result.getCityName()).isEqualTo("杭州");
        assertThat(result.getTotalBudget()).isEqualTo(200D);
        assertThat(result.getBudgetLevel()).isEqualTo("中");
        assertThat(result.getBudgetTight()).isTrue();
    }

    @Test
    void parseSmartFillShouldDetectMallMonkeyConflictAndSuggestAnimalAlternatives() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO result = adapter.parseSmartFill(
                "我想去万象城看猴子",
                List.of("成都万象城", "成都动物园", "成都大熊猫繁育研究基地")
        );

        assertThat(result.getConflictWarnings()).anyMatch(item -> item.contains("万象城") && item.contains("猴子"));
        assertThat(result.getPreferredPoiCategories()).contains("动物园", "景区");
        assertThat(result.getExcludedPoiCategories()).contains("商场");
        assertThat(result.getAlternativePoiHints()).contains("成都动物园", "成都大熊猫繁育研究基地");
    }

    @Test
    void parseSmartFillShouldExtractHotpotIntentAsDirectFoodCategory() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO result = adapter.parseSmartFill(
                "我想吃火锅，预算80",
                List.of("蜀大侠火锅", "川菜博物馆")
        );

        assertThat(result.getPreferredPoiCategories()).contains("火锅", "餐饮");
        assertThat(result.getExcludedPoiCategories()).contains("博物馆");
        assertThat(result.getBudgetTight()).isTrue();
    }

    @Test
    void parseSmartFillShouldKeepHotpotCategoryWhenRealModelOmitsIt() {
        com.citytrip.service.impl.RealLlmGatewayService realLlmService = mock(com.citytrip.service.impl.RealLlmGatewayService.class);
        SmartFillVO delegated = new SmartFillVO();
        delegated.setThemes(List.of("美食"));
        delegated.setBudgetTight(false);
        when(realLlmService.parseSmartFill(any(), any())).thenReturn(delegated);

        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                null,
                realLlmService
        );

        SmartFillVO result = adapter.parseSmartFill(
                "我预算100想吃火锅",
                List.of("蜀大侠火锅", "自然风纱窗")
        );

        assertThat(result).isSameAs(delegated);
        assertThat(result.getPreferredPoiCategories()).contains("火锅", "餐饮");
        assertThat(result.getExcludedPoiCategories()).contains("五金", "家装", "装修材料", "纱窗");
        assertThat(result.getTotalBudget()).isEqualTo(100D);
        assertThat(result.getBudgetTight()).isTrue();
    }



    @Test
    void parseSmartFillShouldExtractConsumerPoiIntentBeyondHotpot() {
        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter())
        );

        SmartFillVO barbecue = adapter.parseSmartFill(
                "\u6211\u9884\u7b97100\u60f3\u5403\u70e4\u8089",
                List.of("\u738b\u5988\u624b\u6495\u70e4\u5154\u70e4\u8089", "\u81ea\u7136\u98ce\u7eb1\u7a97")
        );
        assertThat(barbecue.getThemes()).contains("\u7f8e\u98df");
        assertThat(barbecue.getPreferredPoiCategories()).contains("\u70e4\u8089", "\u70e7\u70e4", "\u9910\u996e");
        assertThat(barbecue.getExcludedPoiCategories()).contains("\u4e94\u91d1", "\u5bb6\u88c5", "\u7eb1\u7a97");
        assertThat(barbecue.getTotalBudget()).isEqualTo(100D);

        SmartFillVO netCafe = adapter.parseSmartFill(
                "\u6211\u60f3\u627e\u4e2a\u7f51\u5427\u6253\u6e38\u620f",
                List.of("\u718a\u732b\u7535\u7ade\u7f51\u5496", "\u6210\u90fd\u535a\u7269\u9986")
        );
        assertThat(netCafe.getThemes()).contains("\u4f11\u95f2");
        assertThat(netCafe.getPreferredPoiCategories()).contains("\u7f51\u5427", "\u7f51\u5496", "\u7535\u7ade", "\u5a31\u4e50");
        assertThat(netCafe.getExcludedPoiCategories()).contains("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1");

        SmartFillVO spa = adapter.parseSmartFill(
                "\u9644\u8fd1\u6709\u6ca1\u6709\u6d17\u6d74\u6309\u6469",
                List.of("\u6c64\u6cc9\u6d17\u6d74", "\u81ea\u7136\u535a\u7269\u9986")
        );
        assertThat(spa.getThemes()).contains("\u4f11\u95f2");
        assertThat(spa.getPreferredPoiCategories()).contains("\u6d17\u6d74", "\u8db3\u6d74", "\u6309\u6469", "\u4f11\u95f2");
        assertThat(spa.getExcludedPoiCategories()).contains("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1");
    }

    @Test
    void parseSmartFillShouldKeepConsumerPoiIntentWhenRealModelOmitsIt() {
        com.citytrip.service.impl.RealLlmGatewayService realLlmService = mock(com.citytrip.service.impl.RealLlmGatewayService.class);
        SmartFillVO delegated = new SmartFillVO();
        delegated.setThemes(List.of("\u8d2d\u7269"));
        delegated.setBudgetTight(false);
        when(realLlmService.parseSmartFill(any(), any())).thenReturn(delegated);

        LangChainLlmServiceAdapter adapter = new LangChainLlmServiceAdapter(
                new LangChainAiOrchestrator(new AiSceneRouter()),
                null,
                realLlmService
        );

        SmartFillVO result = adapter.parseSmartFill(
                "\u6211\u60f3\u627e\u4e2a\u7f51\u5496\u6253\u6e38\u620f\uff0c\u9884\u7b97120",
                List.of("\u718a\u732b\u7535\u7ade\u7f51\u5496", "\u6210\u90fd\u535a\u7269\u9986")
        );

        assertThat(result).isSameAs(delegated);
        assertThat(result.getPreferredPoiCategories()).contains("\u7f51\u5427", "\u7f51\u5496", "\u7535\u7ade", "\u5a31\u4e50");
        assertThat(result.getExcludedPoiCategories()).contains("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1");
        assertThat(result.getThemes()).contains("\u4f11\u95f2");
        assertThat(result.getTotalBudget()).isEqualTo(120D);
        assertThat(result.getBudgetTight()).isTrue();
    }

    private ItineraryNodeVO buildNode(String poiName) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(poiName);
        return node;
    }
}

package com.citytrip.service.application.itinerary;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
import com.citytrip.service.geo.CityResolverService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SmartFillUseCaseTest {

    @Test
    void shouldMapIfsAliasToCanonicalPoiName() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        Poi poi = new Poi();
        poi.setName("IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3");
        when(poiMapper.selectPlanningCandidates(false, null, 200)).thenReturn(List.of(poi));

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setSummary(List.of("\u8d2d\u7269"));
        llmOutput.setMustVisitPoiNames(List.of("IFS\u91d1\u878d\u4e2d\u5fc3"));
        when(llmService.parseSmartFill(eq("\u6211\u60f3\u53bbIFS\u91d1\u878d\u4e2d\u5fc3"), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText("\u6211\u60f3\u53bbIFS\u91d1\u878d\u4e2d\u5fc3");

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getMustVisitPoiNames()).contains("IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3");
        assertThat(result.getSummary().stream().anyMatch(item -> item.contains("IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3"))).isTrue();
    }

    @Test
    void shouldFallbackToLocalExtractionWhenLlmUnavailable() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "5月3日从春熙路出发，预算300，和女朋友一天逛熊猫和夜市，别太累，9点到18点";
        when(llmService.parseSmartFill(eq(text), anyList())).thenThrow(new RuntimeException("model unavailable"));

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getTripDays()).isEqualTo(1.0D);
        assertThat(result.getStartTime()).isEqualTo("09:00");
        assertThat(result.getEndTime()).isEqualTo("18:00");
        assertThat(result.getBudgetLevel()).isEqualTo("中");
        assertThat(result.getCompanionType()).isEqualTo("情侣");
        assertThat(result.getWalkingLevel()).isEqualTo("低");
        assertThat(result.getThemes()).contains("自然", "美食", "休闲");
        assertThat(result.getMustVisitPoiNames()).contains("成都大熊猫繁育研究基地");
        assertThat(result.getIsNight()).isTrue();
        assertThat(result.getDepartureText()).isEqualTo("春熙路");
    }


    @Test
    void shouldKeepExactNumericBudgetForStrictPlanning() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "Chengdu one day budget 200 rmb, prefer subway and walking";
        when(llmService.parseSmartFill(eq(text), anyList())).thenThrow(new RuntimeException("model unavailable"));

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getTotalBudget()).isEqualTo(200D);
        assertThat(result.getBudgetTight()).isTrue();
        assertThat(result.getSummary()).anyMatch(item -> item.contains("预算：200元"));
    }

    @Test
    void shouldExtractHotpotIntentCategoriesEvenWhenModelDoesNotReturnThem() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "我预算100想吃火锅";

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setThemes(List.of("美食"));
        llmOutput.setBudgetTight(false);
        when(llmService.parseSmartFill(eq(text), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getTotalBudget()).isEqualTo(100D);
        assertThat(result.getBudgetTight()).isTrue();
        assertThat(result.getThemes()).contains("美食");
        assertThat(result.getPreferredPoiCategories()).contains("火锅", "餐饮");
        assertThat(result.getExcludedPoiCategories()).contains("五金", "家装", "装修材料", "纱窗");
        assertThat(result.getSummary()).anyMatch(item -> item.contains("火锅"));
    }



    @Test
    void shouldExtractConsumerPoiIntentsBeyondHotpotWhenModelDoesNotReturnThem() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setThemes(List.of("\u8d2d\u7269"));
        llmOutput.setBudgetTight(false);
        when(llmService.parseSmartFill(eq("\u6211\u9884\u7b97100\u60f3\u5403\u70e4\u8089"), anyList())).thenReturn(llmOutput);
        when(llmService.parseSmartFill(eq("\u6211\u60f3\u627e\u4e2a\u7f51\u5427\u6253\u6e38\u620f"), anyList())).thenReturn(new SmartFillVO());
        when(llmService.parseSmartFill(eq("\u9644\u8fd1\u6709\u6ca1\u6709\u6d17\u6d74\u6309\u6469"), anyList())).thenReturn(new SmartFillVO());

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);

        SmartFillReqDTO barbecueReq = new SmartFillReqDTO();
        barbecueReq.setText("\u6211\u9884\u7b97100\u60f3\u5403\u70e4\u8089");
        SmartFillVO barbecue = useCase.parse(barbecueReq);
        assertThat(barbecue.getTotalBudget()).isEqualTo(100D);
        assertThat(barbecue.getThemes()).contains("\u7f8e\u98df");
        assertThat(barbecue.getPreferredPoiCategories()).contains("\u70e4\u8089", "\u70e7\u70e4", "\u9910\u996e");
        assertThat(barbecue.getExcludedPoiCategories()).contains("\u4e94\u91d1", "\u5bb6\u88c5", "\u7eb1\u7a97");

        SmartFillReqDTO netCafeReq = new SmartFillReqDTO();
        netCafeReq.setText("\u6211\u60f3\u627e\u4e2a\u7f51\u5427\u6253\u6e38\u620f");
        SmartFillVO netCafe = useCase.parse(netCafeReq);
        assertThat(netCafe.getThemes()).contains("\u4f11\u95f2");
        assertThat(netCafe.getPreferredPoiCategories()).contains("\u7f51\u5427", "\u7f51\u5496", "\u7535\u7ade", "\u5a31\u4e50");
        assertThat(netCafe.getExcludedPoiCategories()).contains("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1");

        SmartFillReqDTO spaReq = new SmartFillReqDTO();
        spaReq.setText("\u9644\u8fd1\u6709\u6ca1\u6709\u6d17\u6d74\u6309\u6469");
        SmartFillVO spa = useCase.parse(spaReq);
        assertThat(spa.getThemes()).contains("\u4f11\u95f2");
        assertThat(spa.getPreferredPoiCategories()).contains("\u6d17\u6d74", "\u8db3\u6d74", "\u6309\u6469", "\u4f11\u95f2");
        assertThat(spa.getExcludedPoiCategories()).contains("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1");
    }

    @Test
    void shouldPreferExplicitCityAndBudgetFromTextWhenModelReturnsGenericCity() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "杭州一天预算200元，少走路，想逛博物馆和西湖";

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setCityName("成都");
        llmOutput.setBudgetTight(false);
        llmOutput.setSummary(List.of("成都样例"));
        when(llmService.parseSmartFill(eq(text), anyList())).thenReturn(llmOutput);

        GeoSearchProperties geoSearchProperties = new GeoSearchProperties();
        SmartFillUseCase useCase = new SmartFillUseCase(
                llmService,
                poiMapper,
                null,
                null,
                new CityResolverService(geoSearchProperties)
        );
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getCityName()).isEqualTo("杭州");
        assertThat(result.getTotalBudget()).isEqualTo(200D);
        assertThat(result.getBudgetTight()).isTrue();
        assertThat(result.getSummary()).anyMatch(item -> item.contains("预算：200元"));
    }

    @Test
    void shouldExtractPandaIntentAsMustVisitPoiWithoutTurningItIntoDeparture() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "我想一个人去看大熊猫";

        Poi pandaBase = new Poi();
        pandaBase.setName("成都大熊猫繁育研究基地");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(pandaBase));
        when(llmService.parseSmartFill(eq(text), anyList())).thenThrow(new RuntimeException("model unavailable"));

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getCityName()).isEqualTo("成都");
        assertThat(result.getCompanionType()).isEqualTo("独自");
        assertThat(result.getThemes()).contains("自然");
        assertThat(result.getMustVisitPoiNames()).contains("成都大熊猫繁育研究基地");
        assertThat(result.getDepartureText()).isNull();
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都大熊猫繁育研究基地"));
    }

    @Test
    void shouldCanonicalizeShortPoiNamesReturnedByModel() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        Poi mall = new Poi();
        mall.setName("成都万象城");
        Poi museum = new Poi();
        museum.setName("成都自然博物馆");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(mall, museum));

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setMustVisitPoiNames(List.of("万象城", "自然博物馆"));
        when(llmService.parseSmartFill(eq("我想去万象城和自然博物馆"), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText("我想去万象城和自然博物馆");

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getMustVisitPoiNames()).contains("成都万象城", "成都自然博物馆");
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都万象城"));
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都自然博物馆"));
    }

    @Test
    void shouldExtractMultipleCanonicalPoisFromNaturalLanguageWhenModelUnavailable() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "我想去万象城、自然博物馆和东郊记忆，不想太累";

        Poi mall = new Poi();
        mall.setName("成都万象城");
        Poi museum = new Poi();
        museum.setName("成都自然博物馆");
        Poi memory = new Poi();
        memory.setName("东郊记忆");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(mall, museum, memory));
        when(llmService.parseSmartFill(eq(text), anyList())).thenThrow(new RuntimeException("model unavailable"));

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getWalkingLevel()).isEqualTo("低");
        assertThat(result.getMustVisitPoiNames()).contains("成都万象城", "成都自然博物馆", "东郊记忆");
    }

    @Test
    void shouldCanonicalizeCityShiPrefixedPoiNamesWithChinesePunctuation() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        String text = "我想去自然博物馆？预算200。";

        Poi museum = new Poi();
        museum.setName("成都市自然博物馆");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(museum));
        when(llmService.parseSmartFill(eq(text), anyList())).thenThrow(new RuntimeException("model unavailable"));

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText(text);

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getMustVisitPoiNames()).contains("成都市自然博物馆");
        assertThat(result.getTotalBudget()).isEqualTo(200D);
    }


    @Test
    void shouldUseAliasResolutionForMustVisitWhenModelReturnsShortPoiName() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setMustVisitPoiNames(List.of("动物园"));
        when(llmService.parseSmartFill(eq("我想去动物园"), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText("我想去动物园");

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getMustVisitPoiNames()).contains("成都动物园");
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都动物园"));
    }

    @Test
    void shouldUseAliasResolutionForMuseumShortNameReturnedByModel() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        Poi museum = new Poi();
        museum.setName("成都自然博物馆");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(museum));

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setMustVisitPoiNames(List.of("自然馆"));
        when(llmService.parseSmartFill(eq("我想去自然馆"), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText("我想去自然馆");

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getMustVisitPoiNames()).contains("成都自然博物馆");
        assertThat(result.getSummary()).anyMatch(item -> item.contains("成都自然博物馆"));
    }
    @Test
    void shouldWarnAndRecommendAnimalAlternativesForMallMonkeyConflict() {
        LlmService llmService = mock(LlmService.class);
        PoiMapper poiMapper = mock(PoiMapper.class);

        Poi mall = new Poi();
        mall.setName("成都万象城");
        Poi zoo = new Poi();
        zoo.setName("成都动物园");
        Poi panda = new Poi();
        panda.setName("成都大熊猫繁育研究基地");
        when(poiMapper.selectPlanningCandidates(false, null, null, "成都", 200)).thenReturn(List.of(mall, zoo, panda));

        SmartFillVO llmOutput = new SmartFillVO();
        llmOutput.setMustVisitPoiNames(List.of("成都万象城"));
        llmOutput.setConflictWarnings(List.of("万象城不适合看猴子"));
        llmOutput.setAlternativePoiHints(List.of("成都动物园", "成都大熊猫繁育研究基地"));
        llmOutput.setPreferredPoiCategories(List.of("动物园", "景区"));
        llmOutput.setExcludedPoiCategories(List.of("商场"));
        when(llmService.parseSmartFill(eq("我想去万象城看猴子"), anyList())).thenReturn(llmOutput);

        SmartFillUseCase useCase = new SmartFillUseCase(llmService, poiMapper);
        SmartFillReqDTO req = new SmartFillReqDTO();
        req.setText("我想去万象城看猴子");

        SmartFillVO result = useCase.parse(req);

        assertThat(result.getConflictWarnings()).anyMatch(item -> item.contains("万象城") && item.contains("猴子"));
        assertThat(result.getAlternativePoiHints()).contains("成都动物园", "成都大熊猫繁育研究基地");
    }
}

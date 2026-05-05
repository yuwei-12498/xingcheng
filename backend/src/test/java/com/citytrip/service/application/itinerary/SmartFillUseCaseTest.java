package com.citytrip.service.application.itinerary;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.SmartFillReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.SmartFillVO;
import com.citytrip.service.LlmService;
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
}

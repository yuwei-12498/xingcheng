package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.service.domain.ai.ChatPoiSkillService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PoiRecommendationSkillTest {

    @Test
    void supportsSceneBasedRecommendationQuestion() {
        PoiRecommendationSkill skill = new PoiRecommendationSkill(mock(ChatPoiSkillService.class));

        assertThat(skill.supports(request("两个人晚上适合去哪逛逛？"))).isTrue();
    }

    @Test
    void rejectsTransportQuestionAndNearbyHotelQuestion() {
        PoiRecommendationSkill skill = new PoiRecommendationSkill(mock(ChatPoiSkillService.class));

        assertThat(skill.supports(request("宽窄巷子到锦里怎么去"))).isFalse();
        assertThat(skill.supports(request("太古里附近酒店有什么推荐"))).isFalse();
    }

    @Test
    void executeShouldReturnStructuredCardsForRecommendedPois() {
        ChatPoiSkillService poiSkillService = mock(ChatPoiSkillService.class);
        PoiRecommendationSkill skill = new PoiRecommendationSkill(poiSkillService);
        ChatReqDTO req = request("两个人晚上适合去哪逛逛？");
        req.getContext().setUserLat(30.660000D);
        req.getContext().setUserLng(104.070000D);

        Poi poi = new Poi();
        poi.setId(1L);
        poi.setName("太古里");
        poi.setCategory("商圈");
        poi.setDistrict("锦江区");
        poi.setAddress("中纱帽街8号");
        poi.setLatitude(new BigDecimal("30.651500"));
        poi.setLongitude(new BigDecimal("104.081000"));
        poi.setCityName("成都");
        poi.setSourceType("local");

        when(poiSkillService.loadRelevantPois(same(req))).thenReturn(List.of(poi));

        ChatSkillPayloadVO payload = skill.execute(req);

        assertThat(payload.getSkillName()).isEqualTo("poi_recommendation");
        assertThat(payload.getStatus()).isEqualTo("ok");
        assertThat(payload.getResults()).hasSize(1);
        assertThat(payload.getResults().get(0).getName()).isEqualTo("太古里");
        assertThat(payload.getResults().get(0).getAddress()).isEqualTo("中纱帽街8号");
        assertThat(payload.getResults().get(0).getDistanceMeters()).isNotNull();
        assertThat(payload.getFallbackMessage()).contains("晚上");
        verify(poiSkillService).loadRelevantPois(same(req));
    }

    private ChatReqDTO request(String question) {
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setCityName("成都");
        context.setCompanionType("朋友");
        context.setNightMode(Boolean.TRUE);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion(question);
        req.setContext(context);
        return req;
    }
}

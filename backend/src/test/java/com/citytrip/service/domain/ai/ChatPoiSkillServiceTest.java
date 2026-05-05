package com.citytrip.service.domain.ai;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ChatPoiSkillServiceTest {

    @Test
    void loadRelevantPoisShouldPrioritizeQuestionAndPreferenceMatches() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        ChatPoiSkillService service = new ChatPoiSkillService(poiMapper);

        ChatReqDTO req = new ChatReqDTO();
        req.setQuestion("雨天带亲子想去杜甫草堂和博物馆，晚上还能去哪？");
        ChatReqDTO.ChatContext context = new ChatReqDTO.ChatContext();
        context.setRainy(Boolean.TRUE);
        context.setNightMode(Boolean.TRUE);
        context.setCompanionType("亲子");
        context.setPreferences(List.of("文化", "博物馆"));
        req.setContext(context);

        Poi dufu = poi(1L, "杜甫草堂", "博物馆", "青羊", "文化,历史", "亲子,情侣", 4.8, 1, 1, 0);
        Poi museum = poi(2L, "成都博物馆", "博物馆", "青羊", "文化,展览", "亲子,独自", 4.5, 1, 1, 0);
        Poi jiuyanqiao = poi(3L, "九眼桥", "夜游", "武侯", "夜景,酒吧", "朋友,情侣", 4.2, 0, 0, 1);

        when(poiMapper.selectPlanningCandidates(true, null, ChatPoiSkillService.POI_CANDIDATE_LIMIT))
                .thenReturn(List.of(jiuyanqiao, museum, dufu));

        List<Poi> selected = service.loadRelevantPois(req);

        assertThat(selected).hasSize(3);
        assertThat(selected.get(0).getName()).isEqualTo("杜甫草堂");
        assertThat(selected.get(1).getName()).isEqualTo("成都博物馆");
    }

    @Test
    void loadRelevantPoisShouldCapResultSize() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        ChatPoiSkillService service = new ChatPoiSkillService(poiMapper);

        List<Poi> many = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            many.add(poi((long) i, "POI" + i, "分类", "锦江", "标签" + i, "朋友", 3.0 + i, 1, 1, 1));
        }
        when(poiMapper.selectPlanningCandidates(false, null, ChatPoiSkillService.POI_CANDIDATE_LIMIT))
                .thenReturn(many);

        List<Poi> selected = service.loadRelevantPois(new ChatReqDTO());

        assertThat(selected).hasSize(ChatPoiSkillService.POI_CONTEXT_LIMIT);
    }

    @Test
    void loadRelevantPoisShouldReturnEmptyOnQueryFailure() {
        PoiMapper poiMapper = mock(PoiMapper.class);
        ChatPoiSkillService service = new ChatPoiSkillService(poiMapper);
        when(poiMapper.selectPlanningCandidates(false, null, ChatPoiSkillService.POI_CANDIDATE_LIMIT))
                .thenThrow(new IllegalStateException("db down"));

        List<Poi> selected = service.loadRelevantPois(new ChatReqDTO());

        assertThat(selected).isEmpty();
    }

    private Poi poi(Long id,
                    String name,
                    String category,
                    String district,
                    String tags,
                    String suitableFor,
                    double priority,
                    int indoor,
                    int rainFriendly,
                    int nightAvailable) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setCategory(category);
        poi.setDistrict(district);
        poi.setTags(tags);
        poi.setSuitableFor(suitableFor);
        poi.setPriorityScore(BigDecimal.valueOf(priority));
        poi.setIndoor(indoor);
        poi.setRainFriendly(rainFriendly);
        poi.setNightAvailable(nightAvailable);
        return poi;
    }
}


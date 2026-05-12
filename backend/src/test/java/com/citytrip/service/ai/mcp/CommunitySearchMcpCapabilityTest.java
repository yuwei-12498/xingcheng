package com.citytrip.service.ai.mcp;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunitySearchMcpCapabilityTest {

    @Test
    void shouldCallCommunityQueryServiceAndNormalizeResults() {
        CommunityItineraryQueryService queryService = mock(CommunityItineraryQueryService.class);
        CommunityItineraryVO item = new CommunityItineraryVO();
        item.setId(101L);
        item.setTitle("成都拍照夜景路线");
        item.setShareNote("下午拍照晚上看夜景");
        item.setRouteSummary("春熙路 -> IFS -> 太古里");
        item.setThemes(List.of("拍照", "citywalk"));
        item.setLikeCount(19L);
        item.setLiked(Boolean.TRUE);

        CommunityItineraryPageVO page = new CommunityItineraryPageVO();
        page.setRecords(List.of(item));
        when(queryService.listPublic(anyInt(), eq(2), anyString(), eq("成都拍照攻略"), isNull(), eq(66L)))
                .thenReturn(page);

        CommunitySearchMcpCapability capability = new CommunitySearchMcpCapability(queryService);

        Object result = capability.execute(Map.of(
                "keyword", "成都拍照攻略",
                "limit", 2,
                "currentUserId", 66L
        ));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.toString())
                .contains("community.search")
                .contains("成都拍照夜景路线")
                .contains("春熙路 -> IFS -> 太古里")
                .contains("拍照")
                .contains("true");
    }

    @Test
    void shouldReportUnavailableWhenCommunityServiceMissing() {
        CommunitySearchMcpCapability capability = new CommunitySearchMcpCapability(null);

        Object result = capability.execute(Map.of("keyword", "成都拍照攻略"));

        assertThat(result.toString()).contains("community.search", "unavailable");
    }
}

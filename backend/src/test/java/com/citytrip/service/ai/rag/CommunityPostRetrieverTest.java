package com.citytrip.service.ai.rag;

import com.citytrip.model.vo.CommunityItineraryPageVO;
import com.citytrip.model.vo.CommunityItineraryVO;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.application.community.CommunityItineraryQueryService;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CommunityPostRetrieverTest {

    @Test
    void shouldReturnCommunityDocumentsWithTitleRouteSummaryAndThemes() {
        CommunityItineraryQueryService queryService = mock(CommunityItineraryQueryService.class);
        CommunityItineraryVO item = new CommunityItineraryVO();
        item.setId(101L);
        item.setTitle("成都太古里春熙路拍照路线");
        item.setShareNote("下午拍照和夜景都比较集中");
        item.setRouteSummary("春熙路 -> IFS -> 太古里");
        item.setThemes(List.of("拍照", "citywalk"));
        item.setLikeCount(28L);
        item.setCommentCount(9L);
        item.setUpdatedAt(LocalDateTime.of(2026, 5, 10, 18, 30));

        CommunityItineraryPageVO page = new CommunityItineraryPageVO();
        page.setRecords(List.of(item));
        when(queryService.listPublic(anyInt(), anyInt(), anyString(), anyString(), isNull(), isNull()))
                .thenReturn(page);

        CommunityPostRetriever retriever = new CommunityPostRetriever(queryService);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("成都拍照路线攻略")
                .cityName("成都")
                .build());

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).source()).isEqualTo("community-post");
        assertThat(documents.get(0).content())
                .contains("成都太古里春熙路拍照路线")
                .contains("春熙路 -> IFS -> 太古里")
                .contains("拍照")
                .contains("28")
                .contains("9");
    }

    @Test
    void shouldReturnEmptyWhenCommunityServiceMissing() {
        CommunityPostRetriever retriever = new CommunityPostRetriever(null);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("成都拍照路线攻略")
                .cityName("成都")
                .build());

        assertThat(documents).isEmpty();
    }

    @Test
    void shouldMarkLikedCommunityRoutesForCurrentUser() {
        CommunityItineraryQueryService queryService = mock(CommunityItineraryQueryService.class);
        CommunityItineraryVO item = new CommunityItineraryVO();
        item.setId(102L);
        item.setTitle("成都商圈夜景路线");
        item.setRouteSummary("春熙路 -> IFS -> 太古里");
        item.setLiked(Boolean.TRUE);

        CommunityItineraryPageVO page = new CommunityItineraryPageVO();
        page.setRecords(List.of(item));
        when(queryService.listPublic(anyInt(), anyInt(), anyString(), anyString(), isNull(), eq(66L)))
                .thenReturn(page);

        CommunityPostRetriever retriever = new CommunityPostRetriever(queryService);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userId(66L)
                .userInput("成都夜景路线攻略")
                .cityName("成都")
                .build());

        assertThat(documents).singleElement().satisfies(document -> {
            assertThat(document.source()).isEqualTo("community-post-liked");
            assertThat(document.content()).contains("liked=true");
        });
    }
}

package com.citytrip.service.ai.rag;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RouteHistoryRetrieverTest {

    @Test
    void shouldReturnRouteSnapshotForCurrentItinerary() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);

        SavedItinerary entity = new SavedItinerary();
        entity.setId(11L);
        entity.setShareNote("拍照和商圈都在一条线上");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("option-1");
        itinerary.setRecommendReason("拍照和商圈顺路，晚上氛围更好");
        itinerary.setTotalDuration(420);
        itinerary.setTotalCost(BigDecimal.valueOf(188));
        itinerary.setNodes(List.of(node("春熙路"), node("IFS"), node("太古里")));

        when(repository.reload(11L)).thenReturn(entity);
        when(codec.readItinerary(entity)).thenReturn(itinerary);

        RouteHistoryRetriever retriever = new RouteHistoryRetriever(repository, codec);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("这条路线赶不赶")
                .itineraryId(11L)
                .cityName("成都")
                .build());

        assertThat(documents).hasSize(1);
        assertThat(documents.get(0).source()).isEqualTo("route-history-current");
        assertThat(documents.get(0).content())
                .contains("option-1")
                .contains("春熙路")
                .contains("IFS")
                .contains("太古里")
                .contains("420")
                .contains("188")
                .contains("拍照和商圈顺路");
    }

    @Test
    void shouldReturnEmptyWhenItineraryIdMissing() {
        RouteHistoryRetriever retriever = new RouteHistoryRetriever(null, null);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("这条路线赶不赶")
                .build());

        assertThat(documents).isEmpty();
    }

    @Test
    void shouldIncludeFavoriteAndRecentRoutesFromUserHistory() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);

        SavedItinerary favorite = new SavedItinerary();
        favorite.setId(21L);
        favorite.setFavorited(1);
        favorite.setShareNote("最喜欢的拍照路线");

        SavedItinerary recent = new SavedItinerary();
        recent.setId(22L);
        recent.setFavorited(0);
        recent.setShareNote("最近逛街路线");

        when(repository.listOwned(7L, true, 2)).thenReturn(List.of(favorite));
        when(repository.listOwned(7L, false, 3)).thenReturn(List.of(favorite, recent));

        ItineraryVO favoriteItinerary = new ItineraryVO();
        favoriteItinerary.setSelectedOptionKey("fav");
        favoriteItinerary.setNodes(List.of(node("太古里"), node("望平街")));

        ItineraryVO recentItinerary = new ItineraryVO();
        recentItinerary.setSelectedOptionKey("recent");
        recentItinerary.setNodes(List.of(node("东郊记忆"), node("建设路")));

        when(codec.readItinerary(favorite)).thenReturn(favoriteItinerary);
        when(codec.readItinerary(recent)).thenReturn(recentItinerary);

        RouteHistoryRetriever retriever = new RouteHistoryRetriever(repository, codec);

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userId(7L)
                .userInput("按我喜欢的路线风格再推荐")
                .cityName("成都")
                .build());

        assertThat(documents).hasSize(2);
        assertThat(documents).extracting(RetrievalDocument::source)
                .containsExactly("route-history-favorite", "route-history-recent");
        assertThat(documents.get(0).content()).contains("favorited=true", "太古里");
        assertThat(documents.get(1).content()).contains("东郊记忆");
    }

    private ItineraryNodeVO node(String name) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(name);
        return node;
    }
}

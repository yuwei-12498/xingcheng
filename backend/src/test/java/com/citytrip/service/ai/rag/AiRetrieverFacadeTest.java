package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiPlatformProperties;
import com.citytrip.service.ai.model.AiScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AiRetrieverFacadeTest {

    @Test
    void shouldMergeDocumentsFromAllEnabledRetrievers() {
        AiRetrieverFacade facade = new AiRetrieverFacade(List.of(
                context -> List.of(new RetrievalDocument("poi", "\u6210\u90fd\u4e07\u8c61\u57ce")),
                context -> List.of(new RetrievalDocument("city-guide", "\u4e07\u8c61\u57ce\u662f\u6210\u90fd\u5546\u5708\u7b80\u79f0"))
        ));

        List<RetrievalDocument> result = facade.retrieve(AiExecutionContext.builder()
                .scene(AiScene.SMART_FILL)
                .userInput("\u6211\u60f3\u53bb\u4e07\u8c61\u57ce")
                .build());

        assertThat(result).hasSize(2);
    }

    @Test
    void shouldRankDeduplicateAndApplyPerSourceQuota() {
        AiPlatformProperties.Retrieval retrieval = new AiPlatformProperties.Retrieval();
        retrieval.setMaxDocuments(3);
        retrieval.setMaxPerSource(1);

        AiRetrieverFacade facade = new AiRetrieverFacade(
                List.of(
                        context -> List.of(
                                new RetrievalDocument("community-post", "title=\u6210\u90fd\u62cd\u7167\u8def\u7ebf | routeSummary=\u6625\u7199\u8def -> \u592a\u53e4\u91cc"),
                                new RetrievalDocument("community-post", "title=\u6210\u90fd\u62cd\u7167\u8def\u7ebf | routeSummary=\u6625\u7199\u8def -> \u592a\u53e4\u91cc")
                        ),
                        context -> List.of(
                                new RetrievalDocument("city-guide", "\u592a\u53e4\u91cc\u591c\u666f\u62cd\u7167\u66f4\u597d"),
                                new RetrievalDocument("route-history", "routePath=\u6625\u7199\u8def -> \u592a\u53e4\u91cc | recommendReason=\u62cd\u7167\u987a\u8def")
                        )
                ),
                new RetrievalRankingService(retrieval)
        );

        List<RetrievalDocument> result = facade.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("\u6210\u90fd\u62cd\u7167\u653b\u7565\u8def\u7ebf")
                .cityName("\u6210\u90fd")
                .build());

        assertThat(result).hasSize(3);
        assertThat(result.get(0).source()).isEqualTo("community-post");
        assertThat(result).extracting(RetrievalDocument::content).doesNotHaveDuplicates();
        assertThat(result.stream().filter(document -> "community-post".equals(document.source())).count()).isEqualTo(1L);
    }

    @Test
    void shouldPreferFavoriteAndLikedRoutesForPersonalizedContext() {
        AiPlatformProperties.Retrieval retrieval = new AiPlatformProperties.Retrieval();
        retrieval.setMaxDocuments(5);
        retrieval.setMaxPerSource(3);

        AiRetrieverFacade facade = new AiRetrieverFacade(
                List.of(context -> List.of(
                        new RetrievalDocument("city-guide", "\u6210\u90fd\u591c\u666f\u62cd\u7167\u66f4\u9002\u5408\u592a\u53e4\u91cc"),
                        new RetrievalDocument("community-post-liked", "title=\u6210\u90fd\u591c\u666f\u8def\u7ebf | routeSummary=\u6625\u7199\u8def -> \u592a\u53e4\u91cc | liked=true"),
                        new RetrievalDocument("route-history-favorite", "routePath=IFS -> \u592a\u53e4\u91cc | favorited=true"),
                        new RetrievalDocument("route-history-disliked", "routePath=\u67d0\u8001\u8def\u7ebf | disliked=true"),
                        new RetrievalDocument("route-history-recent", "routePath=\u4e1c\u90ca\u8bb0\u5fc6 -> \u5efa\u8bbe\u8def")
                )),
                new RetrievalRankingService(retrieval)
        );

        List<RetrievalDocument> result = facade.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userId(66L)
                .userInput("\u6210\u90fd\u62cd\u7167\u8def\u7ebf\u600e\u4e48\u5b89\u6392")
                .cityName("\u6210\u90fd")
                .recentPoiNames(List.of("\u592a\u53e4\u91cc"))
                .build());

        assertThat(result).hasSize(5);
        assertThat(result.get(0).source()).isEqualTo("route-history-favorite");
        assertThat(result.get(1).source()).isEqualTo("community-post-liked");
        assertThat(result.get(result.size() - 1).source()).isEqualTo("route-history-disliked");
    }

    @Test
    void shouldKeepFacadeCallShapeWhenPoiFactsComeFromJsonRepository() {
        AiRetrieverFacade facade = new AiRetrieverFacade(List.of(
                new PoiKnowledgeRetriever(),
                new CityGuideRetriever()
        ));

        List<RetrievalDocument> result = facade.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("\u6210\u90fd\u535a\u7269\u9986\u548c\u592a\u53e4\u91cc\u9002\u5408\u600e\u4e48\u4e32\u8d77\u6765\u901b")
                .cityName("\u6210\u90fd")
                .build());

        assertThat(result).extracting(RetrievalDocument::source)
                .contains("poi-knowledge");
        assertThat(result).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("sourceUrl="))
                .anyMatch(item -> item.contains("\u6210\u90fd\u535a\u7269\u9986"));
    }
}

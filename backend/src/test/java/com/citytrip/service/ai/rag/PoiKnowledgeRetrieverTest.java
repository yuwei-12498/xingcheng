package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiKnowledgeRetrieverTest {

    @Test
    void retrieveShouldLoadMuseumFactsFromJsonResources() {
        PoiKnowledgeRetriever retriever = new PoiKnowledgeRetriever();

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.POI_EXPLANATION)
                .userInput("\u6210\u90fd\u535a\u7269\u9986\u5f00\u653e\u4fe1\u606f\u548c\u53c2\u89c2\u91cd\u70b9\u662f\u4ec0\u4e48")
                .cityName("\u6210\u90fd")
                .build());

        assertThat(documents).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("\u6210\u90fd\u535a\u7269\u9986"))
                .anyMatch(item -> item.contains("sourceUrl=https://www.cdmuseum.com"));
    }

    @Test
    void retrieveShouldLoadFamilyAnimalFactsForPandaBase() {
        PoiKnowledgeRetriever retriever = new PoiKnowledgeRetriever();

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.POI_EXPLANATION)
                .userInput("\u718a\u732b\u57fa\u5730\u4ec0\u4e48\u65f6\u5019\u53bb\u66f4\u5408\u9002")
                .cityName("\u6210\u90fd")
                .build());

        assertThat(documents).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730"))
                .anyMatch(item -> item.contains("panda.org.cn"));
    }

    @Test
    void retrieveShouldReturnTaikooLiAndChunxiRoadFactsFromSplitCategoryFiles() {
        PoiKnowledgeRetriever retriever = new PoiKnowledgeRetriever();

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.OPTION_EXPLANATION)
                .userInput("\u592a\u53e4\u91cc\u548c\u6625\u7199\u8def\u665a\u4e0a\u600e\u4e48\u987a\u8def\u5b89\u6392")
                .cityName("\u6210\u90fd")
                .build());

        assertThat(documents).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("\u592a\u53e4\u91cc"))
                .anyMatch(item -> item.contains("\u6625\u7199\u8def"))
                .anyMatch(item -> item.contains("sourceName="));
    }
}

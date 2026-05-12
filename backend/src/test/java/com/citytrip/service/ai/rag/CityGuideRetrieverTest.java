package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CityGuideRetrieverTest {

    @Test
    void retrieveShouldReturnWanXiangChengAndGuoJinAliasHints() {
        CityGuideRetriever retriever = new CityGuideRetriever();

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("万象城和国金搜不到怎么办")
                .build());

        assertThat(documents).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("万象城"))
                .anyMatch(item -> item.contains("IFS"));
    }

    @Test
    void retrieveShouldReturnNightGuideForChunxiRoadAndTaikooLi() {
        CityGuideRetriever retriever = new CityGuideRetriever();

        List<RetrievalDocument> documents = retriever.retrieve(AiExecutionContext.builder()
                .scene(AiScene.CHAT_QA)
                .userInput("春熙路和太古里夜游怎么安排")
                .build());

        assertThat(documents).extracting(RetrievalDocument::content)
                .anyMatch(item -> item.contains("春熙路"))
                .anyMatch(item -> item.contains("太古里"));
    }
}

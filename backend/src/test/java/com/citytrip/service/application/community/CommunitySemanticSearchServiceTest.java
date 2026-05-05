package com.citytrip.service.application.community;

import com.citytrip.service.impl.vivo.VivoEmbeddingClient;
import com.citytrip.service.impl.vivo.VivoRerankClient;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CommunitySemanticSearchServiceTest {

    @Test
    void shouldUseRerankScoreAsPrimarySignal() {
        CommunitySemanticSearchService service = new CommunitySemanticSearchService(
                new FakeEmbeddingClient(List.of(
                        List.of(1D, 0D),
                        List.of(0.90D, 0.10D),
                        List.of(0.70D, 0.30D)
                )),
                new FakeRerankClient(List.of(0.20D, 0.95D))
        );

        List<CommunitySemanticSearchService.ScoredCommunityCandidate> ranked = service.rank(
                "适合情侣夜游散步",
                List.of(
                        candidate(1L, "A 夜景路线"),
                        candidate(2L, "B 情侣夜游散步")
                )
        );

        assertThat(ranked.get(0).id()).isEqualTo(2L);
    }

    @Test
    void shouldFallbackToEmbeddingWhenRerankFails() {
        CommunitySemanticSearchService service = new CommunitySemanticSearchService(
                new FakeEmbeddingClient(List.of(
                        List.of(1D, 0D),
                        List.of(0.95D, 0.05D),
                        List.of(0.20D, 0.80D)
                )),
                new FailingRerankClient()
        );

        List<CommunitySemanticSearchService.ScoredCommunityCandidate> ranked = service.rank(
                "夜游拍照",
                List.of(
                        candidate(1L, "A 夜游拍照"),
                        candidate(2L, "B 白天博物馆")
                )
        );

        assertThat(ranked.get(0).id()).isEqualTo(1L);
    }

    @Test
    void defaultVivoClientsShouldNotReportSemanticModelReady() {
        CommunitySemanticSearchService service = new CommunitySemanticSearchService(
                new VivoEmbeddingClient(),
                new VivoRerankClient()
        );

        assertThat(service.isEmbeddingReady()).isFalse();
        assertThat(service.isRerankReady()).isFalse();
        assertThat(service.isSemanticModelReady()).isFalse();
    }

    private CommunitySemanticSearchService.CommunitySemanticCandidate candidate(Long id, String text) {
        return new CommunitySemanticSearchService.CommunitySemanticCandidate(id, text);
    }

    private static final class FakeEmbeddingClient extends VivoEmbeddingClient {
        private final List<List<Double>> vectors;

        private FakeEmbeddingClient(List<List<Double>> vectors) {
            this.vectors = vectors;
        }

        @Override
        public List<List<Double>> embed(String modelName, List<String> sentences) {
            return vectors;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    private static final class FakeRerankClient extends VivoRerankClient {
        private final List<Double> scores;

        private FakeRerankClient(List<Double> scores) {
            this.scores = scores;
        }

        @Override
        public List<Double> rerank(String query, List<String> sentences) {
            return scores;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }

    private static final class FailingRerankClient extends VivoRerankClient {
        @Override
        public List<Double> rerank(String query, List<String> sentences) {
            throw new IllegalStateException("rerank down");
        }

        @Override
        public boolean isAvailable() {
            return true;
        }
    }
}

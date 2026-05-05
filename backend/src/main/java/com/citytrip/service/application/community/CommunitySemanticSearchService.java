package com.citytrip.service.application.community;

import com.citytrip.service.impl.vivo.VivoEmbeddingClient;
import com.citytrip.service.impl.vivo.VivoRerankClient;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class CommunitySemanticSearchService {
    private static final String DEFAULT_EMBEDDING_MODEL = "m3e-base";
    private static final int RERANK_LIMIT = 30;

    private final VivoEmbeddingClient embeddingClient;
    private final VivoRerankClient rerankClient;

    public CommunitySemanticSearchService(VivoEmbeddingClient embeddingClient,
                                          VivoRerankClient rerankClient) {
        this.embeddingClient = embeddingClient;
        this.rerankClient = rerankClient;
    }

    public boolean isEmbeddingReady() {
        return embeddingClient != null && embeddingClient.isAvailable();
    }

    public boolean isRerankReady() {
        return rerankClient != null && rerankClient.isAvailable();
    }

    public boolean isSemanticModelReady() {
        return isEmbeddingReady() || isRerankReady();
    }

    public List<ScoredCommunityCandidate> rank(String query, List<CommunitySemanticCandidate> candidates) {
        if (!StringUtils.hasText(query) || candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredCommunityCandidate> coarse = embedAndScore(query, candidates);
        if (coarse.isEmpty()) {
            coarse = lexicalRank(query, candidates);
        }
        List<ScoredCommunityCandidate> topN = coarse.stream().limit(RERANK_LIMIT).toList();
        try {
            return rerankAndMerge(query, topN, candidates);
        } catch (Exception ex) {
            return topN;
        }
    }

    private List<ScoredCommunityCandidate> embedAndScore(String query, List<CommunitySemanticCandidate> candidates) {
        if (!isEmbeddingReady()) {
            return List.of();
        }
        List<String> texts = new ArrayList<>();
        texts.add(query);
        for (CommunitySemanticCandidate candidate : candidates) {
            texts.add(candidate == null ? "" : safeText(candidate.text()));
        }
        List<List<Double>> vectors = embeddingClient.embed(DEFAULT_EMBEDDING_MODEL, texts);
        if (vectors == null || vectors.size() != texts.size()) {
            return List.of();
        }
        List<Double> queryVector = vectors.get(0);
        if (!isUsableVector(queryVector)) {
            return List.of();
        }
        List<ScoredCommunityCandidate> scored = new ArrayList<>();
        for (int index = 0; index < candidates.size(); index++) {
            CommunitySemanticCandidate candidate = candidates.get(index);
            if (candidate == null || candidate.id() == null) {
                continue;
            }
            List<Double> candidateVector = vectors.get(index + 1);
            if (!isUsableVector(candidateVector)) {
                continue;
            }
            double cosine = cosine(queryVector, candidateVector);
            scored.add(new ScoredCommunityCandidate(candidate.id(), normalizeCosine(cosine)));
        }
        scored.sort(Comparator.comparing(ScoredCommunityCandidate::score).reversed());
        return scored;
    }

    private List<ScoredCommunityCandidate> rerankAndMerge(String query,
                                                          List<ScoredCommunityCandidate> coarse,
                                                          List<CommunitySemanticCandidate> candidates) {
        if (!isRerankReady() || coarse.isEmpty()) {
            return coarse;
        }
        List<String> texts = new ArrayList<>();
        for (ScoredCommunityCandidate item : coarse) {
            texts.add(findText(item.id(), candidates));
        }
        List<Double> rerankScores = rerankClient.rerank(query, texts);
        if (rerankScores == null || rerankScores.size() != coarse.size()) {
            return coarse;
        }
        List<ScoredCommunityCandidate> merged = new ArrayList<>();
        for (int index = 0; index < coarse.size(); index++) {
            ScoredCommunityCandidate coarseItem = coarse.get(index);
            double rerankScore = rerankScores.get(index) == null ? 0D : rerankScores.get(index);
            merged.add(new ScoredCommunityCandidate(
                    coarseItem.id(),
                    finalScore(rerankScore, coarseItem.score())
            ));
        }
        merged.sort(Comparator.comparing(ScoredCommunityCandidate::score).reversed());
        return merged;
    }

    private List<ScoredCommunityCandidate> lexicalRank(String query, List<CommunitySemanticCandidate> candidates) {
        List<ScoredCommunityCandidate> scored = new ArrayList<>();
        for (CommunitySemanticCandidate candidate : candidates) {
            if (candidate == null || candidate.id() == null) {
                continue;
            }
            scored.add(new ScoredCommunityCandidate(candidate.id(), lexicalScore(query, candidate.text())));
        }
        scored.sort(Comparator.comparing(ScoredCommunityCandidate::score).reversed());
        return scored;
    }

    private double lexicalScore(String query, String text) {
        String normalizedQuery = safeText(query).toLowerCase(Locale.ROOT);
        String normalizedText = safeText(text).toLowerCase(Locale.ROOT);
        if (normalizedQuery.isEmpty() || normalizedText.isEmpty()) {
            return 0D;
        }
        double score = normalizedText.contains(normalizedQuery) ? 1D : 0D;
        Set<String> queryTokens = uniqueChars(normalizedQuery);
        Set<String> textTokens = uniqueChars(normalizedText);
        if (!queryTokens.isEmpty()) {
            long shared = queryTokens.stream().filter(textTokens::contains).count();
            score += (double) shared / (double) queryTokens.size();
        }
        return score;
    }

    private Set<String> uniqueChars(String value) {
        Set<String> tokens = new LinkedHashSet<>();
        for (char item : value.toCharArray()) {
            if (!Character.isWhitespace(item)) {
                tokens.add(String.valueOf(item));
            }
        }
        return tokens;
    }

    private String findText(Long id, List<CommunitySemanticCandidate> candidates) {
        for (CommunitySemanticCandidate candidate : candidates) {
            if (candidate != null && id != null && id.equals(candidate.id())) {
                return safeText(candidate.text());
            }
        }
        return "";
    }

    private boolean isUsableVector(List<Double> vector) {
        return vector != null && !vector.isEmpty();
    }

    private double cosine(List<Double> left, List<Double> right) {
        int length = Math.min(left.size(), right.size());
        if (length == 0) {
            return 0D;
        }
        double dot = 0D;
        double leftNorm = 0D;
        double rightNorm = 0D;
        for (int index = 0; index < length; index++) {
            double l = left.get(index) == null ? 0D : left.get(index);
            double r = right.get(index) == null ? 0D : right.get(index);
            dot += l * r;
            leftNorm += l * l;
            rightNorm += r * r;
        }
        if (leftNorm <= 0D || rightNorm <= 0D) {
            return 0D;
        }
        return dot / (Math.sqrt(leftNorm) * Math.sqrt(rightNorm));
    }

    private double normalizeCosine(double cosine) {
        double normalized = (cosine + 1D) / 2D;
        if (normalized < 0D) {
            return 0D;
        }
        if (normalized > 1D) {
            return 1D;
        }
        return normalized;
    }

    private double finalScore(double rerankScore, double cosineScoreNormalized) {
        return rerankScore * 0.75D + cosineScoreNormalized * 0.25D;
    }

    private String safeText(String value) {
        return value == null ? "" : value.trim();
    }

    public record CommunitySemanticCandidate(Long id, String text) {
    }

    public record ScoredCommunityCandidate(Long id, double score) {
    }
}

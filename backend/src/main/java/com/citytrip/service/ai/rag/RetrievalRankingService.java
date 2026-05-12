package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiPlatformProperties;
import com.citytrip.service.ai.runtime.ChatAugmentationIntent;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

public class RetrievalRankingService {
    private final AiPlatformProperties.Retrieval retrievalProperties;

    public RetrievalRankingService() {
        this(new AiPlatformProperties.Retrieval());
    }

    public RetrievalRankingService(AiPlatformProperties.Retrieval retrievalProperties) {
        this.retrievalProperties = retrievalProperties == null ? new AiPlatformProperties.Retrieval() : retrievalProperties;
    }

    public List<RetrievalDocument> rank(AiExecutionContext context, List<RetrievalDocument> documents) {
        if (documents == null || documents.isEmpty()) {
            return List.of();
        }
        ChatAugmentationIntent intent = resolveIntent(context);
        List<RankedDocument> ranked = new ArrayList<>();
        for (RetrievalDocument document : documents) {
            if (document == null || !StringUtils.hasText(document.content())) {
                continue;
            }
            ranked.add(new RankedDocument(document, score(context, intent, document)));
        }
        ranked.sort(Comparator.comparing(RankedDocument::score).reversed());

        Set<String> seenContent = new LinkedHashSet<>();
        Map<String, Integer> sourceCounts = new LinkedHashMap<>();
        List<RetrievalDocument> result = new ArrayList<>();
        int maxDocuments = Math.max(1, retrievalProperties.getMaxDocuments());
        int maxPerSource = Math.max(1, retrievalProperties.getMaxPerSource());

        for (RankedDocument item : ranked) {
            RetrievalDocument document = item.document();
            String normalizedContent = normalize(document.content());
            if (!seenContent.add(normalizedContent)) {
                continue;
            }
            String source = StringUtils.hasText(document.source()) ? document.source().trim() : "unknown";
            int used = sourceCounts.getOrDefault(source, 0);
            if (used >= maxPerSource) {
                continue;
            }
            sourceCounts.put(source, used + 1);
            result.add(document);
            if (result.size() >= maxDocuments) {
                break;
            }
        }
        return List.copyOf(result);
    }

    private double score(AiExecutionContext context,
                         ChatAugmentationIntent intent,
                         RetrievalDocument document) {
        double score = sourcePriority(intent, document.source());
        score += preferenceAdjustment(document);
        score += overlapScore(context == null ? null : context.getUserInput(), document.content());
        score += overlapScore(join(context == null ? null : context.getRecentMessages()), document.content()) * 0.35D;
        score += poiAffinityScore(context == null ? null : context.getRecentPoiNames(), document.content());
        if (context != null && StringUtils.hasText(context.getCityName()) && document.content().contains(context.getCityName())) {
            score += 8D;
        }
        if (intent == ChatAugmentationIntent.ROUTE_VERIFY
                && StringUtils.hasText(context == null ? null : context.getRouteSummary())
                && document.content().contains(context.getRouteSummary())) {
            score += 12D;
        }
        return score;
    }

    private double sourcePriority(ChatAugmentationIntent intent, String source) {
        String normalized = StringUtils.hasText(source) ? source.trim().toLowerCase(Locale.ROOT) : "unknown";
        if (normalized.contains("disliked")) {
            return 5D;
        }
        if (intent == ChatAugmentationIntent.COMMUNITY_GUIDE) {
            if (normalized.contains("favorite")) {
                return 145D;
            }
            if (normalized.contains("liked")) {
                return 132D;
            }
            if (normalized.contains("community")) {
                return 120D;
            }
            if (normalized.contains("city-guide")) {
                return 70D;
            }
        }
        if (intent == ChatAugmentationIntent.ROUTE_VERIFY) {
            if (normalized.contains("favorite")) {
                return 150D;
            }
            if (normalized.contains("current")) {
                return 132D;
            }
            if (normalized.contains("route-history")) {
                return 120D;
            }
            if (normalized.contains("liked")) {
                return 88D;
            }
            if (normalized.contains("community")) {
                return 60D;
            }
        }
        if (normalized.contains("favorite")) {
            return 118D;
        }
        if (normalized.contains("liked")) {
            return 108D;
        }
        if (normalized.contains("recent")) {
            return 92D;
        }
        if (normalized.contains("community")) {
            return 90D;
        }
        if (normalized.contains("route-history")) {
            return 85D;
        }
        if (normalized.contains("poi")) {
            return 75D;
        }
        if (normalized.contains("city-guide")) {
            return 70D;
        }
        return 50D;
    }

    private double overlapScore(String query, String content) {
        String normalizedQuery = normalize(query);
        String normalizedContent = normalize(content);
        if (!StringUtils.hasText(normalizedQuery) || !StringUtils.hasText(normalizedContent)) {
            return 0D;
        }
        double score = normalizedContent.contains(normalizedQuery) ? 25D : 0D;
        Set<String> queryChars = uniqueChars(normalizedQuery);
        Set<String> contentChars = uniqueChars(normalizedContent);
        if (!queryChars.isEmpty()) {
            long shared = queryChars.stream().filter(contentChars::contains).count();
            score += ((double) shared / (double) queryChars.size()) * 30D;
        }
        return score;
    }

    private double preferenceAdjustment(RetrievalDocument document) {
        if (document == null) {
            return 0D;
        }
        String source = normalize(document.source());
        String content = normalize(document.content());
        if (source.contains("disliked") || content.contains("dislikedtrue") || content.contains("preferencedisliked")) {
            return -120D;
        }
        double score = 0D;
        if (source.contains("favorite") || content.contains("favoritedtrue") || content.contains("preferencefavorite")) {
            score += 38D;
        }
        if (source.contains("liked") || content.contains("likedtrue") || content.contains("preferenceliked")) {
            score += 24D;
        }
        if (source.contains("recent")) {
            score += 10D;
        }
        return score;
    }

    private double poiAffinityScore(List<String> recentPoiNames, String content) {
        if (recentPoiNames == null || recentPoiNames.isEmpty() || !StringUtils.hasText(content)) {
            return 0D;
        }
        double score = 0D;
        for (String recentPoiName : recentPoiNames) {
            if (!StringUtils.hasText(recentPoiName)) {
                continue;
            }
            if (content.contains(recentPoiName.trim())) {
                score += 6D;
            }
        }
        return Math.min(score, 18D);
    }

    private String join(List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        return values.stream()
                .filter(StringUtils::hasText)
                .map(String::trim)
                .reduce((left, right) -> left + " " + right)
                .orElse("");
    }

    private ChatAugmentationIntent resolveIntent(AiExecutionContext context) {
        String question = context == null ? "" : safe(context.getUserInput());
        if (question.contains("附近") || question.contains("周边") || question.contains("旁边")) {
            return ChatAugmentationIntent.NEARBY_DISCOVERY;
        }
        if (question.contains("社区") || question.contains("帖子") || question.contains("攻略")) {
            return ChatAugmentationIntent.COMMUNITY_GUIDE;
        }
        if (question.contains("多久") || question.contains("怎么走") || question.contains("顺路")
                || question.contains("交通") || StringUtils.hasText(context == null ? null : context.getRouteSummary())) {
            return ChatAugmentationIntent.ROUTE_VERIFY;
        }
        return ChatAugmentationIntent.GENERAL_QA;
    }

    private Set<String> uniqueChars(String value) {
        Set<String> chars = new LinkedHashSet<>();
        for (char item : value.toCharArray()) {
            if (!Character.isWhitespace(item)) {
                chars.add(String.valueOf(item));
            }
        }
        return chars;
    }

    private String normalize(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？；:：、|\\-_=+()（）\\[\\]{}<>]+", "");
    }

    private String safe(String value) {
        return StringUtils.hasText(value) ? value.trim() : "";
    }

    private record RankedDocument(RetrievalDocument document, double score) {
    }
}

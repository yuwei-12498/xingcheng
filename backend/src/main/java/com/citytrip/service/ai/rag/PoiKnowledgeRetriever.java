package com.citytrip.service.ai.rag;

import com.citytrip.service.ai.model.AiExecutionContext;
import org.springframework.util.StringUtils;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

public class PoiKnowledgeRetriever implements ContextRetriever {
    private final PoiFactRepository repository;

    public PoiKnowledgeRetriever() {
        this(new PoiFactRepository());
    }

    public PoiKnowledgeRetriever(PoiFactRepository repository) {
        this.repository = repository == null ? new PoiFactRepository() : repository;
    }

    @Override
    public List<RetrievalDocument> retrieve(AiExecutionContext context) {
        if (context == null || !StringUtils.hasText(context.getUserInput())) {
            return List.of();
        }
        String normalizedInput = normalize(context.getUserInput());
        return repository.loadAll().stream()
                .filter(fact -> matchesPoiOrAlias(normalizedInput, fact))
                .sorted(Comparator.comparingDouble((PoiFactRecord fact) -> scoreFact(context, normalizedInput, fact)).reversed())
                .limit(4)
                .map(this::toDocument)
                .toList();
    }

    private RetrievalDocument toDocument(PoiFactRecord fact) {
        return new RetrievalDocument(
                "poi-knowledge",
                "category=" + safe(fact.categoryFile())
                        + " | poi=" + safe(fact.poiName())
                        + " | factType=" + safe(fact.factType())
                        + " | content=" + safe(fact.content())
                        + " | sourceName=" + safe(fact.sourceName())
                        + " | sourceUrl=" + safe(fact.sourceUrl())
                        + " | updatedAt=" + safe(fact.updatedAt())
        );
    }

    private boolean matchesPoiOrAlias(String normalizedInput, PoiFactRecord fact) {
        if (!StringUtils.hasText(normalizedInput) || fact == null) {
            return false;
        }
        if (containsNormalized(normalizedInput, fact.poiName())) {
            return true;
        }
        for (String alias : fact.aliases()) {
            if (containsNormalized(normalizedInput, alias)) {
                return true;
            }
        }
        return false;
    }

    private double scoreFact(AiExecutionContext context, String normalizedInput, PoiFactRecord fact) {
        double score = fact == null ? 0D : fact.weight();
        if (fact == null) {
            return score;
        }
        if (containsNormalized(normalizedInput, fact.poiName())) {
            score += 5D;
        }
        for (String alias : fact.aliases()) {
            if (containsNormalized(normalizedInput, alias)) {
                score += 3D;
            }
        }
        if (StringUtils.hasText(context.getCityName()) && context.getCityName().equalsIgnoreCase(safe(fact.city()))) {
            score += 1.2D;
        }
        for (String tag : fact.tags()) {
            if (containsNormalized(normalizedInput, tag)) {
                score += 0.5D;
            }
        }
        if (context.getRecentPoiNames() != null) {
            for (String recentPoiName : context.getRecentPoiNames()) {
                if (containsNormalized(normalize(recentPoiName), fact.poiName())) {
                    score += 0.8D;
                    break;
                }
            }
        }
        if (StringUtils.hasText(fact.factType()) && containsNormalized(normalizedInput, fact.factType())) {
            score += 0.3D;
        }
        return score;
    }

    private boolean containsNormalized(String normalizedInput, String raw) {
        return StringUtils.hasText(normalizedInput)
                && StringUtils.hasText(raw)
                && normalizedInput.contains(normalize(raw));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static String normalize(String text) {
        if (!StringUtils.hasText(text)) {
            return "";
        }
        return text.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,\uFF0C\u3002\uFF01\uFF1F\uFF1B;:\uFF1A\u201C\u201D\"'\u2018\u2019\uFF08\uFF09()\\[\\]{}<>\u300A\u300B\u00B7~`_-]+", "");
    }
}

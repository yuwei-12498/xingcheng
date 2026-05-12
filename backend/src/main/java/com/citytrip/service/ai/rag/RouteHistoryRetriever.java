package com.citytrip.service.ai.rag;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class RouteHistoryRetriever implements ContextRetriever {
    private static final int FAVORITE_LIMIT = 2;
    private static final int RECENT_LIMIT = 3;

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;

    public RouteHistoryRetriever() {
        this(null, null);
    }

    public RouteHistoryRetriever(SavedItineraryRepository savedItineraryRepository,
                                 SavedItineraryCodec savedItineraryCodec) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
    }

    @Override
    public List<RetrievalDocument> retrieve(AiExecutionContext context) {
        if (savedItineraryRepository == null
                || savedItineraryCodec == null
                || context == null) {
            return List.of();
        }
        java.util.LinkedHashMap<Long, RetrievalDocument> documents = new java.util.LinkedHashMap<>();
        try {
            if (context.getItineraryId() != null) {
                SavedItinerary entity = savedItineraryRepository.reload(context.getItineraryId());
                addDocument(documents, entity, resolveCurrentSource(entity));
            }
            if (context.getUserId() != null) {
                for (SavedItinerary favorite : savedItineraryRepository.listOwned(context.getUserId(), true, FAVORITE_LIMIT)) {
                    addDocument(documents, favorite, "route-history-favorite");
                }
                for (SavedItinerary recent : savedItineraryRepository.listOwned(context.getUserId(), false, RECENT_LIMIT)) {
                    addDocument(documents, recent, "route-history-recent");
                }
            }
            return documents.isEmpty() ? List.of() : List.copyOf(documents.values());
        } catch (Exception ex) {
            return List.of();
        }
    }

    private void addDocument(java.util.LinkedHashMap<Long, RetrievalDocument> documents,
                             SavedItinerary entity,
                             String source) throws Exception {
        if (entity == null) {
            return;
        }
        Long key = entity.getId();
        if (key != null && documents.containsKey(key)) {
            return;
        }
        ItineraryVO itinerary = savedItineraryCodec.readItinerary(entity);
        String summary = summarize(entity, itinerary);
        if (!StringUtils.hasText(summary)) {
            return;
        }
        RetrievalDocument document = new RetrievalDocument(source, summary);
        if (key == null) {
            documents.put(-(long) documents.size() - 1L, document);
            return;
        }
        documents.put(key, document);
    }

    private String resolveCurrentSource(SavedItinerary entity) {
        return entity != null && Integer.valueOf(1).equals(entity.getFavorited())
                ? "route-history-current-favorite"
                : "route-history-current";
    }

    private String summarize(SavedItinerary entity, ItineraryVO itinerary) {
        List<String> parts = new ArrayList<>();
        if (itinerary != null) {
            append(parts, "selectedOption", itinerary.getSelectedOptionKey());
            append(parts, "recommendReason", itinerary.getRecommendReason());
            if (itinerary.getTotalDuration() != null) {
                parts.add("totalDuration=" + itinerary.getTotalDuration());
            }
            if (itinerary.getTotalCost() != null) {
                parts.add("totalCost=" + itinerary.getTotalCost().stripTrailingZeros().toPlainString());
            }
            String routePath = buildRoutePath(itinerary.getNodes());
            append(parts, "routePath", routePath);
        }
        if (entity != null) {
            append(parts, "shareNote", entity.getShareNote());
            if (Integer.valueOf(1).equals(entity.getFavorited())) {
                parts.add("favorited=true");
            }
        }
        return String.join(" | ", parts);
    }

    private String buildRoutePath(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return "";
        }
        return nodes.stream()
                .filter(node -> node != null && StringUtils.hasText(node.getPoiName()))
                .limit(6)
                .map(node -> node.getPoiName().trim())
                .collect(Collectors.joining(" -> "));
    }

    private void append(List<String> parts, String label, String value) {
        if (StringUtils.hasText(value)) {
            parts.add(label + "=" + value.trim());
        }
    }
}

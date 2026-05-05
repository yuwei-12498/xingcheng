package com.citytrip.service.domain.ai;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatPoiSkillService {

    public static final int POI_CANDIDATE_LIMIT = 200;
    public static final int POI_CONTEXT_LIMIT = 8;

    private final PoiMapper poiMapper;

    public ChatPoiSkillService(PoiMapper poiMapper) {
        this.poiMapper = poiMapper;
    }

    public List<Poi> loadRelevantPois(ChatReqDTO req) {
        if (poiMapper == null) {
            return List.of();
        }
        boolean rainy = isRainy(req);
        List<Poi> candidates;
        try {
            String cityCode = req == null || req.getContext() == null ? null : req.getContext().getCityCode();
            String cityName = req == null || req.getContext() == null ? null : req.getContext().getCityName();
            if (!StringUtils.hasText(cityCode) && !StringUtils.hasText(cityName)) {
                candidates = poiMapper.selectPlanningCandidates(rainy, null, POI_CANDIDATE_LIMIT);
            } else {
                candidates = poiMapper.selectPlanningCandidates(rainy, null, cityCode, cityName, POI_CANDIDATE_LIMIT);
            }
        } catch (Exception ex) {
            return List.of();
        }
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }

        List<ScoredPoi> scored = new ArrayList<>();
        for (Poi poi : candidates) {
            if (poi == null) {
                continue;
            }
            scored.add(new ScoredPoi(poi, scorePoi(poi, req)));
        }

        scored.sort(Comparator
                .comparingDouble(ScoredPoi::score).reversed()
                .thenComparing((ScoredPoi item) -> priority(item.poi()), Comparator.reverseOrder())
                .thenComparing(item -> item.poi().getId(), Comparator.nullsLast(Comparator.naturalOrder())));

        int limit = Math.min(scored.size(), POI_CONTEXT_LIMIT);
        List<Poi> selected = new ArrayList<>(limit);
        for (int i = 0; i < limit; i++) {
            selected.add(scored.get(i).poi());
        }
        return selected;
    }

    private double scorePoi(Poi poi, ChatReqDTO req) {
        String question = normalize(req == null ? null : req.getQuestion());
        String blob = buildPoiBlob(poi);
        Set<String> keywords = extractKeywords(question);
        List<String> preferences = extractPreferences(req);
        String companionType = normalize(req == null || req.getContext() == null ? null : req.getContext().getCompanionType());
        boolean rainy = isRainy(req);
        boolean nightMode = isNightMode(req);

        double score = priority(poi) * 10.0D;
        if (contains(question, poi.getName())) {
            score += 120.0D;
        }
        if (contains(question, poi.getCategory())) {
            score += 28.0D;
        }
        if (contains(question, poi.getDistrict())) {
            score += 24.0D;
        }

        for (String keyword : keywords) {
            if (blob.contains(keyword)) {
                score += 9.0D;
            }
        }

        for (String preference : preferences) {
            if (blob.contains(preference)) {
                score += 18.0D;
            }
        }

        if (StringUtils.hasText(companionType) && containsValue(poi.getSuitableFor(), companionType)) {
            score += 16.0D;
        }

        if (rainy) {
            if (flag(poi.getIndoor()) || flag(poi.getRainFriendly())) {
                score += 16.0D;
            } else {
                score -= 12.0D;
            }
        }
        if (nightMode) {
            if (flag(poi.getNightAvailable())) {
                score += 14.0D;
            } else {
                score -= 8.0D;
            }
        }

        if (flag(poi.getTemporarilyClosed())) {
            score -= 200.0D;
        }
        return score;
    }

    private boolean isRainy(ChatReqDTO req) {
        return req != null && req.getContext() != null && Boolean.TRUE.equals(req.getContext().getRainy());
    }

    private boolean isNightMode(ChatReqDTO req) {
        return req != null && req.getContext() != null && Boolean.TRUE.equals(req.getContext().getNightMode());
    }

    private List<String> extractPreferences(ChatReqDTO req) {
        if (req == null || req.getContext() == null || req.getContext().getPreferences() == null) {
            return List.of();
        }
        List<String> normalized = new ArrayList<>();
        for (String preference : req.getContext().getPreferences()) {
            String value = normalize(preference);
            if (value.length() >= 2) {
                normalized.add(value);
            }
        }
        return normalized;
    }

    private Set<String> extractKeywords(String text) {
        if (!StringUtils.hasText(text)) {
            return Set.of();
        }
        String[] parts = text.split("[\\s,，。！？!?:：；、/\\\\|（）()\\[\\]{}\"'`]+");
        Set<String> keywords = new LinkedHashSet<>();
        for (String part : parts) {
            if (part != null) {
                String value = normalize(part);
                if (value.length() >= 2 && value.length() <= 12) {
                    keywords.add(value);
                }
            }
        }
        return keywords;
    }

    private String buildPoiBlob(Poi poi) {
        return String.join("|",
                normalize(poi.getName()),
                normalize(poi.getCategory()),
                normalize(poi.getDistrict()),
                normalize(poi.getTags()),
                normalize(poi.getSuitableFor()),
                normalize(poi.getDescription())
        );
    }

    private boolean contains(String source, String value) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(value)) {
            return false;
        }
        return source.contains(normalize(value));
    }

    private boolean containsValue(String source, String value) {
        if (!StringUtils.hasText(source) || !StringUtils.hasText(value)) {
            return false;
        }
        String normalizedSource = normalize(source).replace('，', ',');
        String normalizedValue = normalize(value);
        if (normalizedSource.contains(normalizedValue)) {
            return true;
        }
        String[] parts = normalizedSource.split(",");
        for (String part : parts) {
            if (normalizedValue.equals(part.trim())) {
                return true;
            }
        }
        return false;
    }

    private String normalize(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private boolean flag(Integer value) {
        return value != null && value > 0;
    }

    private double priority(Poi poi) {
        if (poi == null) {
            return 0.0D;
        }
        BigDecimal priorityScore = poi.getPriorityScore();
        return priorityScore == null ? 0.0D : priorityScore.doubleValue();
    }

    private record ScoredPoi(Poi poi, double score) {
    }
}

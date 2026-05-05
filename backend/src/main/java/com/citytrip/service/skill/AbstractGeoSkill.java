package com.citytrip.service.skill;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.vo.ChatSkillPayloadVO;
import com.citytrip.model.vo.PoiSearchResultVO;
import com.citytrip.service.PoiService;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

abstract class AbstractGeoSkill implements ChatSkillHandler {

    protected static final String DEFAULT_CITY = "成都";
    protected static final String GEO_SOURCE = "vivo-geo";
    private static final String[] ANCHOR_MARKERS = {"附近", "周边", "旁边"};
    private static final String[] SEARCH_PREFIXES = {
            "帮我搜索一下", "帮我搜一下", "帮我搜索", "帮我找一下", "帮我找",
            "搜索一下", "搜一下", "推荐一下", "请问一下", "我想找",
            "推荐", "帮我", "请问", "搜索", "搜", "找"
    };
    private static final String[] ANCHOR_PREFIXES = {
            "推荐一下", "请问一下", "帮我找一下", "帮我找", "推荐", "帮我", "请问", "我在", "在"
    };
    private static final String[] TRAILING_NOISE = {"一下"};

    protected String questionOf(ChatReqDTO req) {
        return req == null || !StringUtils.hasText(req.getQuestion()) ? "" : req.getQuestion().trim();
    }

    protected String cityOf(ChatReqDTO req) {
        return req == null || req.getContext() == null || !StringUtils.hasText(req.getContext().getCityName())
                ? DEFAULT_CITY
                : req.getContext().getCityName().trim();
    }

    protected boolean containsAny(String text, String... words) {
        if (!StringUtils.hasText(text) || words == null || words.length == 0) {
            return false;
        }
        for (String word : words) {
            if (StringUtils.hasText(word) && text.contains(word)) {
                return true;
            }
        }
        return false;
    }

    protected String extractAnchorKeyword(String question) {
        String normalized = trimToNull(question);
        if (normalized == null) {
            return null;
        }
        String subject = normalized;
        for (String marker : ANCHOR_MARKERS) {
            int index = subject.indexOf(marker);
            if (index >= 0) {
                subject = subject.substring(0, index);
                break;
            }
        }
        subject = stripLeading(subject, SEARCH_PREFIXES);
        subject = stripLeading(subject, ANCHOR_PREFIXES);
        subject = stripTrailing(subject, TRAILING_NOISE);
        return trimToNull(subject);
    }

    protected String extractPoiKeyword(String question) {
        String keyword = stripLeading(question, SEARCH_PREFIXES);
        keyword = stripTrailing(keyword, TRAILING_NOISE);
        return trimToNull(keyword);
    }

    protected AnchorResolution resolveAnchor(String anchor,
                                             String city,
                                             PoiService poiService,
                                             PlaceDisambiguationService placeDisambiguationService) {
        String normalizedAnchor = trimToNull(anchor);
        if (normalizedAnchor == null || placeDisambiguationService == null) {
            return AnchorResolution.unresolved(null);
        }

        PlaceDisambiguationService.PlaceResolution resolution = placeDisambiguationService.disambiguate(normalizedAnchor, city, null);
        if (resolution != null && resolution.best() != null && !resolution.clarificationRequired()) {
            GeoPoint point = toValidPoint(resolution.best().latitude(), resolution.best().longitude());
            if (point != null) {
                return AnchorResolution.resolved(point, normalizedAnchor, resolution.best().source());
            }
            return AnchorResolution.unresolved(normalizedAnchor);
        }
        if (resolution != null
                && resolution.candidates() != null
                && !resolution.candidates().isEmpty()
                && resolution.clarificationRequired()) {
            return AnchorResolution.clarification(normalizedAnchor,
                    trimToNull(resolution.clarificationQuestion()),
                    firstNonBlank(bestSourceOf(resolution), GEO_SOURCE));
        }
        if (poiService == null) {
            return AnchorResolution.unresolved(normalizedAnchor);
        }
        GeoPoint point = poiService.searchLive(normalizedAnchor, city, 1).stream()
                .map(item -> toValidPoint(item.latitude(), item.longitude()))
                .filter(candidate -> candidate != null)
                .findFirst()
                .orElse(null);
        return point == null
                ? AnchorResolution.unresolved(normalizedAnchor)
                : AnchorResolution.resolved(point, normalizedAnchor, null);
    }

    protected ChatSkillPayloadVO buildClarificationPayload(String skillName,
                                                           String intent,
                                                           String city,
                                                           String anchor,
                                                           String category,
                                                           int limit,
                                                           int radiusMeters,
                                                           String source,
                                                           String clarificationQuestion) {
        ChatSkillPayloadVO payload = buildPayload(skillName, intent, city, anchor, category, limit, radiusMeters,
                List.of(), firstNonBlank(source, GEO_SOURCE), clarificationQuestion);
        payload.setStatus("clarification_required");
        return payload;
    }

    protected List<ChatSkillPayloadVO.ResultItem> fromGeoCandidates(List<GeoPoiCandidate> candidates) {
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>(candidates.size());
        for (GeoPoiCandidate candidate : candidates) {
            if (candidate == null) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(candidate.getName());
            item.setAddress(candidate.getAddress());
            item.setCategory(candidate.getCategory());
            item.setLatitude(candidate.getLatitude());
            item.setLongitude(candidate.getLongitude());
            item.setCityName(candidate.getCityName());
            item.setSource(candidate.getSource());
            item.setDistanceMeters(candidate.getDistanceMeters());
            items.add(item);
        }
        return items;
    }

    protected List<ChatSkillPayloadVO.ResultItem> fromPoiResults(List<PoiSearchResultVO> results) {
        if (results == null || results.isEmpty()) {
            return List.of();
        }
        List<ChatSkillPayloadVO.ResultItem> items = new ArrayList<>(results.size());
        for (PoiSearchResultVO result : results) {
            if (result == null) {
                continue;
            }
            ChatSkillPayloadVO.ResultItem item = new ChatSkillPayloadVO.ResultItem();
            item.setName(result.name());
            item.setAddress(result.address());
            item.setCategory(result.category());
            item.setLatitude(result.latitude());
            item.setLongitude(result.longitude());
            item.setCityName(result.cityName());
            item.setSource(result.source());
            items.add(item);
        }
        return items;
    }

    protected ChatSkillPayloadVO buildPayload(String skillName,
                                              String intent,
                                              String city,
                                              String anchor,
                                              String category,
                                              int limit,
                                              int radiusMeters,
                                              List<ChatSkillPayloadVO.ResultItem> items,
                                              String source,
                                              String fallbackMessage) {
        ChatSkillPayloadVO payload = new ChatSkillPayloadVO();
        payload.setSkillName(skillName);
        payload.setIntent(intent);
        payload.setStatus(items == null || items.isEmpty() ? "empty" : "ok");
        payload.setCity(city);
        payload.setSource(source);
        payload.setResults(items == null ? List.of() : items);
        payload.getQuery().setAnchor(anchor);
        payload.getQuery().setCategory(category);
        payload.getQuery().setLimit(limit);
        payload.getQuery().setRadiusMeters(radiusMeters);
        payload.setFallbackMessage(fallbackMessage);
        payload.setEvidence(List.of("source=" + source, "intent=" + intent));
        return payload;
    }

    protected String trimToNull(String text) {
        return StringUtils.hasText(text) ? text.trim() : null;
    }

    private String stripLeading(String text, String... prefixes) {
        String value = trimToNull(text);
        if (value == null || prefixes == null) {
            return value;
        }
        boolean changed;
        do {
            changed = false;
            for (String prefix : prefixes) {
                if (StringUtils.hasText(prefix) && value.startsWith(prefix)) {
                    value = trimToNull(value.substring(prefix.length()));
                    changed = true;
                    break;
                }
            }
        } while (changed && value != null);
        return value;
    }

    private String stripTrailing(String text, String... suffixes) {
        String value = trimToNull(text);
        if (value == null || suffixes == null) {
            return value;
        }
        boolean changed;
        do {
            changed = false;
            for (String suffix : suffixes) {
                if (StringUtils.hasText(suffix) && value.endsWith(suffix)) {
                    value = trimToNull(value.substring(0, value.length() - suffix.length()));
                    changed = true;
                    break;
                }
            }
        } while (changed && value != null);
        return value;
    }

    private GeoPoint toValidPoint(BigDecimal latitude, BigDecimal longitude) {
        if (latitude == null || longitude == null) {
            return null;
        }
        GeoPoint point = new GeoPoint(latitude, longitude);
        return point.valid() ? point : null;
    }

    private String bestSourceOf(PlaceDisambiguationService.PlaceResolution resolution) {
        if (resolution == null) {
            return null;
        }
        if (resolution.best() != null && StringUtils.hasText(resolution.best().source())) {
            return resolution.best().source();
        }
        if (resolution.candidates() == null) {
            return null;
        }
        return resolution.candidates().stream()
                .map(PlaceDisambiguationService.ResolvedPlace::source)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
    }

    private String firstNonBlank(String first, String fallback) {
        return StringUtils.hasText(first) ? first : fallback;
    }

    protected record AnchorResolution(GeoPoint center,
                                      String anchor,
                                      String clarificationQuestion,
                                      boolean clarificationRequired,
                                      String source) {
        static AnchorResolution resolved(GeoPoint center, String anchor, String source) {
            return new AnchorResolution(center, anchor, null, false, source);
        }

        static AnchorResolution clarification(String anchor, String clarificationQuestion, String source) {
            return new AnchorResolution(null, anchor, clarificationQuestion, true, source);
        }

        static AnchorResolution unresolved(String anchor) {
            return new AnchorResolution(null, anchor, null, false, null);
        }
    }
}
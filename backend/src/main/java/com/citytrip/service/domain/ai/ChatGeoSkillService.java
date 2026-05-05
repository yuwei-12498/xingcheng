package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import com.citytrip.service.geo.PlaceDisambiguationService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ChatGeoSkillService {

    private static final Set<String> GEO_INTENT_KEYWORDS = Set.of(
            "附近", "多远", "在哪", "怎么去", "路线", "距离", "地铁", "公交", "打车", "导航"
    );

    private final GeoSearchService geoSearchService;
    private final PlaceDisambiguationService placeDisambiguationService;

    public ChatGeoSkillService(GeoSearchService geoSearchService,
                               PlaceDisambiguationService placeDisambiguationService) {
        this.geoSearchService = geoSearchService;
        this.placeDisambiguationService = placeDisambiguationService;
    }

    public GeoFactsResult collectFacts(ChatReqDTO req, List<Poi> localPois) {
        String question = req == null ? null : req.getQuestion();
        boolean geoIntent = isGeoIntent(question);
        boolean hasRouteContext = hasRouteContext(req);
        if (!geoIntent && !hasRouteContext) {
            return GeoFactsResult.empty(false);
        }

        String cityName = req == null || req.getContext() == null ? null : req.getContext().getCityName();
        GeoPoint userPoint = resolveUserPoint(req);

        List<GeoFact> facts = new ArrayList<>();
        facts.addAll(fromRouteContext(req, userPoint));
        facts.addAll(fromLocalPois(localPois, userPoint));

        if (geoIntent) {
            String keyword = extractMainKeyword(question);
            PlaceDisambiguationService.PlaceResolution resolution = placeDisambiguationService.disambiguate(keyword, cityName, null);
            if (resolution.clarificationRequired() && StringUtils.hasText(resolution.clarificationQuestion())) {
                return new GeoFactsResult(Collections.emptyList(), resolution.clarificationQuestion(), true);
            }

            if (resolution.best() != null) {
                PlaceDisambiguationService.ResolvedPlace best = resolution.best();
                facts.add(toFact(best.canonicalName(),
                        best.category(),
                        best.cityName(),
                        best.district(),
                        best.latitude(),
                        best.longitude(),
                        "external-disambiguation",
                        userPoint));
            }

            if (userPoint != null && userPoint.valid()) {
                List<GeoPoiCandidate> nearby = safeCandidates(geoSearchService.searchNearby(
                        userPoint,
                        cityName,
                        null,
                        1500,
                        6
                ));
                for (GeoPoiCandidate candidate : nearby) {
                    facts.add(toFact(candidate.getName(),
                            candidate.getCategory(),
                            candidate.getCityName(),
                            candidate.getDistrict(),
                            candidate.getLatitude(),
                            candidate.getLongitude(),
                            StringUtils.hasText(candidate.getSource()) ? candidate.getSource() : "external-nearby",
                            userPoint));
                }
            }

            if (StringUtils.hasText(keyword)) {
                List<GeoPoiCandidate> searched = safeCandidates(geoSearchService.searchByKeyword(keyword, cityName, 5));
                for (GeoPoiCandidate candidate : searched) {
                    facts.add(toFact(candidate.getName(),
                            candidate.getCategory(),
                            candidate.getCityName(),
                            candidate.getDistrict(),
                            candidate.getLatitude(),
                            candidate.getLongitude(),
                            StringUtils.hasText(candidate.getSource()) ? candidate.getSource() : "external-search",
                            userPoint));
                }
            }
        }

        List<GeoFact> deduped = dedupeAndSort(facts);
        return new GeoFactsResult(
                deduped.size() > 8 ? deduped.subList(0, 8) : deduped,
                null,
                geoIntent
        );
    }

    private boolean hasRouteContext(ChatReqDTO req) {
        return req != null
                && req.getContext() != null
                && req.getContext().getItinerary() != null
                && req.getContext().getItinerary().getNodes() != null
                && !req.getContext().getItinerary().getNodes().isEmpty();
    }

    private List<GeoFact> fromLocalPois(List<Poi> localPois, GeoPoint userPoint) {
        if (localPois == null || localPois.isEmpty()) {
            return Collections.emptyList();
        }
        List<GeoFact> facts = new ArrayList<>();
        for (Poi poi : localPois) {
            if (poi == null || !StringUtils.hasText(poi.getName())) {
                continue;
            }
            facts.add(toFact(poi.getName(),
                    poi.getCategory(),
                    poi.getCityName(),
                    poi.getDistrict(),
                    poi.getLatitude(),
                    poi.getLongitude(),
                    "local",
                    userPoint));
        }
        return facts;
    }

    private List<GeoFact> fromRouteContext(ChatReqDTO req, GeoPoint userPoint) {
        if (req == null
                || req.getContext() == null
                || req.getContext().getItinerary() == null
                || req.getContext().getItinerary().getNodes() == null
                || req.getContext().getItinerary().getNodes().isEmpty()) {
            return Collections.emptyList();
        }
        List<GeoFact> facts = new ArrayList<>();
        for (ChatReqDTO.ChatRouteNode node : req.getContext().getItinerary().getNodes()) {
            if (node == null || !StringUtils.hasText(node.getPoiName())) {
                continue;
            }
            facts.add(toFact(
                    node.getPoiName(),
                    node.getCategory(),
                    req.getContext().getCityName(),
                    node.getDistrict(),
                    node.getLatitude(),
                    node.getLongitude(),
                    "itinerary-route",
                    userPoint
            ));
        }
        return facts;
    }

    private GeoFact toFact(String name,
                           String category,
                           String cityName,
                           String district,
                           BigDecimal latitude,
                           BigDecimal longitude,
                           String source,
                           GeoPoint userPoint) {
        Integer distanceMeters = null;
        if (userPoint != null && userPoint.valid() && latitude != null && longitude != null) {
            distanceMeters = estimateDistanceMeters(userPoint.latitude(), userPoint.longitude(), latitude, longitude);
        }
        return new GeoFact(
                name,
                category,
                cityName,
                district,
                latitude,
                longitude,
                distanceMeters,
                source
        );
    }

    private List<GeoFact> dedupeAndSort(List<GeoFact> facts) {
        if (facts == null || facts.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, GeoFact> map = new LinkedHashMap<>();
        for (GeoFact fact : facts) {
            if (fact == null || !StringUtils.hasText(fact.name())) {
                continue;
            }
            String key = fact.name().trim().toLowerCase(Locale.ROOT);
            GeoFact existing = map.get(key);
            if (existing == null) {
                map.put(key, fact);
                continue;
            }
            if (better(fact, existing)) {
                map.put(key, fact);
            }
        }
        return map.values().stream()
                .sorted(Comparator
                        .comparing((GeoFact item) -> item.distanceMeters() == null ? Integer.MAX_VALUE : item.distanceMeters())
                        .thenComparing(item -> item.source() == null ? "" : item.source()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private boolean better(GeoFact left, GeoFact right) {
        if (left.distanceMeters() != null && right.distanceMeters() == null) {
            return true;
        }
        if (left.distanceMeters() != null && right.distanceMeters() != null) {
            return left.distanceMeters() < right.distanceMeters();
        }
        return Objects.equals(left.source(), "local") && !Objects.equals(right.source(), "local");
    }

    private GeoPoint resolveUserPoint(ChatReqDTO req) {
        if (req == null || req.getContext() == null) {
            return null;
        }
        Double lat = req.getContext().getUserLat();
        Double lng = req.getContext().getUserLng();
        if (lat == null || lng == null) {
            return null;
        }
        if (!Double.isFinite(lat) || !Double.isFinite(lng)) {
            return null;
        }
        GeoPoint point = new GeoPoint(BigDecimal.valueOf(lat), BigDecimal.valueOf(lng));
        return point.valid() ? point : null;
    }

    private boolean isGeoIntent(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        for (String keyword : GEO_INTENT_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String extractMainKeyword(String question) {
        if (!StringUtils.hasText(question)) {
            return null;
        }
        String normalized = question.replaceAll("[？?！!。,.，;；\\s]+", " ").trim();
        if (!StringUtils.hasText(normalized)) {
            return null;
        }
        String[] tokens = normalized.split("\\s+");
        String best = null;
        for (String token : tokens) {
            if (!StringUtils.hasText(token)) {
                continue;
            }
            String value = token.trim();
            if (value.length() < 2 || value.length() > 20) {
                continue;
            }
            if (isGeoIntent(value)) {
                continue;
            }
            if (best == null || value.length() > best.length()) {
                best = value;
            }
        }
        return best;
    }

    private Integer estimateDistanceMeters(BigDecimal lat1, BigDecimal lng1, BigDecimal lat2, BigDecimal lng2) {
        if (lat1 == null || lng1 == null || lat2 == null || lng2 == null) {
            return null;
        }
        double r = 6371_000D;
        double a1 = Math.toRadians(lat1.doubleValue());
        double a2 = Math.toRadians(lat2.doubleValue());
        double dLat = a2 - a1;
        double dLng = Math.toRadians(lng2.doubleValue() - lng1.doubleValue());
        double hav = Math.pow(Math.sin(dLat / 2), 2)
                + Math.cos(a1) * Math.cos(a2) * Math.pow(Math.sin(dLng / 2), 2);
        double distance = 2 * r * Math.asin(Math.sqrt(hav));
        return BigDecimal.valueOf(distance).setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private List<GeoPoiCandidate> safeCandidates(List<GeoPoiCandidate> source) {
        return source == null ? Collections.emptyList() : source;
    }

    public record GeoFact(String name,
                          String category,
                          String cityName,
                          String district,
                          BigDecimal latitude,
                          BigDecimal longitude,
                          Integer distanceMeters,
                          String source) {
    }

    public record GeoFactsResult(List<GeoFact> facts,
                                 String clarificationQuestion,
                                 boolean geoIntent) {
        public static GeoFactsResult empty(boolean geoIntent) {
            return new GeoFactsResult(Collections.emptyList(), null, geoIntent);
        }
    }
}

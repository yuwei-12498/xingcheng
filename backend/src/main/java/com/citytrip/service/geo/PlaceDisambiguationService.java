package com.citytrip.service.geo;

import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class PlaceDisambiguationService {

    public static final double DEFAULT_LOW_CONFIDENCE_THRESHOLD = 0.60D;
    private static final int DEFAULT_CANDIDATE_LIMIT = 6;

    private static final Map<String, String> ALIAS_TO_CANONICAL = new LinkedHashMap<>();

    static {
        ALIAS_TO_CANONICAL.put("ifs", "IFS国际金融中心");
        ALIAS_TO_CANONICAL.put("国金", "IFS国际金融中心");
        ALIAS_TO_CANONICAL.put("金融中心", "IFS国际金融中心");
        ALIAS_TO_CANONICAL.put("ifs国金", "IFS国际金融中心");
        ALIAS_TO_CANONICAL.put("ifs国际金融中心", "IFS国际金融中心");
    }

    private final GeoSearchService geoSearchService;

    public PlaceDisambiguationService(GeoSearchService geoSearchService) {
        this.geoSearchService = geoSearchService;
    }

    public PlaceResolution disambiguate(String keyword, String cityName, String preferredCategory) {
        return disambiguate(keyword, cityName, preferredCategory, DEFAULT_LOW_CONFIDENCE_THRESHOLD);
    }

    public PlaceResolution disambiguate(String keyword,
                                        String cityName,
                                        String preferredCategory,
                                        double lowConfidenceThreshold) {
        if (!StringUtils.hasText(keyword)) {
            return PlaceResolution.empty();
        }
        String normalizedKeyword = normalizeAlias(keyword);
        List<GeoPoiCandidate> geoCandidates = geoSearchService.searchByKeyword(normalizedKeyword, cityName, DEFAULT_CANDIDATE_LIMIT);
        if (geoCandidates.isEmpty()) {
            return PlaceResolution.empty();
        }

        List<ResolvedPlace> scored = geoCandidates.stream()
                .map(candidate -> scoreCandidate(normalizedKeyword, cityName, preferredCategory, candidate))
                .sorted(Comparator.comparingDouble(ResolvedPlace::score).reversed())
                .collect(Collectors.toCollection(ArrayList::new));
        if (scored.isEmpty()) {
            return PlaceResolution.empty();
        }

        ResolvedPlace best = scored.get(0);
        ResolvedPlace second = scored.size() > 1 ? scored.get(1) : null;
        double confidence = resolveConfidence(best, second);
        boolean clarificationRequired = confidence < lowConfidenceThreshold
                || (second != null && (best.score() - second.score()) < 0.08D && confidence < 0.82D);
        String clarificationQuestion = clarificationRequired ? buildClarificationQuestion(scored) : null;
        return new PlaceResolution(best, scored, confidence, clarificationRequired, clarificationQuestion);
    }

    public Optional<ResolvedPlace> resolveBest(String keyword, String cityName, String preferredCategory) {
        PlaceResolution resolution = disambiguate(keyword, cityName, preferredCategory);
        if (resolution.best() == null || resolution.clarificationRequired()) {
            return Optional.empty();
        }
        return Optional.of(resolution.best());
    }

    public String normalizeAlias(String rawKeyword) {
        if (!StringUtils.hasText(rawKeyword)) {
            return rawKeyword;
        }
        String trimmed = rawKeyword.trim();
        String lower = normalizeText(trimmed);
        for (Map.Entry<String, String> entry : ALIAS_TO_CANONICAL.entrySet()) {
            if (lower.contains(entry.getKey())) {
                return entry.getValue();
            }
        }
        return trimmed;
    }

    private ResolvedPlace scoreCandidate(String keyword,
                                         String cityName,
                                         String preferredCategory,
                                         GeoPoiCandidate candidate) {
        double cityScore = cityScore(cityName, candidate.getCityName());
        double categoryScore = categoryScore(preferredCategory, candidate.getCategory());
        double similarityScore = nameSimilarity(keyword, candidate.getName());
        double baseScore = cityScore * 0.35D + categoryScore * 0.25D + similarityScore * 0.40D;
        if (StringUtils.hasText(candidate.getSource())) {
            baseScore += 0.03D;
        }
        double score = clamp(baseScore, 0D, 1D);
        return new ResolvedPlace(
                candidate.getName(),
                candidate.getCityName(),
                candidate.getDistrict(),
                candidate.getCategory(),
                candidate.getLatitude(),
                candidate.getLongitude(),
                candidate.getSource(),
                score
        );
    }

    private double resolveConfidence(ResolvedPlace best, ResolvedPlace second) {
        if (best == null) {
            return 0D;
        }
        if (second == null) {
            return clamp(Math.max(best.score(), 0.72D), 0D, 1D);
        }
        double gap = Math.max(0D, best.score() - second.score());
        return clamp(best.score() + Math.min(0.15D, gap * 0.7D), 0D, 1D);
    }

    private String buildClarificationQuestion(List<ResolvedPlace> scored) {
        if (scored == null || scored.isEmpty()) {
            return "我没完全确定你指的是哪个地点，能再说具体一点吗？";
        }
        List<String> topNames = scored.stream()
                .limit(3)
                .map(ResolvedPlace::canonicalName)
                .filter(StringUtils::hasText)
                .distinct()
                .toList();
        if (topNames.size() >= 2) {
            return "你指的是“" + topNames.get(0) + "”还是“" + topNames.get(1) + "”？";
        }
        return "我没完全确定你指的是哪个地点，能再说具体一点吗？";
    }

    private double cityScore(String requestCity, String candidateCity) {
        if (!StringUtils.hasText(requestCity)) {
            return 0.65D;
        }
        String request = normalizeText(requestCity);
        String candidate = normalizeText(candidateCity);
        if (!StringUtils.hasText(candidate)) {
            return 0.35D;
        }
        return request.equals(candidate) || candidate.contains(request) || request.contains(candidate) ? 1D : 0.25D;
    }

    private double categoryScore(String requestCategory, String candidateCategory) {
        if (!StringUtils.hasText(requestCategory)) {
            return 0.55D;
        }
        String request = normalizeText(requestCategory);
        String candidate = normalizeText(candidateCategory);
        if (!StringUtils.hasText(candidate)) {
            return 0.25D;
        }
        return request.equals(candidate) || candidate.contains(request) || request.contains(candidate) ? 1D : 0.2D;
    }

    private double nameSimilarity(String keyword, String candidateName) {
        String left = normalizeText(keyword);
        String right = normalizeText(candidateName);
        if (!StringUtils.hasText(left) || !StringUtils.hasText(right)) {
            return 0D;
        }
        if (left.equals(right)) {
            return 1D;
        }
        if (right.contains(left) || left.contains(right)) {
            return 0.92D;
        }
        double jaccard = bigramJaccard(left, right);
        return clamp(jaccard, 0D, 0.9D);
    }

    private double bigramJaccard(String left, String right) {
        List<String> leftTokens = toBigrams(left);
        List<String> rightTokens = toBigrams(right);
        if (leftTokens.isEmpty() || rightTokens.isEmpty()) {
            return 0D;
        }
        long intersection = leftTokens.stream().filter(rightTokens::contains).count();
        long union = leftTokens.size() + rightTokens.size() - intersection;
        if (union <= 0L) {
            return 0D;
        }
        return intersection * 1D / union;
    }

    private List<String> toBigrams(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        if (text.length() <= 1) {
            return List.of(text);
        }
        List<String> tokens = new ArrayList<>(text.length() - 1);
        for (int i = 0; i < text.length() - 1; i++) {
            tokens.add(text.substring(i, i + 2));
        }
        return tokens;
    }

    private String normalizeText(String value) {
        if (!StringUtils.hasText(value)) {
            return "";
        }
        return value.trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[\\s,，。！？:：;；·'\"()（）\\[\\]{}]+", "");
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    public record PlaceResolution(ResolvedPlace best,
                                  List<ResolvedPlace> candidates,
                                  double confidence,
                                  boolean clarificationRequired,
                                  String clarificationQuestion) {
        public static PlaceResolution empty() {
            return new PlaceResolution(null, Collections.emptyList(), 0D, true, null);
        }
    }

    public record ResolvedPlace(String canonicalName,
                                String cityName,
                                String district,
                                String category,
                                BigDecimal latitude,
                                BigDecimal longitude,
                                String source,
                                double score) {
    }
}


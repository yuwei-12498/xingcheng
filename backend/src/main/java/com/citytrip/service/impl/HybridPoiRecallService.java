package com.citytrip.service.impl;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.mapper.UserBehaviorEventMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.query.UserPoiPreferenceStat;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Slf4j
@Service
public class HybridPoiRecallService {

    private static final int RECENT_DAYS = 180;
    private static final int MAX_SEED_POI_COUNT = 12;
    private static final int SIMILAR_USER_LIMIT = 120;
    private static final Duration CACHE_TTL = Duration.ofMinutes(10);
    private static final String CACHE_KEY_PREFIX = "hybrid:poi-recall:v1:";
    private static final String DEFAULT_RECALL_STRATEGY = "hybrid-usercf-content-v1";
    private static final String CONTENT_FALLBACK_STRATEGY = "content-feature-fallback-v1";
    private static final double REPEAT_PENALTY_MAX = 0.42D;
    private static final List<String> GROUP_COMPANION_KEYWORDS = List.of(
            "group", "multi", "friends", "friend", "family", "families", "team", "classmate",
            "couple", "partner", "kids", "parent", "child", "children",
            "\u591a\u4eba", "\u7ed3\u4f34", "\u670b\u53cb", "\u597d\u53cb", "\u5bb6\u5ead", "\u5bb6\u4eba",
            "\u4eb2\u5b50", "\u56e2\u961f", "\u56e2\u5efa", "\u540c\u5b66", "\u60c5\u4fa3", "\u4f34\u4fa3"
    );
    private static final List<String> SOLO_COMPANION_KEYWORDS = List.of(
            "solo", "single", "alone", "\u72ec\u81ea", "\u5355\u4eba", "\u4e00\u4eba", "\u4e2a\u4eba"
    );
    private static final List<String> GROUP_FRIENDLY_POI_KEYWORDS = List.of(
            "group", "friends", "friend", "family", "families", "team", "couple", "kids",
            "parent", "child", "children", "social", "interactive",
            "\u591a\u4eba", "\u7ed3\u4f34", "\u670b\u53cb", "\u597d\u53cb", "\u5bb6\u5ead", "\u5bb6\u4eba",
            "\u4eb2\u5b50", "\u56e2\u961f", "\u56e2\u5efa", "\u540c\u5b66", "\u60c5\u4fa3", "\u4e92\u52a8",
            "\u805a\u4f1a", "\u5546\u5708", "\u8857\u533a", "\u7f8e\u98df", "\u516c\u56ed", "\u535a\u7269\u9986"
    );

    private final UserBehaviorEventMapper userBehaviorEventMapper;
    private final PoiMapper poiMapper;
    private final ItineraryRouteOptimizer routeOptimizer;
    private final boolean redisEnabled;
    private final RedisTemplate<String, Object> redisTemplate;
    private final ObjectMapper objectMapper;

    public HybridPoiRecallService(UserBehaviorEventMapper userBehaviorEventMapper,
                                  PoiMapper poiMapper,
                                  ItineraryRouteOptimizer routeOptimizer,
                                  @Value("${app.redis.enabled:false}") boolean redisEnabled,
                                  @Nullable RedisTemplate<String, Object> redisTemplate,
                                  ObjectMapper objectMapper) {
        this.userBehaviorEventMapper = userBehaviorEventMapper;
        this.poiMapper = poiMapper;
        this.routeOptimizer = routeOptimizer;
        this.redisEnabled = redisEnabled;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
    }

    public RecallResult recall(Long userId,
                               GenerateReqDTO req,
                               List<Poi> rawCandidates,
                               int recallLimit) {
        if (rawCandidates == null || rawCandidates.isEmpty()) {
            return new RecallResult(Collections.emptyList(), Collections.emptyList(), CONTENT_FALLBACK_STRATEGY, 0, 0, false);
        }

        List<Poi> filteredCandidates = routeOptimizer.prepareCandidates(rawCandidates, req, false);
        if (filteredCandidates.isEmpty()) {
            return new RecallResult(Collections.emptyList(), Collections.emptyList(), CONTENT_FALLBACK_STRATEGY, 0, 0, false);
        }

        int normalizedRecallLimit = Math.max(1, Math.min(recallLimit, filteredCandidates.size()));
        if (userId == null) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-anonymous");
        }

        Map<Long, Double> userVector = loadUserVector(userId);
        if (userVector.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-cold-start");
        }

        String cacheKey = buildCacheKey(userId, req, filteredCandidates, normalizedRecallLimit, userVector);
        LinkedHashMap<Long, Double> cachedScoreMap = readCachedScoreMap(cacheKey);
        if (!cachedScoreMap.isEmpty()) {
            List<Poi> cachedCandidates = restoreCachedCandidates(filteredCandidates, cachedScoreMap, normalizedRecallLimit);
            if (!cachedCandidates.isEmpty()) {
                return new RecallResult(filteredCandidates, cachedCandidates, DEFAULT_RECALL_STRATEGY + "-cache", userVector.size(), 0, true);
            }
        }

        List<Long> seedPoiIds = userVector.entrySet().stream()
                .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                .map(Map.Entry::getKey)
                .limit(MAX_SEED_POI_COUNT)
                .toList();
        if (seedPoiIds.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-seed-empty", userVector);
        }

        List<Long> similarUserIds;
        try {
            similarUserIds = userBehaviorEventMapper.selectSimilarUserIdsByPoiIds(
                    seedPoiIds,
                    userId,
                    RECENT_DAYS,
                    SIMILAR_USER_LIMIT
            );
        } catch (DataAccessException ex) {
            log.warn("用户协同召回降级为内容召回（邻居用户查询失败），userId={}, reason={}", userId, ex.getMessage());
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-analytics-unavailable", userVector);
        }
        if (similarUserIds == null || similarUserIds.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-no-neighbor", userVector);
        }

        Set<Long> relevantPoiIds = new LinkedHashSet<>();
        filteredCandidates.stream().map(Poi::getId).filter(Objects::nonNull).forEach(relevantPoiIds::add);
        relevantPoiIds.addAll(seedPoiIds);
        List<UserPoiPreferenceStat> neighborRows;
        try {
            neighborRows = userBehaviorEventMapper.selectUserPoiPreferencesByUserIdsAndPoiIds(
                    similarUserIds,
                    new ArrayList<>(relevantPoiIds),
                    RECENT_DAYS
            );
        } catch (DataAccessException ex) {
            log.warn("用户协同召回降级为内容召回（邻居偏好查询失败），userId={}, reason={}", userId, ex.getMessage());
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-analytics-unavailable", userVector);
        }
        if (neighborRows == null || neighborRows.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-neighbor-empty", userVector);
        }

        Map<Long, Map<Long, Double>> neighborVectors = toUserVectors(neighborRows);
        Map<Long, Double> similarityMap = buildSimilarityMap(userVector, neighborVectors);
        if (similarityMap.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-zero-similarity", userVector);
        }

        UserContentProfile profile = buildUserContentProfile(seedPoiIds, userVector);
        PersonalizedScores personalizedScores = blendScores(filteredCandidates, userVector, neighborVectors, similarityMap, profile, req);
        if (personalizedScores.orderedPoiIds().isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-blend-empty", userVector);
        }

        List<Poi> personalizedCandidates = materializePersonalizedCandidates(
                filteredCandidates,
                personalizedScores.blendedTempScores(),
                personalizedScores.orderedPoiIds(),
                normalizedRecallLimit
        );
        if (personalizedCandidates.isEmpty()) {
            return contentFallback(filteredCandidates, normalizedRecallLimit, CONTENT_FALLBACK_STRATEGY + "-materialize-empty", userVector);
        }

        cacheScoreMap(cacheKey, personalizedScores.topScoreMap(normalizedRecallLimit));
        return new RecallResult(
                filteredCandidates,
                personalizedCandidates,
                DEFAULT_RECALL_STRATEGY,
                userVector.size(),
                similarityMap.size(),
                false
        );
    }

    private RecallResult contentFallback(List<Poi> filteredCandidates, int recallLimit, String strategy) {
        return contentFallback(filteredCandidates, recallLimit, strategy, Collections.emptyMap());
    }

    private RecallResult contentFallback(List<Poi> filteredCandidates,
                                         int recallLimit,
                                         String strategy,
                                         Map<Long, Double> userVector) {
        double maxSeenWeight = userVector == null || userVector.isEmpty()
                ? 1D
                : userVector.values().stream().mapToDouble(Double::doubleValue).max().orElse(1D);
        List<Poi> fallback = filteredCandidates.stream()
                .sorted((left, right) -> {
                    double rightScore = resolveContentScore(right)
                            - resolveRepeatPenalty(right == null ? null : right.getId(), userVector, maxSeenWeight) * 4.0D;
                    double leftScore = resolveContentScore(left)
                            - resolveRepeatPenalty(left == null ? null : left.getId(), userVector, maxSeenWeight) * 4.0D;
                    int byScore = Double.compare(rightScore, leftScore);
                    if (byScore != 0) {
                        return byScore;
                    }
                    Long rightId = right == null ? null : right.getId();
                    Long leftId = left == null ? null : left.getId();
                    return Comparator.nullsLast(Long::compareTo).compare(leftId, rightId);
                })
                .limit(recallLimit)
                .collect(Collectors.toCollection(ArrayList::new));
        return new RecallResult(filteredCandidates, fallback, strategy, 0, 0, false);
    }

    private Map<Long, Double> loadUserVector(Long userId) {
        List<UserPoiPreferenceStat> rows;
        try {
            rows = userBehaviorEventMapper.selectUserPoiPreferences(userId, RECENT_DAYS);
        } catch (DataAccessException ex) {
            log.warn("用户画像查询失败，使用内容召回兜底，userId={}, reason={}", userId, ex.getMessage());
            return Collections.emptyMap();
        }
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyMap();
        }
        Map<Long, Double> vector = new LinkedHashMap<>();
        for (UserPoiPreferenceStat row : rows) {
            if (row == null || row.getPoiId() == null || row.getPreferenceScore() == null) {
                continue;
            }
            vector.merge(row.getPoiId(), Math.max(0D, row.getPreferenceScore()), Double::sum);
        }
        return vector;
    }

    private Map<Long, Map<Long, Double>> toUserVectors(List<UserPoiPreferenceStat> rows) {
        Map<Long, Map<Long, Double>> grouped = new LinkedHashMap<>();
        for (UserPoiPreferenceStat row : rows) {
            if (row == null || row.getUserId() == null || row.getPoiId() == null || row.getPreferenceScore() == null) {
                continue;
            }
            grouped.computeIfAbsent(row.getUserId(), key -> new LinkedHashMap<>())
                    .merge(row.getPoiId(), Math.max(0D, row.getPreferenceScore()), Double::sum);
        }
        return grouped;
    }

    private Map<Long, Double> buildSimilarityMap(Map<Long, Double> userVector,
                                                 Map<Long, Map<Long, Double>> neighborVectors) {
        if (userVector.isEmpty() || neighborVectors.isEmpty()) {
            return Collections.emptyMap();
        }

        double userNorm = Math.sqrt(userVector.values().stream()
                .mapToDouble(value -> value * value)
                .sum());
        if (userNorm <= 0D) {
            return Collections.emptyMap();
        }

        Map<Long, Double> similarityMap = new LinkedHashMap<>();
        for (Map.Entry<Long, Map<Long, Double>> entry : neighborVectors.entrySet()) {
            Map<Long, Double> neighborVector = entry.getValue();
            if (neighborVector == null || neighborVector.isEmpty()) {
                continue;
            }
            double neighborNorm = Math.sqrt(neighborVector.values().stream()
                    .mapToDouble(value -> value * value)
                    .sum());
            if (neighborNorm <= 0D) {
                continue;
            }
            double dot = 0D;
            int overlapCount = 0;
            for (Map.Entry<Long, Double> userEntry : userVector.entrySet()) {
                Double neighborWeight = neighborVector.get(userEntry.getKey());
                if (neighborWeight == null || neighborWeight <= 0D) {
                    continue;
                }
                dot += userEntry.getValue() * neighborWeight;
                overlapCount++;
            }
            if (dot <= 0D || overlapCount == 0) {
                continue;
            }
            double cosine = dot / (userNorm * neighborNorm);
            double supportFactor = Math.min(1D, overlapCount / 3D);
            double similarity = cosine * (0.6D + 0.4D * supportFactor);
            if (similarity > 0.01D) {
                similarityMap.put(entry.getKey(), similarity);
            }
        }
        return similarityMap;
    }

    private UserContentProfile buildUserContentProfile(List<Long> seedPoiIds, Map<Long, Double> userVector) {
        if (seedPoiIds == null || seedPoiIds.isEmpty()) {
            return UserContentProfile.empty();
        }
        List<Poi> historyPois = poiMapper.selectBatchIds(seedPoiIds);
        if (historyPois == null || historyPois.isEmpty()) {
            return UserContentProfile.empty();
        }

        Map<Long, Poi> historyPoiMap = historyPois.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getId() != null)
                .collect(Collectors.toMap(Poi::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        Map<String, Double> districtAffinity = new LinkedHashMap<>();
        Map<String, Double> categoryAffinity = new LinkedHashMap<>();
        Map<String, Double> tagAffinity = new LinkedHashMap<>();

        for (Long seedPoiId : seedPoiIds) {
            Poi poi = historyPoiMap.get(seedPoiId);
            if (poi == null) {
                continue;
            }
            double weight = userVector.getOrDefault(seedPoiId, 0D);
            if (weight <= 0D) {
                continue;
            }
            mergeWeight(districtAffinity, poi.getDistrict(), weight);
            mergeWeight(categoryAffinity, poi.getCategory(), weight);
            for (String tag : splitTags(poi.getTags())) {
                mergeWeight(tagAffinity, tag, weight);
            }
        }
        return new UserContentProfile(
                normalizeAffinityMap(districtAffinity),
                normalizeAffinityMap(categoryAffinity),
                normalizeAffinityMap(tagAffinity)
        );
    }

    private PersonalizedScores blendScores(List<Poi> filteredCandidates,
                                           Map<Long, Double> userVector,
                                           Map<Long, Map<Long, Double>> neighborVectors,
                                           Map<Long, Double> similarityMap,
                                           UserContentProfile profile,
                                           GenerateReqDTO req) {
        if (filteredCandidates == null || filteredCandidates.isEmpty()) {
            return PersonalizedScores.empty();
        }

        Map<Long, Double> collaborativeScores = new HashMap<>();
        Map<Long, Integer> supportCountMap = new HashMap<>();
        for (Map.Entry<Long, Double> similarityEntry : similarityMap.entrySet()) {
            Map<Long, Double> neighborVector = neighborVectors.get(similarityEntry.getKey());
            if (neighborVector == null || neighborVector.isEmpty()) {
                continue;
            }
            double similarity = similarityEntry.getValue();
            for (Map.Entry<Long, Double> neighborEntry : neighborVector.entrySet()) {
                if (neighborEntry.getValue() == null || neighborEntry.getValue() <= 0D) {
                    continue;
                }
                collaborativeScores.merge(neighborEntry.getKey(), similarity * neighborEntry.getValue(), Double::sum);
                supportCountMap.merge(neighborEntry.getKey(), 1, Integer::sum);
            }
        }

        double maxContentScore = filteredCandidates.stream()
                .mapToDouble(this::resolveContentScore)
                .max()
                .orElse(1D);
        double maxCollaborativeScore = collaborativeScores.values().stream()
                .mapToDouble(Double::doubleValue)
                .max()
                .orElse(0D);
        int maxSupport = supportCountMap.values().stream().mapToInt(Integer::intValue).max().orElse(0);
        double maxSeenWeight = userVector.values().stream().mapToDouble(Double::doubleValue).max().orElse(1D);

        Map<Long, Double> finalRankingScoreMap = new HashMap<>();
        Map<Long, Double> blendedTempScoreMap = new HashMap<>();
        for (Poi poi : filteredCandidates) {
            if (poi == null || poi.getId() == null) {
                continue;
            }
            double contentScore = resolveContentScore(poi);
            double normalizedContent = maxContentScore <= 0D ? 0D : contentScore / maxContentScore;
            double collaborativeScore = collaborativeScores.getOrDefault(poi.getId(), 0D);
            double normalizedCollaborative = maxCollaborativeScore <= 0D ? 0D : collaborativeScore / maxCollaborativeScore;
            double profileScore = scoreByProfile(poi, profile, req);
            double supportScore = maxSupport <= 0 ? 0D : supportCountMap.getOrDefault(poi.getId(), 0) * 1D / maxSupport;
            double repeatPenalty = resolveRepeatPenalty(poi.getId(), userVector, maxSeenWeight);

            double rankingScore = normalizedContent * 0.55D
                    + normalizedCollaborative * 0.25D
                    + profileScore * 0.15D
                    + supportScore * 0.05D
                    - repeatPenalty;
            double blendedTempScore = contentScore
                    + normalizedCollaborative * 2.20D
                    + profileScore * 1.30D
                    + supportScore * 0.80D
                    - repeatPenalty * 2.00D;
            finalRankingScoreMap.put(poi.getId(), rankingScore);
            blendedTempScoreMap.put(poi.getId(), blendedTempScore);
        }

        List<Long> orderedPoiIds = filteredCandidates.stream()
                .map(Poi::getId)
                .filter(Objects::nonNull)
                .sorted((left, right) -> {
                    int byScore = Double.compare(
                            finalRankingScoreMap.getOrDefault(right, Double.NEGATIVE_INFINITY),
                            finalRankingScoreMap.getOrDefault(left, Double.NEGATIVE_INFINITY)
                    );
                    if (byScore != 0) {
                        return byScore;
                    }
                    return Long.compare(left, right);
                })
                .toList();
        return new PersonalizedScores(orderedPoiIds, blendedTempScoreMap);
    }

    private double scoreByProfile(Poi poi, UserContentProfile profile, GenerateReqDTO req) {
        if (poi == null) {
            return 0D;
        }
        double score = 0D;
        score += profile.districtAffinity().getOrDefault(normalizeKey(poi.getDistrict()), 0D) * 0.35D;
        score += profile.categoryAffinity().getOrDefault(normalizeKey(poi.getCategory()), 0D) * 0.30D;
        double tagScore = splitTags(poi.getTags()).stream()
                .mapToDouble(tag -> profile.tagAffinity().getOrDefault(normalizeKey(tag), 0D))
                .boxed()
                .sorted(Comparator.reverseOrder())
                .mapToDouble(Double::doubleValue)
                .limit(2)
                .sum();
        score += tagScore * 0.20D;
        if (req != null && Boolean.TRUE.equals(req.getIsNight()) && Integer.valueOf(1).equals(poi.getNightAvailable())) {
            score += 0.08D;
        }
        if (req != null && Boolean.TRUE.equals(req.getIsRainy())
                && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly()))) {
            score += 0.07D;
        }
        if (req != null && StringUtils.hasText(req.getWalkingLevel())
                && Objects.equals(normalizeKey(req.getWalkingLevel()), normalizeKey(poi.getWalkingLevel()))) {
            score += 0.05D;
        }
        if (matchesCompanionPreference(req, poi)) {
            score += 0.06D;
        }
        return Math.min(score, 1D);
    }

    private boolean matchesCompanionPreference(GenerateReqDTO req, Poi poi) {
        if (req == null || poi == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeKey(req.getCompanionType());
        String audienceText = normalizeKey(String.join(" ",
                nullToEmpty(poi.getSuitableFor()),
                nullToEmpty(poi.getTags()),
                nullToEmpty(poi.getCategory()),
                nullToEmpty(poi.getDescription())));
        if (!StringUtils.hasText(audienceText)) {
            return false;
        }
        return audienceText.contains(companionType)
                || (isMultiPersonTrip(req) && containsAnyNormalized(audienceText, GROUP_FRIENDLY_POI_KEYWORDS));
    }

    private boolean isMultiPersonTrip(GenerateReqDTO req) {
        if (req == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeKey(req.getCompanionType());
        if (containsAnyNormalized(companionType, SOLO_COMPANION_KEYWORDS)) {
            return false;
        }
        return containsAnyNormalized(companionType, GROUP_COMPANION_KEYWORDS);
    }

    private boolean containsAnyNormalized(String normalizedText, Collection<String> keywords) {
        if (!StringUtils.hasText(normalizedText) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeKey(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private List<Poi> materializePersonalizedCandidates(List<Poi> filteredCandidates,
                                                        Map<Long, Double> blendedTempScores,
                                                        List<Long> orderedPoiIds,
                                                        int recallLimit) {
        if (filteredCandidates == null || filteredCandidates.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Poi> candidateMap = filteredCandidates.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getId() != null)
                .collect(Collectors.toMap(Poi::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<Poi> recalled = new ArrayList<>();
        for (Long poiId : orderedPoiIds) {
            Poi poi = candidateMap.get(poiId);
            if (poi == null) {
                continue;
            }
            Double personalizedTempScore = blendedTempScores.get(poiId);
            if (personalizedTempScore != null) {
                mergeRecallBoost(poi, personalizedTempScore);
                poi.setTempScore(personalizedTempScore);
            }
            recalled.add(poi);
            if (recalled.size() >= recallLimit) {
                break;
            }
        }
        return recalled;
    }

    private LinkedHashMap<Long, Double> readCachedScoreMap(String cacheKey) {
        if (!isRedisAvailable()) {
            return new LinkedHashMap<>();
        }
        try {
            Object cached = redisTemplate.opsForValue().get(cacheKey);
            if (cached == null) {
                return new LinkedHashMap<>();
            }
            Map<String, Double> converted = objectMapper.convertValue(cached, new TypeReference<>() {
            });
            LinkedHashMap<Long, Double> scoreMap = new LinkedHashMap<>();
            for (Map.Entry<String, Double> entry : converted.entrySet()) {
                if (!StringUtils.hasText(entry.getKey()) || entry.getValue() == null) {
                    continue;
                }
                scoreMap.put(Long.parseLong(entry.getKey()), entry.getValue());
            }
            return scoreMap;
        } catch (IllegalArgumentException ex) {
            log.warn("解析个性化召回缓存失败, cacheKey={}", cacheKey, ex);
            return new LinkedHashMap<>();
        } catch (DataAccessException ex) {
            log.warn("Redis 不可用，跳过个性化召回缓存读取, cacheKey={}", cacheKey);
            return new LinkedHashMap<>();
        }
    }

    private void cacheScoreMap(String cacheKey, LinkedHashMap<Long, Double> scoreMap) {
        if (!isRedisAvailable() || scoreMap == null || scoreMap.isEmpty()) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(cacheKey, scoreMap, CACHE_TTL);
        } catch (DataAccessException ex) {
            log.warn("Redis 涓嶅彲鐢紝璺宠繃涓€у寲鍙洖缂撳瓨鍐欏叆锛宑acheKey={}", cacheKey);
        }
    }

    private List<Poi> restoreCachedCandidates(List<Poi> filteredCandidates,
                                              LinkedHashMap<Long, Double> cachedScoreMap,
                                              int recallLimit) {
        if (filteredCandidates == null || filteredCandidates.isEmpty() || cachedScoreMap.isEmpty()) {
            return Collections.emptyList();
        }
        Map<Long, Poi> candidateMap = filteredCandidates.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getId() != null)
                .collect(Collectors.toMap(Poi::getId, Function.identity(), (left, right) -> left, LinkedHashMap::new));
        List<Poi> recalled = new ArrayList<>();
        for (Map.Entry<Long, Double> entry : cachedScoreMap.entrySet()) {
            Poi poi = candidateMap.remove(entry.getKey());
            if (poi == null) {
                continue;
            }
            poi.setTempScore(entry.getValue());
            recalled.add(poi);
            if (recalled.size() >= recallLimit) {
                return recalled;
            }
        }
        filteredCandidates.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getId() != null)
                .filter(poi -> candidateMap.containsKey(poi.getId()))
                .limit(Math.max(0, recallLimit - recalled.size()))
                .forEach(recalled::add);
        return recalled;
    }

    private String buildCacheKey(Long userId,
                                 GenerateReqDTO req,
                                 List<Poi> filteredCandidates,
                                 int recallLimit,
                                 Map<Long, Double> userVector) {
        StringBuilder builder = new StringBuilder(CACHE_KEY_PREFIX)
                .append(userId == null ? 0L : userId)
                .append(':')
                .append(recallLimit)
                .append(':')
                .append(req == null ? "na" : normalizeKey(req.getTripDate()))
                .append(':')
                .append(req != null && Boolean.TRUE.equals(req.getIsRainy()))
                .append(':')
                .append(req != null && Boolean.TRUE.equals(req.getIsNight()))
                .append(':')
                .append(normalizeKey(req == null ? null : req.getWalkingLevel()))
                .append(':')
                .append(normalizeKey(req == null ? null : req.getCompanionType()));
        if (req != null && req.getThemes() != null) {
            for (String theme : req.getThemes()) {
                builder.append(':').append(normalizeKey(theme));
            }
        }
        builder.append(':').append(filteredCandidates.stream()
                .map(Poi::getId)
                .filter(Objects::nonNull)
                .limit(8)
                .map(String::valueOf)
                .collect(Collectors.joining("-")));
        if (userVector != null && !userVector.isEmpty()) {
            String userSignature = userVector.entrySet().stream()
                    .sorted(Map.Entry.<Long, Double>comparingByValue().reversed())
                    .limit(6)
                    .map(entry -> entry.getKey() + "-" + Math.round(entry.getValue() * 100D))
                    .collect(Collectors.joining("_"));
            builder.append(':').append(userSignature);
        }
        return builder.toString();
    }

    private double resolveRepeatPenalty(Long poiId, Map<Long, Double> userVector, double maxSeenWeight) {
        if (poiId == null || userVector == null || userVector.isEmpty() || !userVector.containsKey(poiId)) {
            return 0D;
        }
        double rawWeight = userVector.getOrDefault(poiId, 0D);
        if (rawWeight <= 0D) {
            return 0D;
        }
        double normalized = Math.min(1D, rawWeight / Math.max(1D, maxSeenWeight));
        if (normalized >= 0.85D) {
            return REPEAT_PENALTY_MAX;
        }
        if (normalized >= 0.60D) {
            return 0.32D;
        }
        if (normalized >= 0.30D) {
            return 0.22D;
        }
        return 0.12D + normalized * 0.10D;
    }

    private boolean isRedisAvailable() {
        return redisEnabled && redisTemplate != null;
    }

    private double resolveContentScore(Poi poi) {
        if (poi == null || poi.getTempScore() == null) {
            return 0D;
        }
        return poi.getTempScore();
    }

    private void mergeRecallBoost(Poi poi, double personalizedTempScore) {
        if (poi == null) {
            return;
        }
        double baseScore = poi.getTempScore() == null ? 0D : poi.getTempScore();
        double delta = personalizedTempScore - baseScore;
        Map<String, Double> breakdown = poi.getTempScoreBreakdown() == null
                ? new LinkedHashMap<>()
                : new LinkedHashMap<>(poi.getTempScoreBreakdown());
        if (Math.abs(delta) > 1e-6) {
            breakdown.put("personalizedRecall", delta);
        }
        poi.setTempScoreBreakdown(breakdown);
    }

    private void mergeWeight(Map<String, Double> affinityMap, String rawKey, double weight) {
        String key = normalizeKey(rawKey);
        if (!StringUtils.hasText(key) || weight <= 0D) {
            return;
        }
        affinityMap.merge(key, weight, Double::sum);
    }

    private Map<String, Double> normalizeAffinityMap(Map<String, Double> affinityMap) {
        if (affinityMap == null || affinityMap.isEmpty()) {
            return Collections.emptyMap();
        }
        double max = affinityMap.values().stream().mapToDouble(Double::doubleValue).max().orElse(1D);
        if (max <= 0D) {
            return Collections.emptyMap();
        }
        Map<String, Double> normalized = new LinkedHashMap<>();
        affinityMap.forEach((key, value) -> normalized.put(key, value / max));
        return normalized;
    }

    private String normalizeKey(String raw) {
        if (!StringUtils.hasText(raw)) {
            return "";
        }
        return raw.trim().toLowerCase(Locale.ROOT);
    }

    private List<String> splitTags(String tags) {
        if (!StringUtils.hasText(tags)) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split("[锛?銆乗\s]+"))
                .filter(StringUtils::hasText)
                .map(this::normalizeKey)
                .distinct()
                .toList();
    }

    private record UserContentProfile(Map<String, Double> districtAffinity,
                                      Map<String, Double> categoryAffinity,
                                      Map<String, Double> tagAffinity) {

        private static UserContentProfile empty() {
            return new UserContentProfile(Collections.emptyMap(), Collections.emptyMap(), Collections.emptyMap());
        }
    }

    private record PersonalizedScores(List<Long> orderedPoiIds,
                                      Map<Long, Double> blendedTempScores) {

        private static PersonalizedScores empty() {
            return new PersonalizedScores(Collections.emptyList(), Collections.emptyMap());
        }

        private LinkedHashMap<Long, Double> topScoreMap(int limit) {
            LinkedHashMap<Long, Double> scoreMap = new LinkedHashMap<>();
            orderedPoiIds.stream()
                    .limit(limit)
                    .forEach(poiId -> scoreMap.put(poiId, blendedTempScores.getOrDefault(poiId, 0D)));
            return scoreMap;
        }
    }

    public record RecallResult(List<Poi> filteredCandidates,
                               List<Poi> recalledCandidates,
                               String recallStrategy,
                               int historyPoiCount,
                               int similarUserCount,
                               boolean cacheHit) {
    }
}

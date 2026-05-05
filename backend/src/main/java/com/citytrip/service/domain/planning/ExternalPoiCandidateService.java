package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ExternalPoiCandidateService {
    private static final LocalTime DEFAULT_OPEN_TIME = LocalTime.of(9, 0);
    private static final LocalTime DEFAULT_CLOSE_TIME = LocalTime.of(21, 0);
    private static final BigDecimal DEFAULT_AVG_COST = BigDecimal.valueOf(80);
    private static final int DEFAULT_STAY_DURATION_MINUTES = 90;
    private static final List<String> INDOOR_KEYWORDS = List.of(
            "museum", "gallery", "mall", "shopping", "bookstore", "library", "cinema", "theater",
            "art center", "exhibition", "aquarium", "cafe", "coffee", "restaurant",
            "博物馆", "美术馆", "展览", "商场", "购物", "书店", "图书馆", "影院", "剧院", "咖啡", "餐厅"
    );
    private static final List<String> RAIN_FRIENDLY_KEYWORDS = List.of(
            "museum", "gallery", "mall", "shopping", "bookstore", "library", "cinema", "theater",
            "art center", "aquarium", "cafe", "coffee", "restaurant", "food hall",
            "博物馆", "美术馆", "商场", "购物", "书店", "图书馆", "影院", "剧院", "咖啡", "餐厅", "美食"
    );
    private static final List<String> LOW_WALKING_KEYWORDS = List.of(
            "museum", "gallery", "mall", "shopping", "bookstore", "library", "cinema", "theater",
            "art center", "aquarium", "cafe", "restaurant", "tea", "market",
            "博物馆", "美术馆", "商场", "购物", "书店", "图书馆", "影院", "剧院", "咖啡", "餐厅", "商圈"
    );
    private static final List<String> HIGH_WALKING_KEYWORDS = List.of(
            "trail", "mountain", "hiking", "park", "forest", "camp", "scenic", "wetland", "lake",
            "步道", "山", "徒步", "公园", "森林", "露营", "景区", "湿地", "湖"
    );
    private static final List<String> NIGHT_FRIENDLY_KEYWORDS = List.of(
            "mall", "shopping", "cinema", "bar", "night market", "food", "restaurant", "street",
            "商场", "购物", "影院", "酒吧", "夜市", "美食", "餐厅", "街区"
    );
    private static final List<String> FAMILY_FRIENDLY_KEYWORDS = List.of(
            "museum", "gallery", "mall", "aquarium", "park", "zoo", "restaurant",
            "博物馆", "美术馆", "商场", "海洋馆", "公园", "动物园", "餐厅"
    );
    private static final List<String> COUPLE_FRIENDLY_KEYWORDS = List.of(
            "gallery", "cafe", "restaurant", "street", "park", "cinema",
            "美术馆", "咖啡", "餐厅", "街区", "公园", "影院"
    );

    private final GeoSearchService geoSearchService;

    public ExternalPoiCandidateService(GeoSearchService geoSearchService) {
        this.geoSearchService = geoSearchService;
    }

    public List<Poi> recallForReplacement(Poi target, GenerateReqDTO request, int limit) {
        if (target == null || !StringUtils.hasText(target.getName())) {
            return Collections.emptyList();
        }
        String cityName = request == null ? null : request.getCityName();
        int bounded = Math.max(1, Math.min(limit, 8));

        List<GeoPoiCandidate> raw = new ArrayList<>();
        if (StringUtils.hasText(target.getCategory())) {
            safeAddCandidates(raw, geoSearchService.searchByKeyword(target.getCategory(), cityName, bounded));
        }
        if (StringUtils.hasText(target.getDistrict()) && StringUtils.hasText(target.getCategory())) {
            safeAddCandidates(raw, geoSearchService.searchByKeyword(target.getDistrict() + " " + target.getCategory(), cityName, bounded));
        }
        safeAddCandidates(raw, geoSearchService.searchByKeyword(target.getName(), cityName, bounded));

        return dedupeAndMap(raw, target.getCategory(), request, bounded);
    }

    public List<Poi> recallForReplan(List<Poi> currentPois, GenerateReqDTO request, int limit) {
        if (currentPois == null || currentPois.isEmpty()) {
            return Collections.emptyList();
        }
        int bounded = Math.max(1, Math.min(limit, 10));
        String cityName = request == null ? null : request.getCityName();
        Set<String> topCategories = currentPois.stream()
                .filter(Objects::nonNull)
                .map(Poi::getCategory)
                .filter(StringUtils::hasText)
                .collect(Collectors.toSet());
        if (topCategories.isEmpty()) {
            return Collections.emptyList();
        }

        List<GeoPoiCandidate> raw = new ArrayList<>();
        for (String category : topCategories) {
            safeAddCandidates(raw, geoSearchService.searchByKeyword(category, cityName, bounded));
        }
        String district = currentPois.stream()
                .filter(Objects::nonNull)
                .map(Poi::getDistrict)
                .filter(StringUtils::hasText)
                .findFirst()
                .orElse(null);
        if (StringUtils.hasText(district)) {
            safeAddCandidates(raw, geoSearchService.searchByKeyword(district + " 景点", cityName, bounded));
        }
        return dedupeAndMap(raw, null, request, bounded);
    }

    private List<Poi> dedupeAndMap(List<GeoPoiCandidate> raw,
                                   String fallbackCategory,
                                   GenerateReqDTO request,
                                   int limit) {
        if (raw == null || raw.isEmpty()) {
            return Collections.emptyList();
        }
        Map<String, GeoPoiCandidate> unique = new LinkedHashMap<>();
        for (GeoPoiCandidate candidate : raw) {
            if (candidate == null || !StringUtils.hasText(candidate.getName())) {
                continue;
            }
            if (candidate.getLatitude() == null || candidate.getLongitude() == null) {
                continue;
            }
            String key = (candidate.getName().trim() + "|" + candidate.getLatitude() + "|" + candidate.getLongitude())
                    .toLowerCase(Locale.ROOT);
            unique.putIfAbsent(key, candidate);
        }
        return unique.values().stream()
                .limit(limit)
                .map(candidate -> enrichBusinessDefaults(candidate, request))
                .map(candidate -> mapToPoi(candidate, fallbackCategory, request))
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private GeoPoiCandidate enrichBusinessDefaults(GeoPoiCandidate candidate, GenerateReqDTO request) {
        if (candidate == null || !StringUtils.hasText(candidate.getName())) {
            return candidate;
        }
        String cityName = StringUtils.hasText(candidate.getCityName())
                ? candidate.getCityName()
                : (request == null ? null : request.getCityName());
        try {
            List<GeoPoiCandidate> details = geoSearchService.searchByKeyword(candidate.getName().trim(), cityName, 1);
            GeoPoiCandidate detail = selectBestDetailCandidate(candidate, details);
            if (detail != null && detail != candidate) {
                mergeDetail(candidate, detail);
            }
        } catch (Exception ignored) {
            // 外部 GEO 详情补全失败时继续使用原始候选，路线生成不能因此中断。
        }
        return candidate;
    }

    private GeoPoiCandidate selectBestDetailCandidate(GeoPoiCandidate original, List<GeoPoiCandidate> details) {
        if (details == null || details.isEmpty()) {
            return null;
        }
        String originalName = normalizeKey(original == null ? null : original.getName());
        return details.stream()
                .filter(Objects::nonNull)
                .filter(item -> StringUtils.hasText(item.getName()))
                .filter(item -> normalizeKey(item.getName()).equals(originalName))
                .findFirst()
                .orElse(details.stream().filter(Objects::nonNull).findFirst().orElse(null));
    }

    private void mergeDetail(GeoPoiCandidate target, GeoPoiCandidate detail) {
        if (target == null || detail == null) {
            return;
        }
        if (StringUtils.hasText(detail.getExternalId())) {
            target.setExternalId(detail.getExternalId());
        }
        if (!StringUtils.hasText(target.getAddress()) && StringUtils.hasText(detail.getAddress())) {
            target.setAddress(detail.getAddress());
        }
        if (!StringUtils.hasText(target.getCategory()) && StringUtils.hasText(detail.getCategory())) {
            target.setCategory(detail.getCategory());
        }
        if (!StringUtils.hasText(target.getDistrict()) && StringUtils.hasText(detail.getDistrict())) {
            target.setDistrict(detail.getDistrict());
        }
        if (!StringUtils.hasText(target.getCityName()) && StringUtils.hasText(detail.getCityName())) {
            target.setCityName(detail.getCityName());
        }
        if (detail.getScore() != null) {
            target.setScore(detail.getScore());
        }
        if (StringUtils.hasText(detail.getOpeningHours())) {
            target.setOpeningHours(detail.getOpeningHours());
        }
        if (StringUtils.hasText(detail.getOpenTime())) {
            target.setOpenTime(detail.getOpenTime());
        }
        if (StringUtils.hasText(detail.getCloseTime())) {
            target.setCloseTime(detail.getCloseTime());
        }
        if (detail.getAvgCost() != null) {
            target.setAvgCost(detail.getAvgCost());
        }
        if (detail.getStayDurationMinutes() != null) {
            target.setStayDurationMinutes(detail.getStayDurationMinutes());
        }
    }

    private Poi mapToPoi(GeoPoiCandidate candidate, String fallbackCategory, GenerateReqDTO request) {
        if (candidate == null || !StringUtils.hasText(candidate.getName())) {
            return null;
        }
        String resolvedCategory = StringUtils.hasText(candidate.getCategory()) ? candidate.getCategory().trim() : fallbackCategory;
        LocalTime openTime = resolveOpenTime(candidate);
        LocalTime closeTime = resolveCloseTime(candidate);
        BigDecimal avgCost = resolveAvgCost(candidate);
        int stayDuration = resolveStayDuration(candidate);
        String categoryText = normalizeMatchingText(String.join(" ",
                nullToEmpty(candidate.getName()),
                nullToEmpty(resolvedCategory),
                nullToEmpty(candidate.getAddress())));
        boolean businessDetailsProvided = hasBusinessDetails(candidate);
        double completeness = computeDataCompleteness(candidate, resolvedCategory);
        int indoor = inferIndoor(categoryText);
        int rainFriendly = inferRainFriendly(categoryText, indoor);
        int nightAvailable = inferNightAvailability(categoryText, closeTime);
        String walkingLevel = inferWalkingLevel(categoryText);
        String suitableFor = inferSuitableFor(categoryText);
        BigDecimal priorityScore = computePriorityScore(candidate, completeness, businessDetailsProvided, indoor, rainFriendly);
        BigDecimal crowdPenalty = computeCrowdPenalty(categoryText);

        Poi poi = new Poi();
        poi.setId(buildTemporaryPoiId(candidate));
        poi.setExternalId(candidate.getExternalId());
        poi.setSourceType("external");
        poi.setCityCode(request == null ? null : request.getCityCode());
        poi.setCityName(StringUtils.hasText(candidate.getCityName()) ? candidate.getCityName() : (request == null ? null : request.getCityName()));
        poi.setName(candidate.getName().trim());
        poi.setCategory(resolvedCategory);
        poi.setDistrict(candidate.getDistrict());
        poi.setAddress(candidate.getAddress());
        poi.setLatitude(candidate.getLatitude().setScale(6, RoundingMode.HALF_UP));
        poi.setLongitude(candidate.getLongitude().setScale(6, RoundingMode.HALF_UP));
        poi.setOpenTime(openTime);
        poi.setCloseTime(closeTime);
        poi.setAvgCost(avgCost);
        poi.setStayDuration(stayDuration);
        poi.setIndoor(indoor);
        poi.setNightAvailable(nightAvailable);
        poi.setRainFriendly(rainFriendly);
        poi.setWalkingLevel(walkingLevel);
        poi.setTags(StringUtils.hasText(resolvedCategory) ? resolvedCategory : candidate.getName().trim());
        poi.setSuitableFor(suitableFor);
        poi.setDescription(buildDescription(resolvedCategory, businessDetailsProvided));
        poi.setPriorityScore(priorityScore);
        poi.setCrowdPenalty(crowdPenalty);
        poi.setTempScore(candidate.getScore() == null ? 10D : 10D + candidate.getScore() * 5D);
        poi.setOperatingStatus("CHECK_REQUIRED");
        poi.setAvailabilityNote(buildAvailabilityNote(candidate, businessDetailsProvided));
        poi.setAvailableOnTripDate(Boolean.TRUE);
        poi.setStatusStale(Boolean.TRUE);
        poi.setExternalDataCompleteness(roundDouble(completeness));
        poi.setExternalBusinessDetailsProvided(businessDetailsProvided);
        return poi;
    }

    private LocalTime resolveOpenTime(GeoPoiCandidate candidate) {
        LocalTime explicit = parseFirstTime(candidate == null ? null : candidate.getOpenTime());
        if (explicit != null) {
            return explicit;
        }
        List<LocalTime> hours = parseOpeningHours(candidate == null ? null : candidate.getOpeningHours());
        return hours.isEmpty() ? DEFAULT_OPEN_TIME : hours.get(0);
    }

    private LocalTime resolveCloseTime(GeoPoiCandidate candidate) {
        LocalTime explicit = parseFirstTime(candidate == null ? null : candidate.getCloseTime());
        if (explicit != null) {
            return explicit;
        }
        List<LocalTime> hours = parseOpeningHours(candidate == null ? null : candidate.getOpeningHours());
        return hours.size() < 2 ? DEFAULT_CLOSE_TIME : hours.get(1);
    }

    private BigDecimal resolveAvgCost(GeoPoiCandidate candidate) {
        BigDecimal value = candidate == null ? null : candidate.getAvgCost();
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0 || value.compareTo(BigDecimal.valueOf(10000)) > 0) {
            return DEFAULT_AVG_COST;
        }
        return value.setScale(0, RoundingMode.HALF_UP);
    }

    private int resolveStayDuration(GeoPoiCandidate candidate) {
        Integer minutes = candidate == null ? null : candidate.getStayDurationMinutes();
        if (minutes == null || minutes < 20 || minutes > 360) {
            return DEFAULT_STAY_DURATION_MINUTES;
        }
        return minutes;
    }

    private String buildAvailabilityNote(GeoPoiCandidate candidate, boolean businessDetailsProvided) {
        if (businessDetailsProvided) {
            return "外部 POI，营业时间、消费或停留建议已按地图详情补全，出发前请以地图实时信息为准。";
        }
        return "外部 POI，地图未返回完整营业时间或消费信息，系统已使用保守估算，出发前请以地图实时信息为准。";
    }

    private String buildDescription(String category, boolean businessDetailsProvided) {
        StringBuilder builder = new StringBuilder("External candidate from GEO API");
        if (StringUtils.hasText(category)) {
            builder.append(" - ").append(category.trim());
        }
        if (businessDetailsProvided) {
            builder.append(" with enriched business details");
        }
        return builder.toString();
    }

    private boolean hasBusinessDetails(GeoPoiCandidate candidate) {
        return candidate != null
                && (StringUtils.hasText(candidate.getOpeningHours())
                || StringUtils.hasText(candidate.getOpenTime())
                || StringUtils.hasText(candidate.getCloseTime())
                || candidate.getAvgCost() != null
                || candidate.getStayDurationMinutes() != null);
    }

    private double computeDataCompleteness(GeoPoiCandidate candidate, String resolvedCategory) {
        if (candidate == null) {
            return 0D;
        }
        int totalSignals = 7;
        int presentSignals = 0;
        if (StringUtils.hasText(candidate.getExternalId())) {
            presentSignals++;
        }
        if (StringUtils.hasText(candidate.getAddress())) {
            presentSignals++;
        }
        if (StringUtils.hasText(candidate.getDistrict())) {
            presentSignals++;
        }
        if (StringUtils.hasText(resolvedCategory)) {
            presentSignals++;
        }
        if (StringUtils.hasText(candidate.getOpeningHours()) || StringUtils.hasText(candidate.getOpenTime()) || StringUtils.hasText(candidate.getCloseTime())) {
            presentSignals++;
        }
        if (candidate.getAvgCost() != null) {
            presentSignals++;
        }
        if (candidate.getStayDurationMinutes() != null) {
            presentSignals++;
        }
        return Math.max(0D, Math.min(1D, presentSignals / (double) totalSignals));
    }

    private int inferIndoor(String categoryText) {
        return hasAnyKeyword(categoryText, INDOOR_KEYWORDS) ? 1 : 0;
    }

    private int inferRainFriendly(String categoryText, int indoor) {
        return indoor == 1 || hasAnyKeyword(categoryText, RAIN_FRIENDLY_KEYWORDS) ? 1 : 0;
    }

    private int inferNightAvailability(String categoryText, LocalTime closeTime) {
        if (closeTime != null && !closeTime.isBefore(LocalTime.of(20, 0))) {
            return 1;
        }
        return hasAnyKeyword(categoryText, NIGHT_FRIENDLY_KEYWORDS) ? 1 : 0;
    }

    private String inferWalkingLevel(String categoryText) {
        if (hasAnyKeyword(categoryText, HIGH_WALKING_KEYWORDS)) {
            return "high";
        }
        if (hasAnyKeyword(categoryText, LOW_WALKING_KEYWORDS)) {
            return "low";
        }
        return "medium";
    }

    private String inferSuitableFor(String categoryText) {
        List<String> audience = new ArrayList<>(List.of("friends", "solo"));
        if (hasAnyKeyword(categoryText, FAMILY_FRIENDLY_KEYWORDS)) {
            audience.add("family");
        }
        if (hasAnyKeyword(categoryText, COUPLE_FRIENDLY_KEYWORDS)) {
            audience.add("couple");
        }
        if (!audience.contains("couple")) {
            audience.add("couple");
        }
        return audience.stream().distinct().collect(Collectors.joining(","));
    }

    private BigDecimal computePriorityScore(GeoPoiCandidate candidate,
                                            double completeness,
                                            boolean businessDetailsProvided,
                                            int indoor,
                                            int rainFriendly) {
        double providerScore = candidate == null || candidate.getScore() == null
                ? 0D
                : Math.max(0D, Math.min(candidate.getScore(), 5D));
        double priority = 5.0D
                + completeness * 1.2D
                + (businessDetailsProvided ? 0.5D : 0D)
                + (providerScore / 5D) * 0.6D
                + (indoor == 1 ? 0.2D : 0D)
                + (rainFriendly == 1 ? 0.2D : 0D);
        return BigDecimal.valueOf(priority).setScale(1, RoundingMode.HALF_UP);
    }

    private BigDecimal computeCrowdPenalty(String categoryText) {
        double penalty;
        if (hasAnyKeyword(categoryText, List.of("mall", "shopping", "night market", "food", "商场", "购物", "夜市", "美食"))) {
            penalty = 0.18D;
        } else if (hasAnyKeyword(categoryText, List.of("museum", "gallery", "aquarium", "博物馆", "美术馆", "海洋馆"))) {
            penalty = 0.10D;
        } else if (hasAnyKeyword(categoryText, HIGH_WALKING_KEYWORDS)) {
            penalty = 0.08D;
        } else {
            penalty = 0.12D;
        }
        return BigDecimal.valueOf(penalty).setScale(2, RoundingMode.HALF_UP);
    }

    private List<LocalTime> parseOpeningHours(String text) {
        if (!StringUtils.hasText(text)) {
            return Collections.emptyList();
        }
        List<LocalTime> times = new ArrayList<>();
        String normalized = text.trim().replace('：', ':');
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("(\\d{1,2}):(\\d{2})").matcher(normalized);
        while (matcher.find() && times.size() < 2) {
            LocalTime parsed = parseHourMinute(matcher.group(1), matcher.group(2));
            if (parsed != null) {
                times.add(parsed);
            }
        }
        return times;
    }

    private LocalTime parseFirstTime(String text) {
        List<LocalTime> times = parseOpeningHours(text);
        return times.isEmpty() ? null : times.get(0);
    }

    private LocalTime parseHourMinute(String hourText, String minuteText) {
        try {
            int hour = Integer.parseInt(hourText);
            int minute = Integer.parseInt(minuteText);
            if (hour == 24 && minute == 0) {
                return LocalTime.of(23, 59);
            }
            if (hour < 0 || hour > 23 || minute < 0 || minute > 59) {
                return null;
            }
            return LocalTime.of(hour, minute);
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private boolean hasAnyKeyword(String normalizedText, List<String> keywords) {
        if (!StringUtils.hasText(normalizedText) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        String haystack = normalizeMatchingText(normalizedText);
        for (String keyword : keywords) {
            String needle = normalizeMatchingText(keyword);
            if (StringUtils.hasText(needle) && haystack.contains(needle)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMatchingText(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String normalizeKey(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private double roundDouble(double value) {
        return BigDecimal.valueOf(value).setScale(2, RoundingMode.HALF_UP).doubleValue();
    }

    private long buildTemporaryPoiId(GeoPoiCandidate candidate) {
        String seed = (candidate.getName() == null ? "" : candidate.getName())
                + "|"
                + (candidate.getLatitude() == null ? "" : candidate.getLatitude().toPlainString())
                + "|"
                + (candidate.getLongitude() == null ? "" : candidate.getLongitude().toPlainString());
        long hash = Integer.toUnsignedLong(seed.hashCode());
        return -(10_000_000L + hash);
    }

    private void safeAddCandidates(List<GeoPoiCandidate> container, List<GeoPoiCandidate> incoming) {
        if (container == null || incoming == null || incoming.isEmpty()) {
            return;
        }
        container.addAll(incoming);
    }
}
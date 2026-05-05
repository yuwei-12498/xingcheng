package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.RoutePathPointVO;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import com.citytrip.service.geo.GeoPoint;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Locale;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class RouteAnalysisService {

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

    private final TravelTimeService travelTimeService;
    private final ItineraryRouteOptimizer routeOptimizer;
    private final SegmentRouteGuideService segmentRouteGuideService;

    public RouteAnalysisService(TravelTimeService travelTimeService,
                                ItineraryRouteOptimizer routeOptimizer,
                                SegmentRouteGuideService segmentRouteGuideService) {
        this.travelTimeService = travelTimeService;
        this.routeOptimizer = routeOptimizer;
        this.segmentRouteGuideService = segmentRouteGuideService;
    }

    public RouteAnalysis analyzeRoute(ItineraryRouteOptimizer.RouteOption route,
                                      GenerateReqDTO req,
                                      Map<Long, String> existingReasons) {
        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(req);
        if (route == null || route.path() == null || route.path().isEmpty()) {
            return new RouteAnalysis(
                    route,
                    Collections.emptyList(),
                    0,
                    BigDecimal.ZERO,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    0,
                    Collections.emptyList()
            );
        }
        Map<Long, String> reasonOverrides = existingReasons == null ? Collections.emptyMap() : existingReasons;

        int startMinute = routeOptimizer.parseTimeMinutes(normalized.getStartTime(), ItineraryRouteOptimizer.DEFAULT_START_MINUTE);
        int currentMinute = startMinute;
        int dayNo = 1;
        int dayStepOrder = 0;
        int totalTravelTime = 0;
        int totalWaitTime = 0;
        int themeMatchCount = 0;
        int companionMatchCount = 0;
        int nightFriendlyCount = 0;
        int indoorFriendlyCount = 0;
        int businessRiskScore = 0;
        Set<String> districts = new LinkedHashSet<>();
        BigDecimal totalCost = BigDecimal.ZERO;
        List<ItineraryNodeVO> nodes = new ArrayList<>();
        Poi departurePoi = routeOptimizer.buildDeparturePoi(normalized);
        Poi prev = null;
        Set<Integer> dayStartIndexes = resolveDayStartIndexes(route);

        for (int index = 0; index < route.path().size(); index++) {
            if (index > 0 && dayStartIndexes.contains(index)) {
                dayNo++;
                dayStepOrder = 0;
                prev = null;
                currentMinute = safeAddInt(safeDayOffset(dayNo - 1), startMinute);
            }

            Poi poi = route.path().get(index);
            if (poi == null) {
                continue;
            }
            int dayOffset = safeDayOffset(dayNo - 1);
            int stay = normalizeStayDuration(poi.getStayDuration());
            TravelTimeService.TravelLegEstimate legEstimate = prev == null
                    ? (departurePoi == null ? new TravelTimeService.TravelLegEstimate(0, BigDecimal.ZERO, "步行")
                    : travelTimeService.estimateTravelLeg(departurePoi, poi))
                    : travelTimeService.estimateTravelLeg(prev, poi);
            if (legEstimate == null) {
                int fallbackMinutes = prev == null
                        ? (departurePoi == null ? 0 : travelTimeService.estimateTravelTimeMinutes(departurePoi, poi))
                        : travelTimeService.estimateTravelTimeMinutes(prev, poi);
                legEstimate = new TravelTimeService.TravelLegEstimate(Math.max(fallbackMinutes, 0), null, null);
            }
            int travel = Math.max(0, legEstimate == null ? 0 : legEstimate.estimatedMinutes());
            int arrival = safeAddInt(currentMinute, travel);
            int visitStart = Math.max(arrival, dayOffset + routeOptimizer.resolveOpenMinute(poi, startMinute));
            int wait = Math.max(0, visitStart - arrival);

            ItineraryNodeVO node = new ItineraryNodeVO();
            node.setDayNo(dayNo);
            node.setStepOrder(++dayStepOrder);
            node.setPoiId(poi.getId());
            node.setPoiName(poi.getName());
            node.setCategory(poi.getCategory());
            node.setDistrict(poi.getDistrict());
            node.setAddress(poi.getAddress());
            node.setLatitude(poi.getLatitude());
            node.setLongitude(poi.getLongitude());
            node.setTravelTime(travel);
            if (legEstimate != null && StringUtils.hasText(legEstimate.transportMode())) {
                node.setTravelTransportMode(legEstimate.transportMode().trim());
            }
            if (legEstimate != null && legEstimate.estimatedDistanceKm() != null) {
                node.setTravelDistanceKm(legEstimate.estimatedDistanceKm().setScale(1, RoundingMode.HALF_UP));
            }
            node.setRoutePathPoints(List.of());
            node.setSegmentRouteGuide(null);
            node.setStayDuration(stay);
            node.setCost(poi.getAvgCost() == null ? BigDecimal.ZERO : poi.getAvgCost());
            node.setStartTime(routeOptimizer.formatTime(visitStart));
            node.setEndTime(routeOptimizer.formatTime(safeAddInt(visitStart, stay)));
            node.setSourceType(StringUtils.hasText(poi.getSourceType()) ? poi.getSourceType() : "local");
            node.setOperatingStatus(poi.getOperatingStatus());
            node.setStatusUpdatedAt(poi.getStatusUpdatedAt());
            node.setStatusNote(buildStatusNote(poi, wait));
            node.setScoreBreakdown(copyScoreBreakdown(poi));
            boolean dayFirstStop = prev == null;
            boolean dayLastStop = index == route.path().size() - 1 || dayStartIndexes.contains(index + 1);
            String fallbackReason = buildNodeReason(normalized, poi, travel, wait, dayFirstStop, dayLastStop);
            Long reasonPoiId = node.getPoiId();
            node.setSysReason(reasonPoiId == null
                    ? fallbackReason
                    : reasonOverrides.getOrDefault(reasonPoiId, fallbackReason));

            if (node.getStepOrder() != null && node.getStepOrder() == 1) {
                node.setDepartureTravelTime(travel);
                node.setDepartureTransportMode(node.getTravelTransportMode());
                node.setDepartureDistanceKm(node.getTravelDistanceKm());
            }

            totalCost = totalCost.add(node.getCost());
            totalTravelTime = safeAddInt(totalTravelTime, travel);
            totalWaitTime = safeAddInt(totalWaitTime, wait);
            themeMatchCount += matchThemes(normalized, poi).size();
            if (matchesCompanion(normalized, poi)) {
                companionMatchCount++;
            }
            if (Boolean.TRUE.equals(normalized.getIsNight()) && Integer.valueOf(1).equals(poi.getNightAvailable())) {
                nightFriendlyCount++;
            }
            if (Boolean.TRUE.equals(normalized.getIsRainy())
                    && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly()))) {
                indoorFriendlyCount++;
            }
            if (Boolean.TRUE.equals(poi.getStatusStale())) {
                businessRiskScore += 2;
            }
            if (poi.getOpenTime() == null || poi.getCloseTime() == null) {
                businessRiskScore += 2;
            }
            if ("UNKNOWN".equalsIgnoreCase(poi.getOperatingStatus())) {
                businessRiskScore += 1;
            }
            if (StringUtils.hasText(poi.getDistrict())) {
                districts.add(poi.getDistrict());
            }

            nodes.add(node);
            currentMinute = safeAddInt(visitStart, stay);
            prev = poi;
        }

        List<String> alerts = buildRouteAlerts(
                normalized,
                totalWaitTime,
                nightFriendlyCount,
                indoorFriendlyCount,
                nodes.size()
        );
        return new RouteAnalysis(
                route,
                nodes,
                safeDuration(currentMinute, startMinute),
                totalCost,
                totalTravelTime,
                totalWaitTime,
                themeMatchCount,
                companionMatchCount,
                nightFriendlyCount,
                indoorFriendlyCount,
                businessRiskScore,
                districts.size(),
                alerts
        );
    }

    private Set<Integer> resolveDayStartIndexes(ItineraryRouteOptimizer.RouteOption route) {
        if (route == null || !StringUtils.hasText(route.signature()) || !route.signature().contains("|")) {
            return Collections.emptySet();
        }
        String[] daySegments = route.signature().split("\\|");
        if (daySegments.length <= 1) {
            return Collections.emptySet();
        }
        Set<Integer> startIndexes = new HashSet<>();
        int cursor = 0;
        for (int i = 0; i < daySegments.length - 1; i++) {
            String segment = daySegments[i];
            if (!StringUtils.hasText(segment)) {
                continue;
            }
            int count = safeLongToInt(java.util.Arrays.stream(segment.split("-"))
                    .filter(StringUtils::hasText)
                    .count());
            cursor = safeAddInt(cursor, count);
            if (cursor > 0 && cursor < route.path().size()) {
                startIndexes.add(cursor);
            }
        }
        return startIndexes;
    }

    private List<String> buildRouteAlerts(GenerateReqDTO req,
                                          int totalWaitTime,
                                          int nightFriendlyCount,
                                          int indoorFriendlyCount,
                                          int totalStops) {
        List<String> alerts = new ArrayList<>();
        if (totalWaitTime >= 25) {
            alerts.add("该方案包含一定等待时间，建议稍晚出发或提前预约。");
        }
        if (Boolean.TRUE.equals(req.getIsRainy()) && indoorFriendlyCount < Math.max(1, totalStops / 2)) {
            alerts.add("当前为雨天偏好，但这条路线仍包含一定比例的室外点位。");
        }
        if (Boolean.TRUE.equals(req.getIsNight()) && nightFriendlyCount == 0) {
            alerts.add("你开启了夜游偏好，但这条路线的夜间亮点相对有限。");
        }
        return alerts;
    }

    private String buildStatusNote(Poi poi, int waitMinutes) {
        List<String> notes = new ArrayList<>();
        if (waitMinutes > 0) {
            notes.add("到达后需等待 " + waitMinutes + " 分钟才能开始游览。");
        }
        String poiSpecificNote = buildPoiSpecificNote(poi);
        if (StringUtils.hasText(poiSpecificNote)) {
            notes.add(poiSpecificNote);
        }
        if (StringUtils.hasText(poi.getAvailabilityNote()) && !isLegacyBusinessHint(poi.getAvailabilityNote())) {
            notes.add(poi.getAvailabilityNote().trim());
        }
        return notes.isEmpty() ? null : String.join(" ", notes);
    }

    private String buildPoiSpecificNote(Poi poi) {
        if (poi == null) {
            return null;
        }

        String name = defaultString(poi.getName());
        String category = defaultString(poi.getCategory());
        String district = defaultString(poi.getDistrict());
        String address = defaultString(poi.getAddress());
        String merged = (name + " " + category + " " + district + " " + address).trim();
        String lowerMerged = merged.toLowerCase(Locale.ROOT);

        if (containsAny(lowerMerged, "ifs", "国金中心", "太古里", "春熙路", "skp")) {
            return "位于市区商圈，入口和连廊较多，建议按主入口或具体楼层导航，避免走错方向。";
        }
        if (containsAny(merged, "青城山", "峨眉山", "山", "栈道", "步道", "徒步")
                || containsAny(lowerMerged, "mountain", "hiking", "trail")) {
            return "山路和台阶较多，建议穿防滑鞋，注意补水并预留返程体力。";
        }
        if (containsAny(merged, "博物馆", "美术馆", "纪念馆", "展览馆", "艺术馆")) {
            return "热门场馆可能需要排队或核验预约，建议预留一点入场时间。";
        }
        if (containsAny(merged, "古镇", "老街", "步行街", "宽窄巷子", "锦里")) {
            return "高峰时段人流较密集，建议照看好随身物品，并提前约好集合点。";
        }
        if (containsAny(merged, "寺", "祠", "宫", "观")) {
            return "参观时注意台阶和礼仪提示，避开集中高峰会更从容。";
        }
        return null;
    }

    private String buildNodeReason(GenerateReqDTO req,
                                   Poi poi,
                                   int travelMinutes,
                                   int waitMinutes,
                                   boolean firstStop,
                                   boolean lastStop) {
        List<String> reasons = new ArrayList<>();
        String scoreReason = buildScoreBreakdownReason(poi);
        if (StringUtils.hasText(scoreReason)) {
            reasons.add(scoreReason);
        }
        List<String> matchedThemes = matchThemes(req, poi);
        if (!matchedThemes.isEmpty()) {
            reasons.add("主题匹配：" + String.join("、", matchedThemes));
        }
        if (matchesCompanion(req, poi)) {
            reasons.add("更适合同伴类型和游玩节奏");
        }
        if (Boolean.TRUE.equals(req.getIsRainy())
                && (Integer.valueOf(1).equals(poi.getIndoor()) || Integer.valueOf(1).equals(poi.getRainFriendly()))) {
            reasons.add("雨天可执行性更强");
        }
        if (Boolean.TRUE.equals(req.getIsNight()) && Integer.valueOf(1).equals(poi.getNightAvailable())) {
            reasons.add("夜间体验更完整");
        }
        if (firstStop) {
            reasons.add("适合作为路线起点，开场切换成本更低");
        } else if (travelMinutes <= 15) {
            reasons.add("与前一站距离近，顺路衔接更好");
        } else if (travelMinutes <= 30) {
            reasons.add("与前一站过渡仍在可接受范围");
        }
        if (waitMinutes > 0 && waitMinutes <= 20) {
            reasons.add("等待时间可控，不会明显压缩后续行程");
        }
        if (lastStop) {
            reasons.add("适合作为收尾点位，便于返程或继续夜游");
        }
        if (highPriority(poi)) {
            reasons.add("综合热度和优先级都较高");
        }
        if (reasons.isEmpty()) {
            reasons.add("在当前时间窗和路线顺序下具有较好的可执行性");
        }
        return reasons.stream().limit(3).collect(Collectors.joining("；"));
    }

    private Map<String, Double> copyScoreBreakdown(Poi poi) {
        if (poi == null || poi.getTempScoreBreakdown() == null || poi.getTempScoreBreakdown().isEmpty()) {
            return Collections.emptyMap();
        }
        return new LinkedHashMap<>(poi.getTempScoreBreakdown());
    }

    private String buildScoreBreakdownReason(Poi poi) {
        if (poi == null || poi.getTempScoreBreakdown() == null || poi.getTempScoreBreakdown().isEmpty()) {
            return null;
        }
        List<String> reasons = poi.getTempScoreBreakdown().entrySet().stream()
                .filter(entry -> entry.getValue() != null && entry.getValue() > 0.2D)
                .sorted(Map.Entry.<String, Double>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> mapScoreComponentLabel(entry.getKey()))
                .filter(StringUtils::hasText)
                .distinct()
                .limit(2)
                .toList();
        if (reasons.isEmpty()) {
            return null;
        }
        return String.join("；", reasons);
    }

    private String mapScoreComponentLabel(String componentKey) {
        if (!StringUtils.hasText(componentKey)) {
            return null;
        }
        return switch (componentKey) {
            case "mustVisit" -> "满足必去点要求";
            case "theme" -> "主题匹配度高";
            case "budget" -> "预算匹配更合适";
            case "rain" -> "雨天可执行性更强";
            case "walking" -> "步行压力更低";
            case "night" -> "夜间体验更完整";
            case "companion", "groupFit" -> "更适合同伴类型";
            case "externalRealtime", "externalDataCompleteness", "externalBusinessDetails" -> "外部实时信息更完整";
            case "personalizedRecall" -> "更贴近你的历史兴趣";
            case "priority" -> "综合热度较高";
            default -> null;
        };
    }

    private List<String> matchThemes(GenerateReqDTO req, Poi poi) {
        if (req == null || req.getThemes() == null || !StringUtils.hasText(poi.getTags())) {
            return Collections.emptyList();
        }
        return req.getThemes().stream()
                .filter(StringUtils::hasText)
                .filter(theme -> poi.getTags().contains(theme))
                .toList();
    }

    private boolean matchesCompanion(GenerateReqDTO req, Poi poi) {
        if (req == null || poi == null || !StringUtils.hasText(req.getCompanionType())) {
            return false;
        }
        String companionType = normalizeMatchingText(req.getCompanionType());
        String audienceText = normalizeMatchingText(String.join(" ",
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
        String companionType = normalizeMatchingText(req.getCompanionType());
        if (containsAnyNormalized(companionType, SOLO_COMPANION_KEYWORDS)) {
            return false;
        }
        return containsAnyNormalized(companionType, GROUP_COMPANION_KEYWORDS);
    }

    private boolean containsAnyNormalized(String normalizedText, List<String> keywords) {
        if (!StringUtils.hasText(normalizedText) || keywords == null || keywords.isEmpty()) {
            return false;
        }
        for (String keyword : keywords) {
            String normalizedKeyword = normalizeMatchingText(keyword);
            if (StringUtils.hasText(normalizedKeyword) && normalizedText.contains(normalizedKeyword)) {
                return true;
            }
        }
        return false;
    }

    private String normalizeMatchingText(String value) {
        return StringUtils.hasText(value) ? value.trim().toLowerCase(Locale.ROOT) : "";
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }

    private boolean highPriority(Poi poi) {
        return poi.getPriorityScore() != null && poi.getPriorityScore().doubleValue() >= 4.0D;
    }

    private boolean containsAny(String text, String... keywords) {
        if (!StringUtils.hasText(text) || keywords == null || keywords.length == 0) {
            return false;
        }
        for (String keyword : keywords) {
            if (StringUtils.hasText(keyword) && text.contains(keyword)) {
                return true;
            }
        }
        return false;
    }

    private String defaultString(String value) {
        return value == null ? "" : value;
    }

    private int safeDayOffset(int dayIndex) {
        if (dayIndex <= 0) {
            return 0;
        }
        long offset = dayIndex * 24L * 60L;
        return safeLongToInt(offset);
    }

    private int safeAddInt(int left, int right) {
        long result = (long) left + right;
        return safeLongToInt(result);
    }

    private int safeLongToInt(long value) {
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (value < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) value;
    }

    private int safeDuration(int end, int start) {
        long duration = (long) end - start;
        return safeLongToInt(Math.max(duration, 0L));
    }

    private int normalizeStayDuration(Integer stayDuration) {
        if (stayDuration == null) {
            return 90;
        }
        return Math.max(stayDuration, 0);
    }

    private boolean isLegacyBusinessHint(String text) {
        if (!StringUtils.hasText(text)) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        return normalized.contains("营业状态更新时间较久")
                || normalized.contains("当前场馆状态超过14天未核验")
                || normalized.contains("营业时间信息不完整")
                || normalized.contains("缺少完整营业时间信息");
    }

    private List<RoutePathPointVO> toRoutePathPoints(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.stream()
                .map(this::toRoutePathPoint)
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private RoutePathPointVO toRoutePathPoint(GeoPoint point) {
        if (point == null || !point.valid()) {
            return null;
        }
        RoutePathPointVO routePathPoint = new RoutePathPointVO();
        routePathPoint.setLatitude(point.latitude());
        routePathPoint.setLongitude(point.longitude());
        return routePathPoint;
    }

    public record RouteAnalysis(ItineraryRouteOptimizer.RouteOption route,
                                List<ItineraryNodeVO> nodes,
                                int totalDuration,
                                BigDecimal totalCost,
                                int totalTravelTime,
                                int totalWaitTime,
                                int themeMatchCount,
                                int companionMatchCount,
                                int nightFriendlyCount,
                                int indoorFriendlyCount,
                                int businessRiskScore,
                                int uniqueDistrictCount,
                                List<String> alerts) {

        public int stopCount() {
            return nodes == null ? 0 : nodes.size();
        }

        public double utility() {
            return route == null ? 0D : route.utility();
        }
    }
}

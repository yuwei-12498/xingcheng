package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
public class ChatFirstLegEtaSkillService {

    private static final Set<String> FIRST_LEG_KEYWORDS = Set.of(
            "第一站", "首站", "首段", "第一段", "从我这", "当前位置", "现在出发", "到第一个", "到第一站", "先去哪里"
    );

    private static final List<ModeProfile> MODE_PROFILES = List.of(
            new ModeProfile("walk", "步行"),
            new ModeProfile("bus", "公交+步行"),
            new ModeProfile("metro", "地铁+步行"),
            new ModeProfile("taxi", "打车")
    );

    private static final int MAX_MODE_OPTION_COUNT = 4;
    private static final ZoneId DEFAULT_ZONE = ZoneId.of("Asia/Shanghai");
    private static final List<DateTimeFormatter> START_TIME_FORMATTERS = List.of(
            DateTimeFormatter.ofPattern("H:mm"),
            DateTimeFormatter.ofPattern("HH:mm"),
            DateTimeFormatter.ofPattern("H:mm:ss"),
            DateTimeFormatter.ofPattern("HH:mm:ss")
    );

    private final GeoSearchService geoSearchService;

    public ChatFirstLegEtaSkillService(GeoSearchService geoSearchService) {
        this.geoSearchService = geoSearchService;
    }

    public boolean isFirstLegIntent(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        for (String keyword : FIRST_LEG_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return normalized.contains("first stop")
                || normalized.contains("first leg")
                || normalized.contains("first station")
                || normalized.contains("from here to first")
                || normalized.contains("how long to first");
    }

    public FirstLegEstimate estimate(ChatReqDTO req, ChatRouteContextSkillService.RouteContext routeContext) {
        if (routeContext == null || !routeContext.available()) {
            return null;
        }
        ChatReqDTO.ChatRouteNode first = routeContext.firstNode();
        if (first == null) {
            return null;
        }
        return compute(req, first).recommended();
    }

    public SkillResult tryHandle(ChatReqDTO req, ChatRouteContextSkillService.RouteContext routeContext) {
        if (!isFirstLegIntent(req == null ? null : req.getQuestion())) {
            return null;
        }
        if (routeContext == null || !routeContext.available()) {
            return new SkillResult(
                    "我还没有拿到当前路线，先生成路线后我就能按“当前位置→第一站”给你精算通行时间。",
                    null,
                    Set.of("FirstLegPreciseETASkill")
            );
        }

        ChatReqDTO.ChatRouteNode first = routeContext.firstNode();
        FirstLegComputation computation = compute(req, first);
        FirstLegEstimate estimate = computation.recommended();
        if (estimate == null) {
            return new SkillResult(
                    "已识别到第一站是「" + first.getPoiName() + "」，但缺少可用定位或路线参数，暂时无法精算首段通行时间。",
                    null,
                    Set.of("FirstLegPreciseETASkill")
            );
        }

        String answer = buildAnswer(first.getPoiName(), computation);
        return new SkillResult(
                answer,
                estimate.source(),
                Set.of("FirstLegPreciseETASkill")
        );
    }

    public List<String> buildEvidence(ChatReqDTO req,
                                      ChatRouteContextSkillService.RouteContext routeContext) {
        if (routeContext == null || !routeContext.available()) {
            return List.of();
        }
        ChatReqDTO.ChatRouteNode first = routeContext.firstNode();
        if (first == null || !StringUtils.hasText(first.getPoiName())) {
            return List.of();
        }

        FirstLegComputation computation = compute(req, first);
        List<String> evidence = new ArrayList<>();
        evidence.add("first_stop=" + first.getPoiName().trim());
        if (computation.timeWindowMinutes() != null && computation.timeWindowMinutes() > 0) {
            evidence.add("first_leg_time_window_min=" + computation.timeWindowMinutes());
        }

        List<ModeCandidate> modeCandidates = computation.modeCandidates();
        for (int i = 0; i < modeCandidates.size() && i < MAX_MODE_OPTION_COUNT; i++) {
            ModeCandidate item = modeCandidates.get(i);
            evidence.add("first_leg_option_" + (i + 1) + "=" + formatCandidate(item));
        }

        FirstLegEstimate recommended = computation.recommended();
        if (recommended != null) {
            evidence.add("first_leg_recommend=" + formatEstimate(recommended));
        }
        return evidence;
    }

    private FirstLegComputation compute(ChatReqDTO req, ChatReqDTO.ChatRouteNode first) {
        Integer timeWindowMinutes = resolveTimeWindowMinutes(first);

        List<ModeCandidate> modeCandidates = estimateByGeoRouteModes(req, first);
        if (!modeCandidates.isEmpty()) {
            ModeCandidate recommended = pickBestMode(modeCandidates, timeWindowMinutes);
            if (recommended != null) {
                FirstLegEstimate estimate = new FirstLegEstimate(
                        recommended.minutes(),
                        normalizeDistance(recommended.distanceKm()),
                        recommended.displayMode(),
                        recommended.source()
                );
                return new FirstLegComputation(estimate, timeWindowMinutes, modeCandidates);
            }
        }

        FirstLegEstimate fallback = estimateFromRouteNode(first);
        if (fallback == null) {
            return new FirstLegComputation(null, timeWindowMinutes, List.of());
        }

        ModeCandidate fallbackCandidate = new ModeCandidate(
                fallback.transportMode(),
                fallback.estimatedMinutes(),
                fallback.estimatedDistanceKm(),
                fallback.source()
        );
        return new FirstLegComputation(fallback, timeWindowMinutes, List.of(fallbackCandidate));
    }

    private List<ModeCandidate> estimateByGeoRouteModes(ChatReqDTO req, ChatReqDTO.ChatRouteNode first) {
        if (req == null
                || req.getContext() == null
                || req.getContext().getUserLat() == null
                || req.getContext().getUserLng() == null
                || first == null
                || first.getLatitude() == null
                || first.getLongitude() == null) {
            return List.of();
        }

        double userLat = req.getContext().getUserLat();
        double userLng = req.getContext().getUserLng();
        if (!Double.isFinite(userLat) || !Double.isFinite(userLng)) {
            return List.of();
        }

        GeoPoint from = new GeoPoint(
                BigDecimal.valueOf(userLat),
                BigDecimal.valueOf(userLng)
        );
        GeoPoint to = new GeoPoint(first.getLatitude(), first.getLongitude());
        if (!from.valid() || !to.valid()) {
            return List.of();
        }

        String cityName = req.getContext().getCityName();
        Map<String, ModeCandidate> unique = new LinkedHashMap<>();

        for (ModeProfile profile : MODE_PROFILES) {
            GeoRouteEstimate route = geoSearchService
                    .estimateTravel(from, to, cityName, profile.routeMode())
                    .orElse(null);
            ModeCandidate candidate = toModeCandidate(route, profile.displayMode(), "geo-route-api");
            mergeCandidate(unique, candidate);
        }

        if (unique.isEmpty()) {
            GeoRouteEstimate fallback = geoSearchService
                    .estimateTravel(from, to, cityName, null)
                    .orElse(null);
            ModeCandidate candidate = toModeCandidate(fallback, null, "geo-route-api");
            mergeCandidate(unique, candidate);
        }

        if (unique.isEmpty()) {
            return List.of();
        }

        List<ModeCandidate> result = new ArrayList<>(unique.values());
        result.sort(this::compareCandidate);
        return result.size() > MAX_MODE_OPTION_COUNT ? result.subList(0, MAX_MODE_OPTION_COUNT) : result;
    }

    private void mergeCandidate(Map<String, ModeCandidate> unique, ModeCandidate incoming) {
        if (incoming == null || !StringUtils.hasText(incoming.displayMode())) {
            return;
        }
        String key = incoming.displayMode().trim();
        ModeCandidate existing = unique.get(key);
        if (existing == null || compareCandidate(incoming, existing) < 0) {
            unique.put(key, incoming);
        }
    }

    private ModeCandidate toModeCandidate(GeoRouteEstimate route,
                                          String fallbackDisplayMode,
                                          String source) {
        if (route == null) {
            return null;
        }
        Integer minutes = route.durationMinutes() != null && route.durationMinutes() > 0
                ? route.durationMinutes()
                : null;
        BigDecimal distance = normalizeDistance(route.distanceKm());
        if (minutes == null && distance == null) {
            return null;
        }

        String mode = normalizeTransportMode(route.transportMode(), minutes, distance);
        if (!StringUtils.hasText(mode)) {
            mode = fallbackDisplayMode;
        }
        if (!StringUtils.hasText(mode)) {
            return null;
        }

        return new ModeCandidate(mode.trim(), minutes, distance, source);
    }

    private ModeCandidate pickBestMode(List<ModeCandidate> candidates, Integer timeWindowMinutes) {
        if (candidates == null || candidates.isEmpty()) {
            return null;
        }

        List<ModeCandidate> sorted = new ArrayList<>(candidates);
        sorted.sort(this::compareCandidate);

        if (timeWindowMinutes != null && timeWindowMinutes > 0) {
            for (ModeCandidate item : sorted) {
                if (item.minutes() != null && item.minutes() > 0 && item.minutes() <= timeWindowMinutes) {
                    return item;
                }
            }
        }
        return sorted.get(0);
    }

    private int compareCandidate(ModeCandidate left, ModeCandidate right) {
        int byMinutes = compareNullableInteger(left == null ? null : left.minutes(), right == null ? null : right.minutes());
        if (byMinutes != 0) {
            return byMinutes;
        }

        int byDistance = compareNullableDecimal(left == null ? null : left.distanceKm(), right == null ? null : right.distanceKm());
        if (byDistance != 0) {
            return byDistance;
        }

        String leftMode = left == null ? null : left.displayMode();
        String rightMode = right == null ? null : right.displayMode();
        return compareNullableString(leftMode, rightMode);
    }

    private int compareNullableInteger(Integer left, Integer right) {
        int l = (left == null || left <= 0) ? Integer.MAX_VALUE : left;
        int r = (right == null || right <= 0) ? Integer.MAX_VALUE : right;
        return Integer.compare(l, r);
    }

    private int compareNullableDecimal(BigDecimal left, BigDecimal right) {
        if (left == null && right == null) {
            return 0;
        }
        if (left == null) {
            return 1;
        }
        if (right == null) {
            return -1;
        }
        return left.compareTo(right);
    }

    private int compareNullableString(String left, String right) {
        if (!StringUtils.hasText(left) && !StringUtils.hasText(right)) {
            return 0;
        }
        if (!StringUtils.hasText(left)) {
            return 1;
        }
        if (!StringUtils.hasText(right)) {
            return -1;
        }
        return left.compareTo(right);
    }

    private Integer resolveTimeWindowMinutes(ChatReqDTO.ChatRouteNode first) {
        if (first == null || !StringUtils.hasText(first.getStartTime())) {
            return null;
        }

        LocalTime startTime = parseStartTime(first.getStartTime());
        if (startTime == null) {
            return null;
        }

        LocalTime now = LocalTime.now(DEFAULT_ZONE);
        long diffMinutes = Duration.between(now, startTime).toMinutes();

        if (diffMinutes <= -360) {
            diffMinutes += 24L * 60L;
        }
        if (diffMinutes <= 0) {
            return null;
        }
        return (int) diffMinutes;
    }

    private LocalTime parseStartTime(String raw) {
        if (!StringUtils.hasText(raw)) {
            return null;
        }
        String text = raw.trim();
        for (DateTimeFormatter formatter : START_TIME_FORMATTERS) {
            try {
                return LocalTime.parse(text, formatter);
            } catch (DateTimeParseException ignored) {
                // try next
            }
        }
        return null;
    }

    private String buildAnswer(String firstPoiName, FirstLegComputation computation) {
        String targetName = StringUtils.hasText(firstPoiName) ? firstPoiName.trim() : "第一站";
        FirstLegEstimate recommended = computation.recommended();

        StringBuilder answer = new StringBuilder();
        answer.append("从当前位置到第一站「").append(targetName).append("」已按实时路况细算");
        if (computation.timeWindowMinutes() != null && computation.timeWindowMinutes() > 0) {
            answer.append("（时间窗约 ").append(computation.timeWindowMinutes()).append(" 分钟）");
        }
        answer.append("：\n");

        if (computation.modeCandidates() != null && !computation.modeCandidates().isEmpty()) {
            for (ModeCandidate candidate : computation.modeCandidates()) {
                answer.append("- ")
                        .append(candidate.displayMode())
                        .append("：")
                        .append(formatLeg(candidate.minutes(), candidate.distanceKm()))
                        .append("\n");
            }
        }

        answer.append("推荐 ").append(recommended.transportMode());
        if (recommended.estimatedMinutes() != null && recommended.estimatedMinutes() > 0) {
            answer.append("，约 ").append(recommended.estimatedMinutes()).append(" 分钟");
        }
        if (recommended.estimatedDistanceKm() != null) {
            answer.append("（约 ")
                    .append(recommended.estimatedDistanceKm().setScale(1, RoundingMode.HALF_UP))
                    .append(" 公里）");
        }

        if (computation.timeWindowMinutes() != null
                && computation.timeWindowMinutes() > 0
                && recommended.estimatedMinutes() != null
                && recommended.estimatedMinutes() > 0) {
            int delta = computation.timeWindowMinutes() - recommended.estimatedMinutes();
            if (delta >= 0) {
                answer.append("，可在时间窗内到达");
            } else {
                answer.append("，按当前路况预计超出时间窗约 ").append(Math.abs(delta)).append(" 分钟");
            }
        }
        answer.append("。");
        return answer.toString();
    }

    private String formatCandidate(ModeCandidate candidate) {
        if (candidate == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        sb.append(candidate.displayMode());
        if (candidate.minutes() != null && candidate.minutes() > 0) {
            sb.append("/").append(candidate.minutes()).append("min");
        }
        if (candidate.distanceKm() != null) {
            sb.append("/").append(candidate.distanceKm().setScale(1, RoundingMode.HALF_UP)).append("km");
        }
        return sb.toString();
    }

    private String formatEstimate(FirstLegEstimate estimate) {
        if (estimate == null) {
            return "unknown";
        }
        StringBuilder sb = new StringBuilder();
        if (StringUtils.hasText(estimate.transportMode())) {
            sb.append(estimate.transportMode());
        }
        if (estimate.estimatedMinutes() != null && estimate.estimatedMinutes() > 0) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(estimate.estimatedMinutes()).append("min");
        }
        if (estimate.estimatedDistanceKm() != null) {
            if (sb.length() > 0) {
                sb.append("/");
            }
            sb.append(estimate.estimatedDistanceKm().setScale(1, RoundingMode.HALF_UP)).append("km");
        }
        return sb.length() == 0 ? "unknown" : sb.toString();
    }

    private String formatLeg(Integer minutes, BigDecimal distanceKm) {
        List<String> parts = new ArrayList<>();
        if (minutes != null && minutes > 0) {
            parts.add("约 " + minutes + " 分钟");
        }
        if (distanceKm != null) {
            parts.add("约 " + distanceKm.setScale(1, RoundingMode.HALF_UP) + " 公里");
        }
        if (parts.isEmpty()) {
            return "暂无可用通行数据";
        }
        return String.join("，", parts);
    }

    private FirstLegEstimate estimateFromRouteNode(ChatReqDTO.ChatRouteNode first) {
        if (first == null) {
            return null;
        }
        Integer minutes = first.getDepartureTravelTime() != null && first.getDepartureTravelTime() > 0
                ? first.getDepartureTravelTime()
                : (first.getTravelTime() != null && first.getTravelTime() > 0 ? first.getTravelTime() : null);
        BigDecimal distance = first.getDepartureDistanceKm() != null
                ? first.getDepartureDistanceKm()
                : first.getTravelDistanceKm();
        if (distance != null) {
            distance = distance.setScale(1, RoundingMode.HALF_UP);
        }
        String mode = StringUtils.hasText(first.getDepartureTransportMode())
                ? first.getDepartureTransportMode().trim()
                : first.getTravelTransportMode();
        mode = normalizeTransportMode(mode, minutes, distance);
        if ((minutes == null || minutes <= 0)
                && (distance == null || distance.compareTo(BigDecimal.ZERO) <= 0)
                && !StringUtils.hasText(mode)) {
            return null;
        }
        return new FirstLegEstimate(minutes, distance, mode, "route-context");
    }

    private BigDecimal normalizeDistance(BigDecimal distanceKm) {
        if (distanceKm == null || distanceKm.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return distanceKm.setScale(1, RoundingMode.HALF_UP);
    }

    private String normalizeTransportMode(String raw, Integer minutes, BigDecimal distanceKm) {
        if (StringUtils.hasText(raw)) {
            String value = raw.trim().toLowerCase(Locale.ROOT);
            if (value.contains("walk") || value.contains("步行")) {
                return "步行";
            }
            if (value.contains("bike") || value.contains("cycle") || value.contains("骑行")) {
                return "骑行";
            }
            if (value.contains("metro") || value.contains("subway") || value.contains("地铁")) {
                return "地铁+步行";
            }
            if (value.contains("transit") || value.contains("public") || value.contains("公交")) {
                return "公交+步行";
            }
            if (value.contains("bus")) {
                return "公交+步行";
            }
            if (value.contains("taxi") || value.contains("drive") || value.contains("driv")
                    || value.contains("car") || value.contains("打车") || value.contains("驾车")) {
                return "打车";
            }
            return raw.trim();
        }

        if (distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
            double km = distanceKm.doubleValue();
            if (km <= 1.2D) {
                return "步行";
            }
            if (km <= 3.5D) {
                return "骑行";
            }
            if (km <= 10D) {
                return "地铁+步行";
            }
            return "打车";
        }
        if (minutes != null) {
            if (minutes <= 12) {
                return "步行";
            }
            if (minutes <= 22) {
                return "骑行";
            }
            if (minutes <= 45) {
                return "地铁+步行";
            }
            return "打车";
        }
        return null;
    }

    private record FirstLegComputation(FirstLegEstimate recommended,
                                       Integer timeWindowMinutes,
                                       List<ModeCandidate> modeCandidates) {
        private FirstLegComputation {
            modeCandidates = modeCandidates == null ? List.of() : modeCandidates;
        }
    }

    private record ModeProfile(String routeMode, String displayMode) {
    }

    private record ModeCandidate(String displayMode,
                                 Integer minutes,
                                 BigDecimal distanceKm,
                                 String source) {
    }

    public record FirstLegEstimate(Integer estimatedMinutes,
                                   BigDecimal estimatedDistanceKm,
                                   String transportMode,
                                   String source) {
    }

    public record SkillResult(String answer, String source, Set<String> usedSkills) {
        public SkillResult {
            usedSkills = usedSkills == null ? Set.of() : new LinkedHashSet<>(usedSkills);
        }
    }
}

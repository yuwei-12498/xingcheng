package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
public class ChatSegmentTransportSkillService {

    private static final Set<String> SEGMENT_KEYWORDS = Set.of(
            "每一段", "每段", "分段", "路段", "出行方式", "交通方式", "通行时长", "逐段", "怎么走"
    );

    private static final int MAX_EVIDENCE_SEGMENT_COUNT = 8;

    private final ChatFirstLegEtaSkillService firstLegEtaSkillService;

    public ChatSegmentTransportSkillService(ChatFirstLegEtaSkillService firstLegEtaSkillService) {
        this.firstLegEtaSkillService = firstLegEtaSkillService;
    }

    public boolean isSegmentIntent(String question) {
        if (!StringUtils.hasText(question)) {
            return false;
        }
        String normalized = question.trim().toLowerCase(Locale.ROOT);
        for (String keyword : SEGMENT_KEYWORDS) {
            if (normalized.contains(keyword)) {
                return true;
            }
        }
        return normalized.contains("segment")
                || normalized.contains("each leg")
                || normalized.contains("per leg")
                || normalized.contains("transport mode");
    }

    public SkillResult tryHandle(ChatReqDTO req, ChatRouteContextSkillService.RouteContext routeContext) {
        if (!isSegmentIntent(req == null ? null : req.getQuestion())) {
            return null;
        }
        if (routeContext == null || !routeContext.available()) {
            return new SkillResult(
                    "我还没有拿到当前路线，先生成路线后我就能把每一段的出行方式和通行时长逐段算出来。",
                    null,
                    Set.of("SegmentTransportSkill")
            );
        }

        List<ChatReqDTO.ChatRouteNode> nodes = routeContext.nodes();
        if (nodes.size() == 1) {
            ChatReqDTO.ChatRouteNode only = nodes.get(0);
            return new SkillResult(
                    "当前路线只有 1 个景点「" + only.getPoiName() + "」，暂时没有景点间路段可拆分。",
                    null,
                    Set.of("SegmentTransportSkill")
            );
        }

        ChatFirstLegEtaSkillService.FirstLegEstimate firstLeg = firstLegEtaSkillService.estimate(req, routeContext);
        StringBuilder answer = new StringBuilder("已按当前路线拆分每一段出行：\n");

        List<String> lines = new ArrayList<>();
        for (int i = 0; i < nodes.size(); i++) {
            ChatReqDTO.ChatRouteNode current = nodes.get(i);
            if (i == 0) {
                String line = buildFirstLegLine(current, firstLeg);
                if (StringUtils.hasText(line)) {
                    lines.add("1) " + line);
                }
                continue;
            }
            ChatReqDTO.ChatRouteNode prev = nodes.get(i - 1);
            lines.add((i + 1) + ") " + buildInterStopLine(prev, current));
        }

        answer.append(String.join("\n", lines));
        if (!StringUtils.hasText(routeContext.selectedOptionKey())) {
            answer.append("\n（可继续问：首段还能再快吗？我可以按实时路况重算。）");
        } else {
            answer.append("\n（当前方案：").append(routeContext.selectedOptionKey()).append("）");
        }

        Set<String> skills = new LinkedHashSet<>();
        skills.add("SegmentTransportSkill");
        if (firstLeg != null) {
            skills.add("FirstLegPreciseETASkill");
        }

        return new SkillResult(
                answer.toString(),
                firstLeg == null ? null : firstLeg.source(),
                skills
        );
    }

    public List<String> buildEvidence(ChatRouteContextSkillService.RouteContext routeContext,
                                      ChatFirstLegEtaSkillService.FirstLegEstimate firstLeg) {
        if (routeContext == null || !routeContext.available()) {
            return List.of();
        }

        List<ChatReqDTO.ChatRouteNode> nodes = routeContext.nodes();
        List<String> evidence = new ArrayList<>();
        evidence.add("segment_count=" + nodes.size());

        for (int i = 0; i < nodes.size() && i < MAX_EVIDENCE_SEGMENT_COUNT; i++) {
            String line;
            ChatReqDTO.ChatRouteNode current = nodes.get(i);
            if (i == 0) {
                line = buildEvidenceLine("当前位置", safePoiName(current),
                        firstLeg == null ? normalizeSimpleTransportMode(current.getDepartureTransportMode()) : firstLeg.transportMode(),
                        firstLeg == null ? safePositiveMinutes(current.getDepartureTravelTime(), current.getTravelTime()) : firstLeg.estimatedMinutes(),
                        firstLeg == null ? safeDistance(current.getDepartureDistanceKm(), current.getTravelDistanceKm()) : firstLeg.estimatedDistanceKm());
            } else {
                ChatReqDTO.ChatRouteNode prev = nodes.get(i - 1);
                line = buildEvidenceLine(
                        safePoiName(prev),
                        safePoiName(current),
                        normalizeSimpleTransportMode(current.getTravelTransportMode()),
                        safePositiveMinutes(current.getTravelTime(), null),
                        safeDistance(current.getTravelDistanceKm(), null)
                );
            }
            evidence.add("segment_" + (i + 1) + "=" + line);
        }

        return evidence;
    }

    private String buildEvidenceLine(String from,
                                     String to,
                                     String mode,
                                     Integer minutes,
                                     BigDecimal distanceKm) {
        StringBuilder sb = new StringBuilder();
        sb.append(StringUtils.hasText(from) ? from.trim() : "unknown")
                .append("->")
                .append(StringUtils.hasText(to) ? to.trim() : "unknown");

        if (StringUtils.hasText(mode)) {
            sb.append("/").append(mode.trim());
        }
        if (minutes != null && minutes > 0) {
            sb.append("/").append(minutes).append("min");
        }
        if (distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
            sb.append("/").append(distanceKm.setScale(1, RoundingMode.HALF_UP)).append("km");
        }
        return sb.toString();
    }

    private String buildFirstLegLine(ChatReqDTO.ChatRouteNode first,
                                     ChatFirstLegEtaSkillService.FirstLegEstimate firstLeg) {
        String target = first == null || !StringUtils.hasText(first.getPoiName()) ? "第一站" : first.getPoiName();
        Integer minutes = null;
        BigDecimal distance = null;
        String mode = null;
        if (firstLeg != null) {
            minutes = firstLeg.estimatedMinutes();
            distance = firstLeg.estimatedDistanceKm();
            mode = firstLeg.transportMode();
        } else if (first != null) {
            minutes = safePositiveMinutes(first.getDepartureTravelTime(), first.getTravelTime());
            distance = safeDistance(first.getDepartureDistanceKm(), first.getTravelDistanceKm());
            mode = normalizeSimpleTransportMode(
                    StringUtils.hasText(first.getDepartureTransportMode())
                            ? first.getDepartureTransportMode()
                            : first.getTravelTransportMode()
            );
        }
        return "当前位置 → " + target + "：" + legText(mode, minutes, distance);
    }

    private String buildInterStopLine(ChatReqDTO.ChatRouteNode from, ChatReqDTO.ChatRouteNode to) {
        String fromName = from == null || !StringUtils.hasText(from.getPoiName()) ? "上一站" : from.getPoiName();
        String toName = to == null || !StringUtils.hasText(to.getPoiName()) ? "下一站" : to.getPoiName();
        Integer minutes = to == null ? null : safePositiveMinutes(to.getTravelTime(), null);
        BigDecimal distance = to == null ? null : safeDistance(to.getTravelDistanceKm(), null);
        String mode = to == null ? null : normalizeSimpleTransportMode(to.getTravelTransportMode());
        return fromName + " → " + toName + "：" + legText(mode, minutes, distance);
    }

    private Integer safePositiveMinutes(Integer first, Integer fallback) {
        if (first != null && first > 0) {
            return first;
        }
        return fallback != null && fallback > 0 ? fallback : null;
    }

    private BigDecimal safeDistance(BigDecimal first, BigDecimal fallback) {
        BigDecimal value = first != null ? first : fallback;
        if (value == null || value.compareTo(BigDecimal.ZERO) <= 0) {
            return null;
        }
        return value.setScale(1, RoundingMode.HALF_UP);
    }

    private String safePoiName(ChatReqDTO.ChatRouteNode node) {
        if (node == null || !StringUtils.hasText(node.getPoiName())) {
            return "unknown";
        }
        return node.getPoiName().trim();
    }

    private String normalizeSimpleTransportMode(String mode) {
        if (!StringUtils.hasText(mode)) {
            return null;
        }
        String value = mode.trim().toLowerCase(Locale.ROOT);
        if (value.contains("walk") || value.contains("步行")) {
            return "步行";
        }
        if (value.contains("bike") || value.contains("cycle") || value.contains("骑行")) {
            return "骑行";
        }
        if (value.contains("metro") || value.contains("subway") || value.contains("地铁")) {
            return "地铁+步行";
        }
        if (value.contains("bus") || value.contains("公交") || value.contains("transit") || value.contains("public")) {
            return "公交+步行";
        }
        if (value.contains("taxi") || value.contains("drive") || value.contains("driv")
                || value.contains("car") || value.contains("打车") || value.contains("驾车")) {
            return "打车";
        }
        return mode.trim();
    }

    private String legText(String mode, Integer minutes, BigDecimal distance) {
        List<String> parts = new ArrayList<>();
        if (StringUtils.hasText(mode)) {
            parts.add(mode.trim());
        }
        if (minutes != null && minutes > 0) {
            parts.add("约 " + minutes + " 分钟");
        }
        if (distance != null && distance.compareTo(BigDecimal.ZERO) > 0) {
            parts.add("约 " + distance.setScale(1, RoundingMode.HALF_UP) + " 公里");
        }
        if (parts.isEmpty()) {
            return "暂无可用通行数据";
        }
        return String.join("，", parts);
    }

    public record SkillResult(String answer, String source, Set<String> usedSkills) {
        public SkillResult {
            usedSkills = usedSkills == null ? Set.of() : new LinkedHashSet<>(usedSkills);
        }
    }
}

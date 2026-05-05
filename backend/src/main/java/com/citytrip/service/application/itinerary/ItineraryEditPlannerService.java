package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ItineraryEditApplyReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.entity.UserCustomPoi;
import com.citytrip.model.vo.ItineraryDayWindowVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.planning.RouteAnalysisService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ItineraryEditPlannerService {

    private final RouteAnalysisService routeAnalysisService;
    private final UserCustomPoiCommandService userCustomPoiCommandService;

    public ItineraryEditPlannerService(RouteAnalysisService routeAnalysisService,
                                       UserCustomPoiCommandService userCustomPoiCommandService) {
        this.routeAnalysisService = routeAnalysisService;
        this.userCustomPoiCommandService = userCustomPoiCommandService;
    }

    public PreparedEdit prepare(Long userId,
                                ItineraryVO current,
                                GenerateReqDTO originalReq,
                                ItineraryEditApplyReqDTO editReq) {
        if (current == null) {
            throw new BadRequestException("当前没有可编辑的行程");
        }
        GenerateReqDTO workingReq = cloneReq(originalReq);
        Map<Integer, DayPlan> dayPlans = buildDayPlans(current, workingReq);
        List<String> summaryParts = new ArrayList<>();

        for (ItineraryEditOperationDTO operation : safeOperations(editReq)) {
            String type = normalizeType(operation.getType());
            switch (type) {
                case "update_stay" -> applyStayUpdate(dayPlans, operation, summaryParts);
                case "move_node" -> applyMove(dayPlans, operation, summaryParts);
                case "update_day_window" -> applyDayWindow(dayPlans, operation, summaryParts);
                case "insert_inline_custom_poi" -> applyInlineCustomPoi(userId, workingReq, dayPlans, operation, summaryParts);
                case "insert_saved_custom_poi" -> applyInlineCustomPoi(userId, workingReq, dayPlans, operation, summaryParts);
                case "remove_node" -> applyRemove(dayPlans, operation, summaryParts);
                default -> throw new BadRequestException("不支持的编辑操作: " + operation.getType());
            }
        }

        List<ItineraryNodeVO> mergedNodes = new ArrayList<>();
        List<ItineraryDayWindowVO> mergedWindows = new ArrayList<>();
        List<String> alerts = new ArrayList<>();
        List<String> scheduleWarnings = new ArrayList<>();
        int totalDuration = 0;
        BigDecimal totalCost = BigDecimal.ZERO;
        for (DayPlan dayPlan : orderedDayPlans(dayPlans)) {
            if (dayPlan.stops.isEmpty()) {
                continue;
            }
            GenerateReqDTO dayReq = cloneReq(workingReq);
            dayReq.setStartTime(dayPlan.window.getStartTime());
            dayReq.setEndTime(dayPlan.window.getEndTime());
            RouteAnalysisService.RouteAnalysis analysis = routeAnalysisService.analyzeRoute(
                    new ItineraryRouteOptimizer.RouteOption(
                            dayPlan.stops.stream().map(EditableStop::toPoi).toList(),
                            "",
                            0D
                    ),
                    dayReq,
                    Map.of()
            );

            List<ItineraryNodeVO> analyzedNodes = analysis == null || analysis.nodes() == null
                    ? List.of()
                    : analysis.nodes();
            for (int index = 0; index < analyzedNodes.size() && index < dayPlan.stops.size(); index++) {
                EditableStop source = dayPlan.stops.get(index);
                ItineraryNodeVO analyzed = analyzedNodes.get(index);
                analyzed.setNodeKey(source.nodeKey);
                analyzed.setDayNo(dayPlan.dayNo);
                analyzed.setStepOrder(index + 1);
                analyzed.setStayDuration(source.stayDuration);
                analyzed.setSourceType(source.sourceType);
                if ("user_custom".equals(source.sourceType)) {
                    analyzed.setPoiId(source.poiId);
                    analyzed.setPoiName(source.poiName);
                    analyzed.setCategory(source.category);
                    analyzed.setAddress(source.address);
                    analyzed.setDistrict(source.district);
                    analyzed.setLatitude(source.latitude);
                    analyzed.setLongitude(source.longitude);
                }
                mergedNodes.add(analyzed);
            }

            if (analysis != null) {
                totalDuration += Math.max(analysis.totalDuration(), 0);
                totalCost = totalCost.add(analysis.totalCost() == null ? BigDecimal.ZERO : analysis.totalCost());
                if (analysis.alerts() != null) {
                    alerts.addAll(analysis.alerts());
                }
            }
            applyOverflowWarning(dayPlan.window, analyzedNodes, scheduleWarnings);
            dayPlan.window.setDayNo(mergedWindows.size() + 1);
            mergedWindows.add(dayPlan.window);
        }

        reindexDays(mergedNodes, mergedWindows);

        ItineraryVO next = new ItineraryVO();
        next.setId(current.getId());
        next.setCustomTitle(current.getCustomTitle());
        next.setShareNote(current.getShareNote());
        next.setFavorited(current.getFavorited());
        next.setFavoriteTime(current.getFavoriteTime());
        next.setIsPublic(current.getIsPublic());
        next.setNodes(mergedNodes);
        next.setDayWindows(mergedWindows);
        next.setOptions(List.of());
        next.setSelectedOptionKey(null);
        next.setScheduleWarnings(scheduleWarnings);
        next.setAlerts(alerts);
        next.setTotalDuration(totalDuration);
        next.setTotalCost(totalCost);
        next.setOriginalReq(workingReq);
        workingReq.setTripDays((double) mergedWindows.size());
        String summary = String.join("；", summaryParts);
        if (!StringUtils.hasText(summary)) {
            summary = StringUtils.hasText(editReq == null ? null : editReq.getSummary()) ? editReq.getSummary().trim() : "已按编辑指令调整行程";
        }
        next.setRecommendReason(summary);
        return new PreparedEdit(workingReq, next, summary);
    }

    private void applyStayUpdate(Map<Integer, DayPlan> dayPlans,
                                 ItineraryEditOperationDTO operation,
                                 List<String> summaryParts) {
        EditableStop stop = requireStop(dayPlans, operation.getNodeKey());
        if (operation.getStayDuration() != null) {
            stop.stayDuration = Math.max(operation.getStayDuration(), 0);
            summaryParts.add("“" + stop.poiName + "”停留调整为 " + stop.stayDuration + " 分钟");
        }
    }

    private void applyMove(Map<Integer, DayPlan> dayPlans,
                           ItineraryEditOperationDTO operation,
                           List<String> summaryParts) {
        EditableStop stop = requireStop(dayPlans, operation.getNodeKey());
        DayPlan sourceDay = requireDay(dayPlans, stop.dayNo);
        sourceDay.stops.removeIf(item -> Objects.equals(item.nodeKey, stop.nodeKey));
        DayPlan targetDay = requireDay(dayPlans, operation.getTargetDayNo());
        int targetIndex = normalizeInsertIndex(operation.getTargetIndex(), targetDay.stops.size());
        stop.dayNo = targetDay.dayNo;
        targetDay.stops.add(targetIndex, stop);
        summaryParts.add("把“" + stop.poiName + "”移动到第 " + targetDay.dayNo + " 天第 " + (targetIndex + 1) + " 站");
    }

    private void applyDayWindow(Map<Integer, DayPlan> dayPlans,
                                ItineraryEditOperationDTO operation,
                                List<String> summaryParts) {
        DayPlan dayPlan = requireDay(dayPlans, operation.getDayNo());
        if (StringUtils.hasText(operation.getStartTime())) {
            dayPlan.window.setStartTime(operation.getStartTime().trim());
            summaryParts.add("第 " + dayPlan.dayNo + " 天开始时间调整为 " + dayPlan.window.getStartTime());
        }
        if (StringUtils.hasText(operation.getEndTime())) {
            dayPlan.window.setEndTime(operation.getEndTime().trim());
            summaryParts.add("第 " + dayPlan.dayNo + " 天结束时间调整为 " + dayPlan.window.getEndTime());
        }
    }

    private void applyInlineCustomPoi(Long userId,
                                      GenerateReqDTO workingReq,
                                      Map<Integer, DayPlan> dayPlans,
                                      ItineraryEditOperationDTO operation,
                                      List<String> summaryParts) {
        DayPlan dayPlan = requireDay(dayPlans, operation.getDayNo());
        UserCustomPoi customPoi = userCustomPoiCommandService.resolveForInsertion(userId, workingReq.getCityName(), operation);
        EditableStop stop = EditableStop.fromCustomPoi(customPoi, operation.getStayDuration());
        int targetIndex = normalizeInsertIndex(operation.getTargetIndex(), dayPlan.stops.size());
        stop.dayNo = dayPlan.dayNo;
        dayPlan.stops.add(targetIndex, stop);
        summaryParts.add("在第 " + dayPlan.dayNo + " 天新增“" + stop.poiName + "”");
    }

    private void applyRemove(Map<Integer, DayPlan> dayPlans,
                             ItineraryEditOperationDTO operation,
                             List<String> summaryParts) {
        EditableStop stop = requireStop(dayPlans, operation.getNodeKey());
        DayPlan dayPlan = requireDay(dayPlans, stop.dayNo);
        dayPlan.stops.removeIf(item -> Objects.equals(item.nodeKey, stop.nodeKey));
        summaryParts.add("移除“" + stop.poiName + "”");
    }

    private Map<Integer, DayPlan> buildDayPlans(ItineraryVO current, GenerateReqDTO originalReq) {
        Map<Integer, DayPlan> dayPlans = new LinkedHashMap<>();
        List<ItineraryDayWindowVO> windows = current.getDayWindows();
        if (windows != null) {
            for (ItineraryDayWindowVO window : windows) {
                if (window == null || window.getDayNo() == null) {
                    continue;
                }
                dayPlans.put(window.getDayNo(), new DayPlan(window.getDayNo(), copyWindow(window)));
            }
        }
        List<ItineraryNodeVO> nodes = current.getNodes() == null ? List.of() : current.getNodes();
        for (ItineraryNodeVO node : nodes) {
            int dayNo = node.getDayNo() == null ? 1 : node.getDayNo();
            DayPlan dayPlan = dayPlans.computeIfAbsent(dayNo, key -> new DayPlan(key, defaultWindow(key, originalReq)));
            dayPlan.stops.add(EditableStop.fromNode(node));
        }
        if (dayPlans.isEmpty()) {
            dayPlans.put(1, new DayPlan(1, defaultWindow(1, originalReq)));
        }
        dayPlans.values().forEach(day -> day.stops.sort(Comparator.comparingInt(item -> item.stepOrder)));
        return dayPlans;
    }

    private List<DayPlan> orderedDayPlans(Map<Integer, DayPlan> dayPlans) {
        return dayPlans.values().stream()
                .sorted(Comparator.comparingInt(item -> item.dayNo))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    private EditableStop requireStop(Map<Integer, DayPlan> dayPlans, String nodeKey) {
        return dayPlans.values().stream()
                .flatMap(day -> day.stops.stream())
                .filter(stop -> Objects.equals(stop.nodeKey, nodeKey))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("未找到要编辑的行程节点: " + nodeKey));
    }

    private DayPlan requireDay(Map<Integer, DayPlan> dayPlans, Integer dayNo) {
        if (dayNo == null) {
            throw new BadRequestException("缺少目标天数");
        }
        return dayPlans.computeIfAbsent(dayNo, key -> new DayPlan(key, defaultWindow(key, null)));
    }

    private List<ItineraryEditOperationDTO> safeOperations(ItineraryEditApplyReqDTO editReq) {
        if (editReq == null || editReq.getOperations() == null) {
            return List.of();
        }
        return editReq.getOperations().stream().filter(Objects::nonNull).toList();
    }

    private String normalizeType(String rawType) {
        return rawType == null ? "" : rawType.trim().toLowerCase(Locale.ROOT);
    }

    private int normalizeInsertIndex(Integer targetIndex, int currentSize) {
        if (targetIndex == null) {
            return currentSize;
        }
        return Math.max(0, Math.min(targetIndex - 1, currentSize));
    }

    private GenerateReqDTO cloneReq(GenerateReqDTO source) {
        GenerateReqDTO target = new GenerateReqDTO();
        if (source == null) {
            return target;
        }
        target.setCityName(source.getCityName());
        target.setCityCode(source.getCityCode());
        target.setTripDays(source.getTripDays());
        target.setTripDate(source.getTripDate());
        target.setTotalBudget(source.getTotalBudget());
        target.setBudgetLevel(source.getBudgetLevel());
        target.setThemes(source.getThemes() == null ? null : new ArrayList<>(source.getThemes()));
        target.setIsRainy(source.getIsRainy());
        target.setIsNight(source.getIsNight());
        target.setWalkingLevel(source.getWalkingLevel());
        target.setCompanionType(source.getCompanionType());
        target.setStartTime(source.getStartTime());
        target.setEndTime(source.getEndTime());
        target.setMustVisitPoiNames(source.getMustVisitPoiNames() == null ? null : new ArrayList<>(source.getMustVisitPoiNames()));
        target.setDeparturePlaceName(source.getDeparturePlaceName());
        target.setDepartureLatitude(source.getDepartureLatitude());
        target.setDepartureLongitude(source.getDepartureLongitude());
        return target;
    }

    private ItineraryDayWindowVO defaultWindow(Integer dayNo, GenerateReqDTO req) {
        ItineraryDayWindowVO window = new ItineraryDayWindowVO();
        window.setDayNo(dayNo);
        window.setStartTime(StringUtils.hasText(req == null ? null : req.getStartTime()) ? req.getStartTime() : "09:00");
        window.setEndTime(StringUtils.hasText(req == null ? null : req.getEndTime()) ? req.getEndTime() : "18:00");
        return window;
    }

    private ItineraryDayWindowVO copyWindow(ItineraryDayWindowVO source) {
        ItineraryDayWindowVO copy = new ItineraryDayWindowVO();
        copy.setDayNo(source.getDayNo());
        copy.setStartTime(source.getStartTime());
        copy.setEndTime(source.getEndTime());
        copy.setOverflowMinutes(source.getOverflowMinutes());
        return copy;
    }

    private void reindexDays(List<ItineraryNodeVO> nodes, List<ItineraryDayWindowVO> dayWindows) {
        Map<Integer, Integer> dayMapping = new LinkedHashMap<>();
        int nextDayNo = 1;
        for (ItineraryNodeVO node : nodes) {
            int sourceDay = node.getDayNo() == null ? 1 : node.getDayNo();
            dayMapping.putIfAbsent(sourceDay, nextDayNo++);
            node.setDayNo(dayMapping.get(sourceDay));
        }
        for (int index = 0; index < dayWindows.size(); index++) {
            dayWindows.get(index).setDayNo(index + 1);
        }
    }

    private void applyOverflowWarning(ItineraryDayWindowVO window,
                                      List<ItineraryNodeVO> analyzedNodes,
                                      List<String> scheduleWarnings) {
        if (window == null || analyzedNodes == null || analyzedNodes.isEmpty()) {
            return;
        }
        String lastEndTime = analyzedNodes.get(analyzedNodes.size() - 1).getEndTime();
        int overflowMinutes = parseMinutes(lastEndTime) - parseMinutes(window.getEndTime());
        if (overflowMinutes > 0) {
            window.setOverflowMinutes(overflowMinutes);
            scheduleWarnings.add("第 " + window.getDayNo() + " 天超出时间窗 " + overflowMinutes + " 分钟");
        } else {
            window.setOverflowMinutes(0);
        }
    }

    private int parseMinutes(String time) {
        if (!StringUtils.hasText(time) || !time.contains(":")) {
            return 0;
        }
        String[] parts = time.split(":");
        if (parts.length < 2) {
            return 0;
        }
        try {
            return Integer.parseInt(parts[0].trim()) * 60 + Integer.parseInt(parts[1].trim());
        } catch (NumberFormatException ex) {
            return 0;
        }
    }

    public record PreparedEdit(GenerateReqDTO updatedRequest, ItineraryVO itinerary, String summary) {
    }

    private static final class DayPlan {
        private final Integer dayNo;
        private final ItineraryDayWindowVO window;
        private final List<EditableStop> stops = new ArrayList<>();

        private DayPlan(Integer dayNo, ItineraryDayWindowVO window) {
            this.dayNo = dayNo;
            this.window = window;
        }
    }

    private static final class EditableStop {
        private String nodeKey;
        private Integer dayNo;
        private Integer stepOrder;
        private Long poiId;
        private String poiName;
        private String category;
        private String district;
        private String address;
        private BigDecimal latitude;
        private BigDecimal longitude;
        private Integer stayDuration;
        private String sourceType;

        private static EditableStop fromNode(ItineraryNodeVO node) {
            EditableStop stop = new EditableStop();
            stop.nodeKey = StringUtils.hasText(node.getNodeKey()) ? node.getNodeKey() : UUID.randomUUID().toString();
            stop.dayNo = node.getDayNo() == null ? 1 : node.getDayNo();
            stop.stepOrder = node.getStepOrder() == null ? 1 : node.getStepOrder();
            stop.poiId = node.getPoiId();
            stop.poiName = node.getPoiName();
            stop.category = node.getCategory();
            stop.district = node.getDistrict();
            stop.address = node.getAddress();
            stop.latitude = node.getLatitude();
            stop.longitude = node.getLongitude();
            stop.stayDuration = node.getStayDuration() == null ? 60 : node.getStayDuration();
            stop.sourceType = StringUtils.hasText(node.getSourceType()) ? node.getSourceType() : "local";
            return stop;
        }

        private static EditableStop fromCustomPoi(UserCustomPoi customPoi, Integer requestedStayDuration) {
            EditableStop stop = new EditableStop();
            stop.nodeKey = "custom-" + customPoi.getId();
            stop.stepOrder = 1;
            stop.poiId = customPoi.getId() == null ? null : -Math.abs(customPoi.getId());
            stop.poiName = customPoi.getName();
            stop.category = customPoi.getCategory();
            stop.district = customPoi.getDistrict();
            stop.address = customPoi.getAddress();
            stop.latitude = customPoi.getLatitude();
            stop.longitude = customPoi.getLongitude();
            stop.stayDuration = requestedStayDuration != null
                    ? requestedStayDuration
                    : (customPoi.getSuggestedStayDuration() == null ? 60 : customPoi.getSuggestedStayDuration());
            stop.sourceType = "user_custom";
            return stop;
        }

        private Poi toPoi() {
            Poi poi = new Poi();
            poi.setId(poiId);
            poi.setName(poiName);
            poi.setCategory(category);
            poi.setDistrict(district);
            poi.setAddress(address);
            poi.setLatitude(latitude);
            poi.setLongitude(longitude);
            poi.setStayDuration(stayDuration);
            poi.setAvgCost(BigDecimal.ZERO);
            poi.setSourceType(sourceType);
            return poi;
        }
    }
}

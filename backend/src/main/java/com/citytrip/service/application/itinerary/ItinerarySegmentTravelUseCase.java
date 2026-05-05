package com.citytrip.service.application.itinerary;

import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.model.vo.SegmentRouteGuideVO;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.domain.planning.SegmentRouteGuideService;
import com.citytrip.service.impl.AmapTravelTimeServiceImpl;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;
import java.util.Objects;

@Service
public class ItinerarySegmentTravelUseCase {

    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final AmapTravelTimeServiceImpl amapTravelTimeService;
    private final SegmentRouteGuideService segmentRouteGuideService;

    public ItinerarySegmentTravelUseCase(SavedItineraryRepository savedItineraryRepository,
                                         SavedItineraryCodec savedItineraryCodec,
                                         SavedItineraryCommandService savedItineraryCommandService,
                                         AmapTravelTimeServiceImpl amapTravelTimeService,
                                         SegmentRouteGuideService segmentRouteGuideService) {
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.amapTravelTimeService = amapTravelTimeService;
        this.segmentRouteGuideService = segmentRouteGuideService;
    }

    public ItineraryVO calculate(Long userId, Long itineraryId, Integer segmentIndex) {
        int index = segmentIndex == null ? -1 : segmentIndex;
        if (index < 0) {
            throw new BadRequestException("segmentIndex must be non-negative");
        }

        SavedItinerary entity = savedItineraryRepository.requireOwned(userId, itineraryId);
        GenerateReqDTO request;
        ItineraryVO itinerary;
        try {
            request = savedItineraryCodec.readRequest(entity);
            itinerary = savedItineraryCodec.readItinerary(entity);
        } catch (JsonProcessingException ex) {
            throw new BadRequestException("The itinerary record is corrupted and cannot calculate travel guidance");
        }

        List<ItineraryNodeVO> nodes = itinerary == null ? null : itinerary.getNodes();
        if (nodes == null || nodes.isEmpty() || index >= nodes.size()) {
            throw new BadRequestException("segmentIndex is out of range");
        }

        ItineraryNodeVO toNode = nodes.get(index);
        boolean firstStopOfDay = isFirstStopOfDay(toNode, index);
        Poi from = firstStopOfDay ? buildDeparturePoi(request) : toPoi(nodes.get(index - 1));
        Poi to = toPoi(toNode);
        TravelTimeService.TravelLegEstimate estimate = amapTravelTimeService.estimateTravelLeg(from, to);
        applyEstimate(toNode, estimate, firstStopOfDay);
        recomputeSchedules(itinerary.getNodes(), request);
        applyEstimateToMatchingOptionNode(itinerary, toNode, estimate, firstStopOfDay, request);
        refreshItineraryTotals(itinerary);
        return savedItineraryCommandService.save(userId, itineraryId, request, itinerary);
    }

    private boolean isFirstStopOfDay(ItineraryNodeVO node, int index) {
        if (index <= 0) {
            return true;
        }
        return node != null && Integer.valueOf(1).equals(node.getStepOrder());
    }

    private void applyEstimateToMatchingOptionNode(ItineraryVO itinerary,
                                                   ItineraryNodeVO sourceNode,
                                                   TravelTimeService.TravelLegEstimate estimate,
                                                   boolean firstStop,
                                                   GenerateReqDTO request) {
        if (itinerary == null || itinerary.getOptions() == null || sourceNode == null) {
            return;
        }
        for (ItineraryOptionVO option : itinerary.getOptions()) {
            if (option == null || option.getNodes() == null) {
                continue;
            }
            for (ItineraryNodeVO candidate : option.getNodes()) {
                if (sameNode(candidate, sourceNode)) {
                    applyEstimate(candidate, estimate, firstStop);
                    recomputeSchedules(option.getNodes(), request);
                    refreshOptionTotals(option);
                }
            }
        }
    }


    private void recomputeSchedules(List<ItineraryNodeVO> nodes, GenerateReqDTO request) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }
        int defaultStart = parseClockMinutes(request == null ? null : request.getStartTime(), 9 * 60);
        Integer currentDay = null;
        int cursor = defaultStart;
        for (ItineraryNodeVO node : nodes) {
            if (node == null) {
                continue;
            }
            int dayNo = node.getDayNo() == null ? 1 : Math.max(1, node.getDayNo());
            if (!Integer.valueOf(dayNo).equals(currentDay) || Integer.valueOf(1).equals(node.getStepOrder())) {
                currentDay = dayNo;
                cursor = defaultStart;
            }
            int travel = Math.max(0, node.getTravelTime() == null ? 0 : node.getTravelTime());
            int stay = resolveStayMinutes(node);
            int start = safeAdd(cursor, travel);
            int end = safeAdd(start, stay);
            node.setStartTime(formatClockMinutes(start));
            node.setEndTime(formatClockMinutes(end));
            cursor = end;
        }
    }

    private int resolveStayMinutes(ItineraryNodeVO node) {
        if (node == null) {
            return 0;
        }
        if (node.getStayDuration() != null && node.getStayDuration() > 0) {
            return node.getStayDuration();
        }
        int start = parseClockMinutes(node.getStartTime(), -1);
        int end = parseClockMinutes(node.getEndTime(), -1);
        if (start >= 0 && end >= start) {
            return end - start;
        }
        return 120;
    }

    private void refreshItineraryTotals(ItineraryVO itinerary) {
        if (itinerary == null) {
            return;
        }
        itinerary.setTotalDuration(totalDuration(itinerary.getNodes()));
        if (itinerary.getOptions() != null) {
            for (ItineraryOptionVO option : itinerary.getOptions()) {
                refreshOptionTotals(option);
            }
        }
    }

    private void refreshOptionTotals(ItineraryOptionVO option) {
        if (option == null) {
            return;
        }
        option.setTotalDuration(totalDuration(option.getNodes()));
        option.setTotalTravelTime(totalTravelTime(option.getNodes()));
    }

    private int totalTravelTime(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItineraryNodeVO node : nodes) {
            if (node != null && node.getTravelTime() != null && node.getTravelTime() > 0) {
                total = safeAdd(total, node.getTravelTime());
            }
        }
        return total;
    }

    private int totalDuration(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return 0;
        }
        int total = 0;
        for (ItineraryNodeVO node : nodes) {
            if (node == null) {
                continue;
            }
            total = safeAdd(total, Math.max(0, node.getTravelTime() == null ? 0 : node.getTravelTime()));
            total = safeAdd(total, resolveStayMinutes(node));
        }
        return total;
    }

    private int parseClockMinutes(String value, int fallback) {
        if (!StringUtils.hasText(value)) {
            return fallback;
        }
        String[] parts = value.trim().split(":");
        if (parts.length < 2) {
            return fallback;
        }
        try {
            int hour = Integer.parseInt(parts[0]);
            int minute = Integer.parseInt(parts[1]);
            if (hour < 0 || minute < 0 || minute > 59) {
                return fallback;
            }
            return hour * 60 + minute;
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private String formatClockMinutes(int value) {
        int normalized = Math.floorMod(value, 24 * 60);
        return String.format("%02d:%02d", normalized / 60, normalized % 60);
    }

    private int safeAdd(int left, int right) {
        long sum = (long) left + right;
        if (sum > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        if (sum < Integer.MIN_VALUE) {
            return Integer.MIN_VALUE;
        }
        return (int) sum;
    }

    private boolean sameNode(ItineraryNodeVO left, ItineraryNodeVO right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getDayNo(), right.getDayNo())
                && Objects.equals(left.getStepOrder(), right.getStepOrder())
                && Objects.equals(left.getPoiId(), right.getPoiId());
    }

    private void applyEstimate(ItineraryNodeVO node,
                               TravelTimeService.TravelLegEstimate estimate,
                               boolean firstStop) {
        if (node == null || estimate == null) {
            return;
        }
        node.setTravelTime(Math.max(estimate.estimatedMinutes(), 0));
        if (estimate.estimatedDistanceKm() != null) {
            node.setTravelDistanceKm(estimate.estimatedDistanceKm().setScale(1, RoundingMode.HALF_UP));
        }
        if (StringUtils.hasText(estimate.transportMode())) {
            node.setTravelTransportMode(estimate.transportMode().trim());
        }
        SegmentRouteGuideVO guide = segmentRouteGuideService.buildGuide(estimate);
        node.setSegmentRouteGuide(guide);
        node.setRoutePathPoints(guide == null ? List.of() : guide.getPathPoints());
        if (firstStop) {
            node.setDepartureTravelTime(node.getTravelTime());
            node.setDepartureDistanceKm(node.getTravelDistanceKm());
            node.setDepartureTransportMode(node.getTravelTransportMode());
        }
    }

    private Poi buildDeparturePoi(GenerateReqDTO request) {
        Poi poi = new Poi();
        poi.setId(-1L);
        poi.setName(StringUtils.hasText(request == null ? null : request.getDeparturePlaceName())
                ? request.getDeparturePlaceName().trim()
                : "departure");
        poi.setCityName(request == null ? null : request.getCityName());
        if (request != null && request.getDepartureLatitude() != null && request.getDepartureLongitude() != null) {
            poi.setLatitude(BigDecimal.valueOf(request.getDepartureLatitude()));
            poi.setLongitude(BigDecimal.valueOf(request.getDepartureLongitude()));
        }
        return poi;
    }

    private Poi toPoi(ItineraryNodeVO node) {
        Poi poi = new Poi();
        if (node == null) {
            return poi;
        }
        poi.setId(node.getPoiId());
        poi.setName(node.getPoiName());
        poi.setCategory(node.getCategory());
        poi.setDistrict(node.getDistrict());
        poi.setAddress(node.getAddress());
        poi.setLatitude(node.getLatitude());
        poi.setLongitude(node.getLongitude());
        return poi;
    }
}

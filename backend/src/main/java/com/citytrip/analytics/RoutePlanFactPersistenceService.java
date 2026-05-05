package com.citytrip.analytics;

import com.citytrip.analytics.command.RoutePlanFactTrackCommand;
import com.citytrip.mapper.RouteNodeFactMapper;
import com.citytrip.mapper.RoutePlanFactMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.RouteNodeFact;
import com.citytrip.model.entity.RoutePlanFact;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

@Service
public class RoutePlanFactPersistenceService {

    private final RoutePlanFactMapper routePlanFactMapper;
    private final RouteNodeFactMapper routeNodeFactMapper;
    private final ObjectMapper objectMapper;

    public RoutePlanFactPersistenceService(RoutePlanFactMapper routePlanFactMapper,
                                           RouteNodeFactMapper routeNodeFactMapper,
                                           ObjectMapper objectMapper) {
        this.routePlanFactMapper = routePlanFactMapper;
        this.routeNodeFactMapper = routeNodeFactMapper;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void persist(RoutePlanFactTrackCommand command) {
        if (command == null || !StringUtils.hasText(command.getPlanSource())) {
            return;
        }

        ItineraryVO itinerary = command.getItinerary();
        ItineraryOptionVO selectedOption = findSelectedOption(itinerary);
        String selectedOptionKey = selectedOption == null ? safeTrim(itinerary == null ? null : itinerary.getSelectedOptionKey())
                : safeTrim(selectedOption.getOptionKey());
        String selectedRouteSignature = selectedOption == null ? buildSignature(itinerary == null ? null : itinerary.getNodes())
                : firstNonBlank(selectedOption.getSignature(), buildSignature(selectedOption.getNodes()));

        RoutePlanFact fact = new RoutePlanFact();
        fact.setUserId(command.getUserId());
        fact.setItineraryId(command.getItineraryId());
        fact.setPlanSource(command.getPlanSource());
        fact.setAlgorithmVersion(command.getAlgorithmVersion());
        fact.setRecallStrategy(command.getRecallStrategy());
        fact.setRawCandidateCount(defaultInt(command.getRawCandidateCount()));
        fact.setFilteredCandidateCount(defaultInt(command.getFilteredCandidateCount()));
        fact.setFinalCandidateCount(defaultInt(command.getFinalCandidateCount()));
        fact.setMaxStops(defaultInt(command.getMaxStops()));
        fact.setGeneratedRouteCount(defaultInt(command.getGeneratedRouteCount()));
        fact.setDisplayedOptionCount(resolveDisplayedOptionCount(command, itinerary));
        fact.setSelectedOptionKey(selectedOptionKey);
        fact.setSelectedRouteSignature(selectedRouteSignature);
        fact.setTotalDuration(itinerary == null ? null : itinerary.getTotalDuration());
        fact.setTotalCost(itinerary == null ? null : itinerary.getTotalCost());
        fact.setTotalTravelTime(selectedOption == null ? null : selectedOption.getTotalTravelTime());
        fact.setBusinessRiskScore(selectedOption == null ? null : selectedOption.getBusinessRiskScore());
        fact.setThemeMatchCount(selectedOption == null ? null : selectedOption.getThemeMatchCount());
        fact.setRouteUtility(selectedOption == null || selectedOption.getRouteUtility() == null
                ? null
                : BigDecimal.valueOf(selectedOption.getRouteUtility()));
        fact.setSelectedRouteFeatureJson(selectedOption == null ? null : writeJson(selectedOption.getFeatureVector()));
        fact.setOptionsFeatureJson(writeJson(buildOptionFeatureSnapshots(itinerary)));
        fillRequestSnapshot(fact, command.getRequest());
        fact.setReplanFromItineraryId(command.getReplanFromItineraryId());
        fact.setReplaceTargetPoiId(command.getReplaceTargetPoiId());
        fact.setReplacedWithPoiId(command.getReplacedWithPoiId());
        fact.setSuccessFlag(Boolean.TRUE.equals(command.getSuccessFlag()) ? 1 : 0);
        fact.setFailReason(normalizeFailReason(command.getFailReason()));
        fact.setPlanningStartedAt(command.getPlanningStartedAt());
        fact.setPlanningFinishedAt(command.getPlanningFinishedAt());
        fact.setCostMs(command.getCostMs());
        routePlanFactMapper.insert(fact);

        List<ItineraryNodeVO> nodes = selectedOption == null
                ? (itinerary == null || itinerary.getNodes() == null ? Collections.emptyList() : itinerary.getNodes())
                : (selectedOption.getNodes() == null ? Collections.emptyList() : selectedOption.getNodes());
        for (ItineraryNodeVO node : nodes) {
            RouteNodeFact nodeFact = new RouteNodeFact();
            nodeFact.setPlanFactId(fact.getId());
            nodeFact.setItineraryId(command.getItineraryId());
            nodeFact.setUserId(command.getUserId());
            nodeFact.setOptionKey(selectedOptionKey);
            nodeFact.setRouteSignature(selectedRouteSignature);
            nodeFact.setStepOrder(node.getStepOrder());
            nodeFact.setPoiId(node.getPoiId());
            nodeFact.setPoiName(node.getPoiName());
            nodeFact.setCategory(node.getCategory());
            nodeFact.setDistrict(node.getDistrict());
            nodeFact.setStartTime(parseTime(node.getStartTime()));
            nodeFact.setEndTime(parseTime(node.getEndTime()));
            nodeFact.setStayDuration(node.getStayDuration());
            nodeFact.setTravelTime(node.getTravelTime());
            nodeFact.setNodeCost(node.getCost());
            nodeFact.setSysReason(truncate(node.getSysReason(), 255));
            nodeFact.setOperatingStatus(truncate(node.getOperatingStatus(), 32));
            nodeFact.setStatusNote(truncate(node.getStatusNote(), 255));
            routeNodeFactMapper.insert(nodeFact);
        }
    }

    private void fillRequestSnapshot(RoutePlanFact fact, GenerateReqDTO request) {
        if (fact == null || request == null) {
            return;
        }
        fact.setTripDate(parseDate(request.getTripDate()));
        fact.setTripStartTime(parseTime(request.getStartTime()));
        fact.setTripEndTime(parseTime(request.getEndTime()));
        fact.setBudgetLevel(safeTrim(request.getBudgetLevel()));
        fact.setIsRainy(Boolean.TRUE.equals(request.getIsRainy()) ? 1 : 0);
        fact.setIsNight(Boolean.TRUE.equals(request.getIsNight()) ? 1 : 0);
        fact.setWalkingLevel(safeTrim(request.getWalkingLevel()));
        fact.setCompanionType(safeTrim(request.getCompanionType()));
        fact.setThemesJson(writeJson(request.getThemes()));
        fact.setRequestSnapshotJson(writeJson(request));
    }

    private ItineraryOptionVO findSelectedOption(ItineraryVO itinerary) {
        if (itinerary == null || itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            return null;
        }
        String selectedOptionKey = itinerary.getSelectedOptionKey();
        for (ItineraryOptionVO option : itinerary.getOptions()) {
            if (option != null && Objects.equals(option.getOptionKey(), selectedOptionKey)) {
                return option;
            }
        }
        return itinerary.getOptions().get(0);
    }

    private Integer resolveDisplayedOptionCount(RoutePlanFactTrackCommand command, ItineraryVO itinerary) {
        if (command != null && command.getDisplayedOptionCount() != null) {
            return command.getDisplayedOptionCount();
        }
        if (itinerary == null || itinerary.getOptions() == null) {
            return 0;
        }
        return itinerary.getOptions().size();
    }

    private List<Map<String, Object>> buildOptionFeatureSnapshots(ItineraryVO itinerary) {
        if (itinerary == null || itinerary.getOptions() == null || itinerary.getOptions().isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> snapshots = new ArrayList<>();
        for (ItineraryOptionVO option : itinerary.getOptions()) {
            if (option == null) {
                continue;
            }
            Map<String, Object> snapshot = new LinkedHashMap<>();
            snapshot.put("optionKey", option.getOptionKey());
            snapshot.put("signature", option.getSignature());
            snapshot.put("routeUtility", option.getRouteUtility());
            snapshot.put("criticScore", option.getCriticScore());
            snapshot.put("featureVector", option.getFeatureVector());
            snapshots.add(snapshot);
        }
        return snapshots;
    }

    private int defaultInt(Integer value) {
        return value == null ? 0 : value;
    }

    private String buildSignature(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return null;
        }
        StringBuilder builder = new StringBuilder();
        for (ItineraryNodeVO node : nodes) {
            if (node == null || node.getPoiId() == null) {
                continue;
            }
            if (!builder.isEmpty()) {
                builder.append("-");
            }
            builder.append(node.getPoiId());
        }
        return builder.isEmpty() ? null : builder.toString();
    }

    private LocalTime parseTime(String time) {
        if (!StringUtils.hasText(time)) {
            return null;
        }
        try {
            return LocalTime.parse(time.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private LocalDate parseDate(String date) {
        if (!StringUtils.hasText(date)) {
            return null;
        }
        try {
            return LocalDate.parse(date.trim());
        } catch (DateTimeParseException ex) {
            return null;
        }
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException ex) {
            return null;
        }
    }

    private String normalizeFailReason(String value) {
        return truncate(value, 255);
    }

    private String safeTrim(String value) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        return value.trim();
    }

    private String firstNonBlank(String first, String second) {
        return StringUtils.hasText(first) ? first.trim() : safeTrim(second);
    }

    private String truncate(String value, int maxLength) {
        if (!StringUtils.hasText(value)) {
            return null;
        }
        String normalized = value.trim();
        return normalized.length() <= maxLength ? normalized : normalized.substring(0, maxLength);
    }
}

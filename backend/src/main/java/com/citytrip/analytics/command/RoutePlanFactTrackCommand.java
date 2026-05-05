package com.citytrip.analytics.command;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ItineraryVO;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class RoutePlanFactTrackCommand {
    private Long userId;
    private Long itineraryId;
    private String planSource;
    private String algorithmVersion;
    private String recallStrategy;
    private Integer rawCandidateCount;
    private Integer filteredCandidateCount;
    private Integer finalCandidateCount;
    private Integer maxStops;
    private Integer generatedRouteCount;
    private Integer displayedOptionCount;
    private GenerateReqDTO request;
    private ItineraryVO itinerary;
    private Long replanFromItineraryId;
    private Long replaceTargetPoiId;
    private Long replacedWithPoiId;
    private Boolean successFlag;
    private String failReason;
    private LocalDateTime planningStartedAt;
    private LocalDateTime planningFinishedAt;
    private Integer costMs;
}

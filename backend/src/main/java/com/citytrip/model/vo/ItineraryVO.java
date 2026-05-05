package com.citytrip.model.vo;

import com.citytrip.model.dto.GenerateReqDTO;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Data
public class ItineraryVO {
    private Long id;
    private String customTitle;
    private String shareNote;
    private String coverImageUrl;
    private Integer totalDuration;
    private BigDecimal totalCost;
    private String recommendReason;
    private String recommendationSource;
    private Boolean aiDecorated;
    private String criticReason;
    private Map<String, String> rejectedOptionReasons;
    private String tips;
    private Boolean favorited;
    private LocalDateTime favoriteTime;
    private Boolean isPublic;
    private GenerateReqDTO originalReq;
    private List<String> alerts;
    private LocalDateTime lastSavedAt;
    private String selectedOptionKey;
    private Long activeEditVersionId;
    private List<String> scheduleWarnings;
    private List<ItineraryDayWindowVO> dayWindows;
    private List<ItineraryOptionVO> options;
    private List<ItineraryNodeVO> nodes;
}

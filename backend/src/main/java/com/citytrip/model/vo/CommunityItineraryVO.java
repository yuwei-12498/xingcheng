package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class CommunityItineraryVO {
    private Long id;
    private String title;
    private String cityName;
    private String tripDate;
    private String coverImageUrl;
    private Integer totalDuration;
    private BigDecimal totalCost;
    private Integer nodeCount;
    private String authorLabel;
    private String shareNote;
    private String routeSummary;
    private List<String> themes = Collections.emptyList();
    private List<String> highlights = Collections.emptyList();
    private Long likeCount;
    private Boolean liked;
    private Long commentCount;
    private Boolean globalPinned;
    private LocalDateTime globalPinnedAt;
    private LocalDateTime updatedAt;
}
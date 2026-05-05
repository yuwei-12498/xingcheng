package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class CommunityItineraryDetailVO {
    private Long id;
    private Long authorId;
    private String title;
    private String cityName;
    private String coverImageUrl;
    private String shareNote;
    private String authorLabel;
    private String tripDate;
    private String startTime;
    private String endTime;
    private List<String> themes = Collections.emptyList();
    private Integer totalDuration;
    private BigDecimal totalCost;
    private Integer nodeCount;
    private String routeSummary;
    private String recommendReason;
    private String selectedOptionKey;
    private List<String> highlights = Collections.emptyList();
    private List<String> alerts = Collections.emptyList();
    private List<ItineraryNodeVO> nodes = Collections.emptyList();
    private Long likeCount;
    private Boolean liked;
    private Long commentCount;
    private Boolean globalPinned;
    private LocalDateTime globalPinnedAt;
    private Long pinnedCommentId;
    private CommunityCommentVO pinnedComment;
    private Boolean canDelete;
    private Boolean canPinComment;
    private Boolean canManage;
    private LocalDateTime updatedAt;
}
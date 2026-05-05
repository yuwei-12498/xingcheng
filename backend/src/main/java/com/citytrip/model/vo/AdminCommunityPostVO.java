package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class AdminCommunityPostVO {
    private Long id;
    private Long userId;
    private String title;
    private String authorLabel;
    private String coverImageUrl;
    private String shareNote;
    private List<String> themes = Collections.emptyList();
    private Integer totalDuration;
    private BigDecimal totalCost;
    private Integer nodeCount;
    private Long likeCount;
    private Long commentCount;
    private Boolean globalPinned;
    private Boolean deleted;
    private LocalDateTime globalPinnedAt;
    private LocalDateTime deletedAt;
    private Long deletedBy;
    private LocalDateTime updatedAt;
}

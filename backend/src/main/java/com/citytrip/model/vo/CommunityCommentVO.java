package com.citytrip.model.vo;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

@Data
public class CommunityCommentVO {
    private Long id;
    private Long itineraryId;
    private Long parentId;
    private Long userId;
    private String content;
    private String authorLabel;
    private LocalDateTime createTime;
    private Boolean mine;
    private Boolean pinned;
    private Boolean canPin;
    private List<CommunityCommentVO> replies = Collections.emptyList();
}
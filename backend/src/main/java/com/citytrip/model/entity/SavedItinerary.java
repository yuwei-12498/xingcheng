package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("saved_itinerary")
public class SavedItinerary {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String requestJson;
    private String itineraryJson;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String customTitle;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private String shareNote;
    private Integer favorited;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime favoriteTime;
    private Integer isPublic;
    private Integer isDeleted;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime deletedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long deletedBy;
    private Integer isGlobalPinned;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private LocalDateTime globalPinnedAt;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long globalPinnedBy;
    @TableField(updateStrategy = FieldStrategy.ALWAYS)
    private Long pinnedCommentId;
    private Integer nodeCount;
    private Integer totalDuration;
    private BigDecimal totalCost;
    private String routeSignature;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

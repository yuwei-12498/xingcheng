package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SegmentRouteGuideVO {
    /**
     * 折叠态摘要，例如：${transportMode} 约 ${durationMinutes} 分钟，约 ${distanceKm} 公里
     */
    private String summary;

    /**
     * 该段推荐交通方式（仅来自真实 route provider 或既有 travelLeg facts）。
     */
    private String transportMode;

    /**
     * 总时长（分钟）。
     */
    private Integer durationMinutes;

    /**
     * 总距离（公里）。
     */
    private BigDecimal distanceKm;

    /**
     * 是否拿到了完整结构化步骤（steps）。
     */
    private Boolean detailAvailable;

    /**
     * 不完整/失败原因（用于显式降级提示）。
     */
    private String incompleteReason;

    /**
     * 结构化步骤（仅来自真实 route provider steps，不得编造）。
     */
    private List<SegmentRouteStepVO> steps;

    /**
     * 该段总路线 geometry。
     */
    private List<RoutePathPointVO> pathPoints;

    /**
     * 数据来源（限定为真实 route provider；非 provider 结果可为空）。
     */
    private String source;
}


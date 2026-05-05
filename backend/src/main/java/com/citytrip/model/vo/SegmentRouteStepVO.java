package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class SegmentRouteStepVO {
    private Integer stepOrder;
    private String type;
    private String instruction;
    private Integer distanceMeters;
    private Integer durationMinutes;
    private String lineName;
    private String fromStation;
    private String toStation;
    private String entranceName;
    private String exitName;
    private Integer stopCount;
    private List<RoutePathPointVO> pathPoints;
}


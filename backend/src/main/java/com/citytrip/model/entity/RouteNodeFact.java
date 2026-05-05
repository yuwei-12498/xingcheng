package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("route_node_fact")
public class RouteNodeFact {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("plan_fact_id")
    private Long planFactId;

    @TableField("itinerary_id")
    private Long itineraryId;

    @TableField("user_id")
    private Long userId;

    @TableField("option_key")
    private String optionKey;

    @TableField("route_signature")
    private String routeSignature;

    @TableField("step_order")
    private Integer stepOrder;

    @TableField("poi_id")
    private Long poiId;

    @TableField("poi_name")
    private String poiName;

    @TableField("category")
    private String category;

    @TableField("district")
    private String district;

    @TableField("start_time")
    private LocalTime startTime;

    @TableField("end_time")
    private LocalTime endTime;

    @TableField("stay_duration")
    private Integer stayDuration;

    @TableField("travel_time")
    private Integer travelTime;

    @TableField("node_cost")
    private BigDecimal nodeCost;

    @TableField("sys_reason")
    private String sysReason;

    @TableField("operating_status")
    private String operatingStatus;

    @TableField("status_note")
    private String statusNote;

    @TableField("create_time")
    private LocalDateTime createTime;
}

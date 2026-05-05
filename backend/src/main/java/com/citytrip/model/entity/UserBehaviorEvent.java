package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_behavior_event")
public class UserBehaviorEvent {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("session_id")
    private String sessionId;

    @TableField("request_id")
    private String requestId;

    @TableField("event_type")
    private String eventType;

    @TableField("event_source")
    private String eventSource;

    @TableField("itinerary_id")
    private Long itineraryId;

    @TableField("poi_id")
    private Long poiId;

    @TableField("option_key")
    private String optionKey;

    @TableField("interaction_weight")
    private BigDecimal interactionWeight;

    @TableField("success_flag")
    private Integer successFlag;

    @TableField("cost_ms")
    private Integer costMs;

    @TableField("request_uri")
    private String requestUri;

    @TableField("http_method")
    private String httpMethod;

    @TableField("client_ip")
    private String clientIp;

    @TableField("user_agent")
    private String userAgent;

    @TableField("referer")
    private String referer;

    @TableField("extra_json")
    private String extraJson;

    @TableField("event_time")
    private LocalDateTime eventTime;
}

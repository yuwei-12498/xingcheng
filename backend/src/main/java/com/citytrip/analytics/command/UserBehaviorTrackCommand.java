package com.citytrip.analytics.command;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
public class UserBehaviorTrackCommand {
    private Long userId;
    private String sessionId;
    private String requestId;
    private String eventType;
    private String eventSource;
    private Long itineraryId;
    private Long poiId;
    private String optionKey;
    private BigDecimal interactionWeight;
    private Boolean successFlag;
    private Integer costMs;
    private String requestUri;
    private String httpMethod;
    private String clientIp;
    private String userAgent;
    private String referer;
    private String extraJson;
    private LocalDateTime eventTime;
}

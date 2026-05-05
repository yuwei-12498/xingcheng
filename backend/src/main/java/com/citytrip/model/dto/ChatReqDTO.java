package com.citytrip.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class ChatReqDTO {
    @NotBlank(message = "question must not be blank")
    @Size(max = 2000, message = "question must be at most 2000 characters")
    private String question;
    @Valid
    private ChatContext context;
    @Valid
    @Size(max = 20, message = "recentMessages must contain at most 20 items")
    private List<ChatMessage> recentMessages;
    @Valid
    private ChatAction action;

    @Data
    public static class ChatContext {
        @Size(max = 64, message = "pageType must be at most 64 characters")
        private String pageType;
        @Size(max = 20, message = "preferences must contain at most 20 items")
        private List<String> preferences;
        private Boolean rainy;
        private Boolean nightMode;
        @Size(max = 64, message = "companionType must be at most 64 characters")
        private String companionType;
        @Size(max = 32, message = "cityCode must be at most 32 characters")
        private String cityCode;
        @Size(max = 64, message = "cityName must be at most 64 characters")
        private String cityName;
        private Double userLat;
        private Double userLng;
        @Valid
        private GenerateReqDTO originalReq;
        @Valid
        private ChatItineraryContext itinerary;
        @Valid
        @Size(max = 20, message = "recentPois must contain at most 20 items")
        private List<ChatRecentPoi> recentPois;
    }

    @Data
    public static class ChatItineraryContext {
        private Long itineraryId;
        @Size(max = 64, message = "selectedOptionKey must be at most 64 characters")
        private String selectedOptionKey;
        @Size(max = 1000, message = "summary must be at most 1000 characters")
        private String summary;
        private Integer totalDuration;
        private BigDecimal totalCost;
        @Valid
        @Size(max = 80, message = "nodes must contain at most 80 items")
        private List<ChatRouteNode> nodes;
    }

    @Data
    public static class ChatRouteNode {
        @Size(max = 64, message = "nodeKey must be at most 64 characters")
        private String nodeKey;
        private Integer dayNo;
        private Integer stepOrder;
        private Long poiId;
        @Size(max = 120, message = "poiName must be at most 120 characters")
        private String poiName;
        @Size(max = 64, message = "category must be at most 64 characters")
        private String category;
        @Size(max = 64, message = "district must be at most 64 characters")
        private String district;
        private String startTime;
        private String endTime;
        private Integer stayDuration;
        private Integer travelTime;
        @Size(max = 32, message = "travelTransportMode must be at most 32 characters")
        private String travelTransportMode;
        private BigDecimal travelDistanceKm;
        private Integer departureTravelTime;
        @Size(max = 32, message = "departureTransportMode must be at most 32 characters")
        private String departureTransportMode;
        private BigDecimal departureDistanceKm;
        private BigDecimal latitude;
        private BigDecimal longitude;
        @Size(max = 32, message = "sourceType must be at most 32 characters")
        private String sourceType;
    }

    @Data
    public static class ChatRecentPoi {
        private Long poiId;
        @Size(max = 120, message = "poiName must be at most 120 characters")
        private String poiName;
        @Size(max = 64, message = "category must be at most 64 characters")
        private String category;
        @Size(max = 64, message = "district must be at most 64 characters")
        private String district;
    }

    @Data
    public static class ChatMessage {
        @Size(max = 16, message = "message role must be at most 16 characters")
        private String role;
        @Size(max = 800, message = "message content must be at most 800 characters")
        private String content;
    }

    @Data
    public static class ChatAction {
        @Size(max = 64, message = "action type must be at most 64 characters")
        private String type;
        @Size(max = 255, message = "proposalToken must be at most 255 characters")
        private String proposalToken;
        @Size(max = 128, message = "clientSessionId must be at most 128 characters")
        private String clientSessionId;
        @Size(max = 80, message = "selectedNodeIds must contain at most 80 items")
        private List<Long> selectedNodeIds;
        @Size(max = 64, message = "selectedOptionKey must be at most 64 characters")
        private String selectedOptionKey;
        @Size(max = 255, message = "selectedValue must be at most 255 characters")
        private String selectedValue;
    }
}

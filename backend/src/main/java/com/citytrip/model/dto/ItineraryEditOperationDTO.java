package com.citytrip.model.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class ItineraryEditOperationDTO {
    @Size(max = 64, message = "operation type must be at most 64 characters")
    private String type;
    @Size(max = 64, message = "nodeKey must be at most 64 characters")
    private String nodeKey;
    @Min(value = 1, message = "dayNo must be positive")
    private Integer dayNo;
    @Min(value = 1, message = "targetDayNo must be positive")
    private Integer targetDayNo;
    @Min(value = 0, message = "targetIndex must not be negative")
    private Integer targetIndex;
    @Min(value = 1, message = "stayDuration must be positive")
    private Integer stayDuration;
    @Pattern(regexp = "^$|^\\d{2}:\\d{2}$", message = "startTime must use HH:mm")
    private String startTime;
    @Pattern(regexp = "^$|^\\d{2}:\\d{2}$", message = "endTime must use HH:mm")
    private String endTime;
    private Long customPoiId;
    @Valid
    private CustomPoiDraft customPoiDraft;

    @Data
    public static class CustomPoiDraft {
        @Size(max = 120, message = "custom POI name must be at most 120 characters")
        private String name;
        @Size(max = 255, message = "roughLocation must be at most 255 characters")
        private String roughLocation;
        @Size(max = 255, message = "reason must be at most 255 characters")
        private String reason;
        @Size(max = 64, message = "category must be at most 64 characters")
        private String category;
        @Min(value = 1, message = "custom POI stayDuration must be positive")
        private Integer stayDuration;
        @Size(max = 255, message = "address must be at most 255 characters")
        private String address;
        @Size(max = 64, message = "district must be at most 64 characters")
        private String district;
        @DecimalMin(value = "-90", message = "latitude must be >= -90")
        @DecimalMax(value = "90", message = "latitude must be <= 90")
        private BigDecimal latitude;
        @DecimalMin(value = "-180", message = "longitude must be >= -180")
        @DecimalMax(value = "180", message = "longitude must be <= 180")
        private BigDecimal longitude;
        @Size(max = 64, message = "geoSource must be at most 64 characters")
        private String geoSource;
    }
}

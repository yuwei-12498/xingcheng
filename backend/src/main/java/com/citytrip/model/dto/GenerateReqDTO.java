package com.citytrip.model.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class GenerateReqDTO {
    @JsonProperty("cityName")
    private String cityName;
    @JsonProperty("cityCode")
    @Size(max = 32, message = "cityCode must be at most 32 characters")
    private String cityCode;

    @DecimalMin(value = "0.5", message = "tripDays must be at least 0.5")
    @DecimalMax(value = "30", message = "tripDays must be at most 30")
    private Double tripDays;
    @Pattern(regexp = "^$|^\\d{4}-\\d{2}-\\d{2}$", message = "tripDate must use yyyy-MM-dd")
    private String tripDate;
    @JsonProperty("totalBudget")
    @DecimalMin(value = "0", message = "totalBudget must not be negative")
    @DecimalMax(value = "1000000", message = "totalBudget is too large")
    private Double totalBudget;
    @Size(max = 32, message = "budgetLevel must be at most 32 characters")
    private String budgetLevel;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Size(max = 12, message = "themes must contain at most 12 items")
    private List<String> themes;
    private Boolean isRainy;
    private Boolean isNight;
    @Size(max = 32, message = "walkingLevel must be at most 32 characters")
    private String walkingLevel;
    @Size(max = 64, message = "companionType must be at most 64 characters")
    private String companionType;
    @Pattern(regexp = "^$|^\\d{2}:\\d{2}$", message = "startTime must use HH:mm")
    private String startTime;
    @Pattern(regexp = "^$|^\\d{2}:\\d{2}$", message = "endTime must use HH:mm")
    private String endTime;
    @JsonFormat(with = JsonFormat.Feature.ACCEPT_SINGLE_VALUE_AS_ARRAY)
    @Size(max = 20, message = "mustVisitPoiNames must contain at most 20 items")
    private List<String> mustVisitPoiNames;
    @Size(max = 1000, message = "naturalLanguageRequirement must be at most 1000 characters")
    private String naturalLanguageRequirement;
    @Size(max = 120, message = "departurePlaceName must be at most 120 characters")
    private String departurePlaceName;
    @DecimalMin(value = "-90", message = "departureLatitude must be >= -90")
    @DecimalMax(value = "90", message = "departureLatitude must be <= 90")
    private Double departureLatitude;
    @DecimalMin(value = "-180", message = "departureLongitude must be >= -180")
    @DecimalMax(value = "180", message = "departureLongitude must be <= 180")
    private Double departureLongitude;
}

package com.citytrip.model.vo;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SmartFillVO {
    private Double tripDays;
    private String tripDate;
    private String startTime;
    private String endTime;
    private String budgetLevel;
    private List<String> themes = new ArrayList<>();
    private Boolean isRainy;
    private Boolean isNight;
    private String walkingLevel;
    private String companionType;
    private List<String> mustVisitPoiNames = new ArrayList<>();
    private String cityName;
    private String departureText;
    private List<String> departureCandidates = new ArrayList<>();
    private Double departureLatitude;
    private Double departureLongitude;
    private List<String> summary = new ArrayList<>();
}

package com.citytrip.model.vo;

import lombok.Data;

@Data
public class ItineraryDayWindowVO {
    private Integer dayNo;
    private String startTime;
    private String endTime;
    private Integer overflowMinutes;
}

package com.citytrip.model.dto;

import com.citytrip.model.vo.ItineraryVO;
import lombok.Data;

@Data
public class ReplanRespDTO {
    private Boolean success;
    private String message;
    private Boolean changed;
    private String reason;
    private ItineraryVO itinerary;
}

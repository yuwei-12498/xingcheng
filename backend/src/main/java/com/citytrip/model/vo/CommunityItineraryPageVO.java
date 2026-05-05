package com.citytrip.model.vo;

import lombok.Data;

import java.util.Collections;
import java.util.List;

@Data
public class CommunityItineraryPageVO {
    private Integer page;
    private Integer size;
    private String sort;
    private Long total;
    private List<String> availableThemes = Collections.emptyList();
    private List<CommunityItineraryVO> pinnedRecords = Collections.emptyList();
    private List<CommunityItineraryVO> records = Collections.emptyList();
}
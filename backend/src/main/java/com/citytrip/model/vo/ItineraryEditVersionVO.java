package com.citytrip.model.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class ItineraryEditVersionVO {
    private Long id;
    private Integer versionNo;
    private String source;
    private String summary;
    private Boolean active;
    private LocalDateTime createTime;
}

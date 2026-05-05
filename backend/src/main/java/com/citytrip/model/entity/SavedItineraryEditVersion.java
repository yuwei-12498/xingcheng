package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@TableName("saved_itinerary_edit_version")
public class SavedItineraryEditVersion {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long itineraryId;
    private Long userId;
    private Integer versionNo;
    private Integer activeFlag;
    private String source;
    private String summary;
    private String requestJson;
    private String itineraryJson;
    private LocalDateTime createTime;
}

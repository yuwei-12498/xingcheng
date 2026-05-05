package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@TableName("user_custom_poi")
public class UserCustomPoi {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long userId;
    private String cityName;
    private String name;
    private String roughLocation;
    private String category;
    private String reason;
    private String address;
    private String district;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private Integer suggestedStayDuration;
    private String geoSource;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;
}

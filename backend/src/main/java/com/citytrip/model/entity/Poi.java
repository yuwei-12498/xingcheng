package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.Map;

@Data
@TableName("poi")
public class Poi {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String cityCode;
    private String cityName;
    private String name;
    private String category;
    private String district;
    private String address;
    private BigDecimal latitude;
    private BigDecimal longitude;
    private LocalTime openTime;
    private LocalTime closeTime;
    private String closedWeekdays;
    private Integer temporarilyClosed;
    private String statusNote;
    private String statusSource;
    private LocalDateTime statusUpdatedAt;
    private BigDecimal avgCost;
    private Integer stayDuration;
    private Integer indoor;
    private Integer nightAvailable;
    private Integer rainFriendly;
    private String walkingLevel;
    private String tags;
    private String suitableFor;
    private String description;
    private BigDecimal priorityScore;
    private BigDecimal crowdPenalty;

    @TableField(exist = false)
    private String sourceType;

    @TableField(exist = false)
    private String externalId;

    @TableField(exist = false)
    private Double tempScore;

    @TableField(exist = false)
    private Map<String, Double> tempScoreBreakdown;

    @TableField(exist = false)
    private Double externalDataCompleteness;

    @TableField(exist = false)
    private Boolean externalBusinessDetailsProvided;

    @TableField(exist = false)
    private String operatingStatus;

    @TableField(exist = false)
    private Boolean availableOnTripDate;

    @TableField(exist = false)
    private Boolean statusStale;

    @TableField(exist = false)
    private String availabilityNote;
}

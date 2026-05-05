package com.citytrip.model.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Data
@TableName("route_plan_fact")
public class RoutePlanFact {

    @TableId(value = "id", type = IdType.AUTO)
    private Long id;

    @TableField("user_id")
    private Long userId;

    @TableField("itinerary_id")
    private Long itineraryId;

    @TableField("plan_source")
    private String planSource;

    @TableField("algorithm_version")
    private String algorithmVersion;

    @TableField("recall_strategy")
    private String recallStrategy;

    @TableField("raw_candidate_count")
    private Integer rawCandidateCount;

    @TableField("filtered_candidate_count")
    private Integer filteredCandidateCount;

    @TableField("final_candidate_count")
    private Integer finalCandidateCount;

    @TableField("max_stops")
    private Integer maxStops;

    @TableField("generated_route_count")
    private Integer generatedRouteCount;

    @TableField("displayed_option_count")
    private Integer displayedOptionCount;

    @TableField("selected_option_key")
    private String selectedOptionKey;

    @TableField("selected_route_signature")
    private String selectedRouteSignature;

    @TableField("total_duration")
    private Integer totalDuration;

    @TableField("total_cost")
    private BigDecimal totalCost;

    @TableField("total_travel_time")
    private Integer totalTravelTime;

    @TableField("business_risk_score")
    private Integer businessRiskScore;

    @TableField("theme_match_count")
    private Integer themeMatchCount;

    @TableField("route_utility")
    private BigDecimal routeUtility;

    @TableField("selected_route_feature_json")
    private String selectedRouteFeatureJson;

    @TableField("options_feature_json")
    private String optionsFeatureJson;

    @TableField("trip_date")
    private LocalDate tripDate;

    @TableField("trip_start_time")
    private LocalTime tripStartTime;

    @TableField("trip_end_time")
    private LocalTime tripEndTime;

    @TableField("budget_level")
    private String budgetLevel;

    @TableField("is_rainy")
    private Integer isRainy;

    @TableField("is_night")
    private Integer isNight;

    @TableField("walking_level")
    private String walkingLevel;

    @TableField("companion_type")
    private String companionType;

    @TableField("themes_json")
    private String themesJson;

    @TableField("request_snapshot_json")
    private String requestSnapshotJson;

    @TableField("replan_from_itinerary_id")
    private Long replanFromItineraryId;

    @TableField("replace_target_poi_id")
    private Long replaceTargetPoiId;

    @TableField("replaced_with_poi_id")
    private Long replacedWithPoiId;

    @TableField("success_flag")
    private Integer successFlag;

    @TableField("fail_reason")
    private String failReason;

    @TableField("planning_started_at")
    private LocalDateTime planningStartedAt;

    @TableField("planning_finished_at")
    private LocalDateTime planningFinishedAt;

    @TableField("cost_ms")
    private Integer costMs;

    @TableField("create_time")
    private LocalDateTime createTime;
}

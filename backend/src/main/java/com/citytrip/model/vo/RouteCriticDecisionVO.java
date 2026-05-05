package com.citytrip.model.vo;

import lombok.Data;

import java.util.Map;

@Data
public class RouteCriticDecisionVO {
    private String selectedOptionKey;
    private String reason;
    private Map<String, String> rejectedReasons;
    private Map<String, Double> optionScores;
}

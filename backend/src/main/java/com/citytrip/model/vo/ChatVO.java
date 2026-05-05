package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class ChatVO {
    private String answer;
    private List<String> relatedTips;
    private List<String> evidence;
    private ChatSkillPayloadVO skillPayload;
}


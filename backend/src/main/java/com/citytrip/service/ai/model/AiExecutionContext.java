package com.citytrip.service.ai.model;

import lombok.Builder;
import lombok.Value;

import java.util.List;

@Value
@Builder
public class AiExecutionContext {
    AiScene scene;
    String userInput;
    Long userId;
    Long itineraryId;
    String cityName;
    List<String> recentMessages;
    List<String> recentPoiNames;
    String routeSummary;
    boolean ragEnabled;
    boolean toolEnabled;
    boolean mcpEnabled;
}

package com.citytrip.service.ai.orchestrator;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;

public class AiExecutionContextFactory {

    public AiExecutionContext create(AiScene scene, String userInput) {
        return AiExecutionContext.builder()
                .scene(scene)
                .userInput(userInput)
                .build();
    }
}

package com.citytrip.service.ai.orchestrator;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;

public class AiSceneRouter {

    public AiScene route(AiExecutionContext context) {
        return context == null || context.getScene() == null ? AiScene.CHAT_QA : context.getScene();
    }
}

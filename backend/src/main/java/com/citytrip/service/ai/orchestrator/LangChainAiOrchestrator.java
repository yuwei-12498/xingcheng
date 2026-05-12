package com.citytrip.service.ai.orchestrator;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;

public class LangChainAiOrchestrator {
    private final AiSceneRouter sceneRouter;

    public LangChainAiOrchestrator(AiSceneRouter sceneRouter) {
        this.sceneRouter = sceneRouter;
    }

    public AiScene resolveScene(AiExecutionContext context) {
        return sceneRouter.route(context);
    }
}

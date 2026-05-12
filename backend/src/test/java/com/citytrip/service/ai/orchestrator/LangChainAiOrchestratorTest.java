package com.citytrip.service.ai.orchestrator;

import com.citytrip.service.ai.model.AiExecutionContext;
import com.citytrip.service.ai.model.AiScene;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LangChainAiOrchestratorTest {

    @Test
    void shouldRouteSmartFillSceneWithoutTouchingLegacyAlgorithmChain() {
        AiExecutionContext context = AiExecutionContext.builder()
                .scene(AiScene.SMART_FILL)
                .userInput("我想去万象城，不想太累")
                .build();

        AiSceneRouter router = new AiSceneRouter();
        assertEquals(AiScene.SMART_FILL, router.route(context));
    }
}

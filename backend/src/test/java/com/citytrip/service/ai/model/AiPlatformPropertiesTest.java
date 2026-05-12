package com.citytrip.service.ai.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiPlatformPropertiesTest {

    @Test
    void defaultsShouldEnableOrchestratorFlagsWithoutBreakingLegacyLlmConfig() {
        AiPlatformProperties properties = new AiPlatformProperties();

        assertTrue(properties.getOrchestrator().isEnabled());
        assertTrue(properties.getRag().isEnabled());
        assertTrue(properties.getMcp().isEnabled());
        assertTrue(properties.getTools().isEnabled());
        assertEquals("chengdu", properties.getCityGuide().getDefaultCityKey());
        assertEquals(6, properties.getRetrieval().getMaxDocuments());
        assertEquals(2, properties.getRetrieval().getMaxPerSource());
    }
}

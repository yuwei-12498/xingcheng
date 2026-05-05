package com.citytrip.service.impl;

import com.citytrip.service.impl.vivo.VivoFunctionCallingService;
import com.citytrip.service.impl.vivo.VivoToolDefinition;
import com.citytrip.service.impl.vivo.VivoToolRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class VivoFunctionCallingServiceTest {

    @Test
    void shouldRejectUnknownTool() {
        VivoFunctionCallingService service = new VivoFunctionCallingService(new VivoToolRegistry());

        assertThatThrownBy(() -> service.executeToolCall("unknown_tool", "{}"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown_tool");
    }

    @Test
    void shouldWrapToolFailureIntoStructuredResult() {
        VivoToolRegistry registry = new VivoToolRegistry();
        registry.register(new VivoToolDefinition(
                "search_poi",
                "search poi",
                "{}",
                arguments -> {
                    throw new IllegalStateException("boom");
                }
        ));
        VivoFunctionCallingService service = new VivoFunctionCallingService(registry);

        String payload = service.executeToolCall("search_poi", "{\"keyword\":\"太古里\"}");

        assertThat(payload).contains("\"status\":\"error\"");
        assertThat(payload).contains("\"tool\":\"search_poi\"");
    }
}

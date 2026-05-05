package com.citytrip.service.guard;

import com.citytrip.common.SystemBusyException;
import com.citytrip.config.AiRequestGuardProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AiRequestGuardTest {

    @Test
    void rejectsWhenSceneConcurrencyIsSaturated() {
        AiRequestGuardProperties properties = new AiRequestGuardProperties();
        properties.getChat().setMaxConcurrent(1);
        properties.getChat().setAcquireTimeoutMs(1);
        AiRequestGuard guard = new AiRequestGuard(properties);

        try (AiRequestGuard.GuardPermit ignored = guard.acquire("chat", "user:1")) {
            assertThatThrownBy(() -> guard.acquire("chat", "user:2"))
                    .isInstanceOf(SystemBusyException.class)
                    .hasMessageContaining("chat");
        }
    }

    @Test
    void rejectsRapidRepeatedRequestsForSameSubject() {
        AiRequestGuardProperties properties = new AiRequestGuardProperties();
        properties.getSmartFill().setCooldownMs(10_000);
        AiRequestGuard guard = new AiRequestGuard(properties);

        guard.call("smart-fill", "anon:127.0.0.1", () -> "ok");

        assertThatThrownBy(() -> guard.call("smart-fill", "anon:127.0.0.1", () -> "too-fast"))
                .isInstanceOf(SystemBusyException.class)
                .hasMessageContaining("too frequent");
    }
}

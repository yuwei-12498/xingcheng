package com.citytrip.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "app.ai-guard")
public class AiRequestGuardProperties {

    private boolean enabled = true;
    private SceneGuardProperties chat = new SceneGuardProperties(6, 250, 0);
    private SceneGuardProperties stream = new SceneGuardProperties(4, 500, 0);
    private SceneGuardProperties smartFill = new SceneGuardProperties(4, 800, 0);
    private SceneGuardProperties generate = new SceneGuardProperties(3, 1200, 0);

    public SceneGuardProperties resolve(String sceneName) {
        String normalized = sceneName == null ? "" : sceneName.trim().toLowerCase();
        return switch (normalized) {
            case "chat" -> chat;
            case "stream", "chat-stream" -> stream;
            case "smart-fill", "smartfill" -> smartFill;
            case "generate", "itinerary-generate" -> generate;
            default -> chat;
        };
    }

    @Data
    public static class SceneGuardProperties {
        private boolean enabled = true;
        private int maxConcurrent = 4;
        private long cooldownMs = 0;
        private long acquireTimeoutMs = 0;

        public SceneGuardProperties() {
        }

        public SceneGuardProperties(int maxConcurrent, long cooldownMs, long acquireTimeoutMs) {
            this.maxConcurrent = maxConcurrent;
            this.cooldownMs = cooldownMs;
            this.acquireTimeoutMs = acquireTimeoutMs;
        }

        public int safeMaxConcurrent() {
            return Math.max(1, maxConcurrent);
        }

        public long safeCooldownMs() {
            return Math.max(0, cooldownMs);
        }

        public long safeAcquireTimeoutMs() {
            return Math.max(0, acquireTimeoutMs);
        }
    }
}

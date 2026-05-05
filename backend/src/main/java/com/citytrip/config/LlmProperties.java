package com.citytrip.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@ConfigurationProperties(prefix = "llm")
public class LlmProperties {
    private static final Set<String> VIVO_ALLOWED_MODELS = Set.of(
            "Volc-DeepSeek-V3.2",
            "Doubao-Seed-2.0-mini",
            "Doubao-Seed-2.0-lite",
            "Doubao-Seed-2.0-pro",
            "qwen3.5-plus"
    );

    private String provider = "real";
    private boolean fallbackToMock = false;
    private int timeoutSeconds = 20;
    private int connectTimeoutSeconds = 3;
    private int readTimeoutSeconds = 0;
    private FeatureFlags features = new FeatureFlags();
    private OpenAiProperties openai = new OpenAiProperties();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isFallbackToMock() {
        return fallbackToMock;
    }

    public void setFallbackToMock(boolean fallbackToMock) {
        this.fallbackToMock = fallbackToMock;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public int getConnectTimeoutSeconds() {
        return connectTimeoutSeconds;
    }

    public void setConnectTimeoutSeconds(int connectTimeoutSeconds) {
        this.connectTimeoutSeconds = connectTimeoutSeconds;
    }

    public int getReadTimeoutSeconds() {
        return readTimeoutSeconds;
    }

    public void setReadTimeoutSeconds(int readTimeoutSeconds) {
        this.readTimeoutSeconds = readTimeoutSeconds;
    }

    public FeatureFlags getFeatures() {
        return features;
    }

    public void setFeatures(FeatureFlags features) {
        this.features = features == null ? new FeatureFlags() : features;
    }

    public OpenAiProperties getOpenai() {
        return openai;
    }

    public void setOpenai(OpenAiProperties openai) {
        this.openai = openai == null ? new OpenAiProperties() : openai;
    }

    public boolean isMockOnly() {
        return "mock".equalsIgnoreCase(provider);
    }

    public boolean isRealOnly() {
        return "real".equalsIgnoreCase(provider);
    }

    public boolean isAuto() {
        return "auto".equalsIgnoreCase(provider);
    }

    public int resolveConnectTimeoutSeconds() {
        return Math.max(connectTimeoutSeconds, 1);
    }

    public int resolveReadTimeoutSeconds() {
        return Math.max(readTimeoutSeconds > 0 ? readTimeoutSeconds : timeoutSeconds, 1);
    }

    public boolean canTryReal() {
        return canTryRealChat() && canTryRealText();
    }

    public boolean canTryRealChat() {
        return getRealChatConfigIssues().isEmpty();
    }

    public boolean canTryRealText() {
        return getRealTextConfigIssues().isEmpty();
    }

    public boolean canTryRealTool() {
        return getRealToolConfigIssues().isEmpty();
    }

    public static boolean isVivoAllowedModel(String model) {
        return model != null && !model.trim().isEmpty() && VIVO_ALLOWED_MODELS.contains(model.trim());
    }

    public List<String> getRealModelConfigIssues() {
        List<String> issues = new ArrayList<>();
        issues.addAll(getRealChatConfigIssues());
        issues.addAll(getRealTextConfigIssues());
        issues.addAll(getRealToolConfigIssues());
        return issues.stream().distinct().toList();
    }

    public List<String> getRealChatConfigIssues() {
        return getRealSceneConfigIssues(openai == null ? null : openai.resolveChatOptions(), "chat");
    }

    public List<String> getRealTextConfigIssues() {
        return getRealSceneConfigIssues(openai == null ? null : openai.resolveTextOptions(), "text");
    }

    public List<String> getRealToolConfigIssues() {
        return getRealSceneConfigIssues(openai == null ? null : openai.resolveToolOptions(), "tool");
    }

    private List<String> getRealSceneConfigIssues(ResolvedOpenAiOptions options, String scene) {
        List<String> issues = new ArrayList<>();
        if (isMockOnly()) {
            issues.add("llm.provider=mock");
            return issues;
        }
        if (openai == null || options == null) {
            issues.add("llm.openai is missing");
            return issues;
        }
        if (!openai.isEnabled()) {
            issues.add("llm.openai.enabled=false");
        }
        if (!hasText(openai.getApiKey())) {
            issues.add("OPENAI_API_KEY is empty");
        }
        if (!hasText(options.getBaseUrl())) {
            issues.add("OPENAI_" + scene.toUpperCase(Locale.ROOT) + "_BASE_URL is empty");
        }
        if (!hasText(options.getModel())) {
            issues.add("OPENAI_" + scene.toUpperCase(Locale.ROOT) + "_MODEL is empty");
        }
        return issues;
    }

    public List<String> getRealModelConfigWarnings() {
        List<String> warnings = new ArrayList<>();
        if (openai == null) {
            return warnings;
        }
        if (looksLikePlaceholderApiKey(openai.getApiKey())) {
            warnings.add("OPENAI_API_KEY looks like a placeholder value");
        }
        if (looksLikeApiKey(openai.getModel())) {
            warnings.add("OPENAI_MODEL looks like an API key; check whether model/key are swapped");
        }
        if (looksLikeApiKey(openai.resolveChatOptions().getModel())) {
            warnings.add("OPENAI_CHAT_MODEL looks like an API key; check whether model/key are swapped");
        }
        if (looksLikeApiKey(openai.resolveTextOptions().getModel())) {
            warnings.add("OPENAI_TEXT_MODEL looks like an API key; check whether model/key are swapped");
        }
        if (looksLikeApiKey(openai.resolveToolOptions().getModel())) {
            warnings.add("OPENAI_TOOL_MODEL looks like an API key; check whether model/key are swapped");
        }
        appendVivoAllowListWarning(warnings, "OPENAI_MODEL", openai.getModel(), openai.getBaseUrl());
        appendVivoAllowListWarning(warnings, "OPENAI_CHAT_MODEL", openai.resolveChatOptions().getModel(), openai.resolveChatOptions().getBaseUrl());
        appendVivoAllowListWarning(warnings, "OPENAI_TEXT_MODEL", openai.resolveTextOptions().getModel(), openai.resolveTextOptions().getBaseUrl());
        appendVivoAllowListWarning(warnings, "OPENAI_TOOL_MODEL", openai.resolveToolOptions().getModel(), openai.resolveToolOptions().getBaseUrl());
        return warnings;
    }

    private void appendVivoAllowListWarning(List<String> warnings, String key, String model, String baseUrl) {
        if (!looksLikeVivoBaseUrl(baseUrl) || !hasText(model)) {
            return;
        }
        if (!isVivoAllowedModel(model)) {
            warnings.add(key + " is not in vivo allow-list");
        }
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    private boolean looksLikePlaceholderApiKey(String value) {
        if (!hasText(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        return "example-api-key".equals(normalized)
                || "test-api-key".equals(normalized)
                || "your-api-key".equals(normalized)
                || "your_openai_api_key".equals(normalized)
                || "replace-me".equals(normalized);
    }

    private boolean looksLikeApiKey(String value) {
        return hasText(value) && value.trim().toLowerCase(Locale.ROOT).startsWith("sk-");
    }

    private boolean looksLikeVivoBaseUrl(String value) {
        return hasText(value) && value.toLowerCase(Locale.ROOT).contains("api-ai.vivo.com.cn");
    }

    public static class FeatureFlags {
        private boolean chatOnlineEnabled = true;
        private boolean toolLoopEnabled = true;
        private boolean poiLiveEnabled = true;
        private boolean semanticOnlineEnabled = true;
        private boolean embeddingOnlineEnabled = true;
        private boolean rerankOnlineEnabled = true;
        private boolean failOpenEnabled = true;
        private boolean preferAccuracyMode = true;

        public boolean isChatOnlineEnabled() {
            return chatOnlineEnabled;
        }

        public void setChatOnlineEnabled(boolean chatOnlineEnabled) {
            this.chatOnlineEnabled = chatOnlineEnabled;
        }

        public boolean isToolLoopEnabled() {
            return toolLoopEnabled;
        }

        public void setToolLoopEnabled(boolean toolLoopEnabled) {
            this.toolLoopEnabled = toolLoopEnabled;
        }

        public boolean isPoiLiveEnabled() {
            return poiLiveEnabled;
        }

        public void setPoiLiveEnabled(boolean poiLiveEnabled) {
            this.poiLiveEnabled = poiLiveEnabled;
        }

        public boolean isSemanticOnlineEnabled() {
            return semanticOnlineEnabled;
        }

        public void setSemanticOnlineEnabled(boolean semanticOnlineEnabled) {
            this.semanticOnlineEnabled = semanticOnlineEnabled;
        }

        public boolean isEmbeddingOnlineEnabled() {
            return embeddingOnlineEnabled;
        }

        public void setEmbeddingOnlineEnabled(boolean embeddingOnlineEnabled) {
            this.embeddingOnlineEnabled = embeddingOnlineEnabled;
        }

        public boolean isRerankOnlineEnabled() {
            return rerankOnlineEnabled;
        }

        public void setRerankOnlineEnabled(boolean rerankOnlineEnabled) {
            this.rerankOnlineEnabled = rerankOnlineEnabled;
        }

        public boolean isFailOpenEnabled() {
            return failOpenEnabled;
        }

        public void setFailOpenEnabled(boolean failOpenEnabled) {
            this.failOpenEnabled = failOpenEnabled;
        }

        public boolean isPreferAccuracyMode() {
            return preferAccuracyMode;
        }

        public void setPreferAccuracyMode(boolean preferAccuracyMode) {
            this.preferAccuracyMode = preferAccuracyMode;
        }
    }

    public static class OpenAiProperties {
        private boolean enabled = true;
        private String apiKey;
        private String baseUrl = "https://api.openai.com/v1";
        private String model = "gpt-5.4";
        private double temperature = 0.7D;
        private Integer maxOutputTokens = 600;
        private SceneProperties chat = new SceneProperties();
        private SceneProperties text = new SceneProperties();
        private SceneProperties tool = new SceneProperties();

        public boolean isEnabled() {
            return enabled;
        }

        public void setEnabled(boolean enabled) {
            this.enabled = enabled;
        }

        public String getApiKey() {
            return apiKey;
        }

        public void setApiKey(String apiKey) {
            this.apiKey = apiKey;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public double getTemperature() {
            return temperature;
        }

        public void setTemperature(double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }

        public SceneProperties getChat() {
            return chat;
        }

        public void setChat(SceneProperties chat) {
            this.chat = chat;
        }

        public SceneProperties getText() {
            return text;
        }

        public void setText(SceneProperties text) {
            this.text = text;
        }

        public SceneProperties getTool() {
            return tool;
        }

        public void setTool(SceneProperties tool) {
            this.tool = tool;
        }

        public ResolvedOpenAiOptions resolveChatOptions() {
            return resolveSceneOptions(chat, true);
        }

        public ResolvedOpenAiOptions resolveTextOptions() {
            return resolveSceneOptions(text, true);
        }

        public ResolvedOpenAiOptions resolveToolOptions() {
            return resolveSceneOptions(tool, false);
        }

        private ResolvedOpenAiOptions resolveSceneOptions(SceneProperties scene, boolean inheritDefaultModel) {
            SceneProperties resolvedScene = scene == null ? new SceneProperties() : scene;
            String resolvedBaseUrl = hasText(resolvedScene.getBaseUrl()) ? resolvedScene.getBaseUrl() : baseUrl;
            String resolvedModel = hasText(resolvedScene.getModel())
                    ? resolvedScene.getModel()
                    : (inheritDefaultModel ? model : null);
            Double resolvedTemperature = resolvedScene.getTemperature() != null ? resolvedScene.getTemperature() : temperature;
            Integer resolvedMaxOutputTokens = positiveOrNull(resolvedScene.getMaxOutputTokens());
            if (resolvedMaxOutputTokens == null) {
                resolvedMaxOutputTokens = positiveOrNull(maxOutputTokens);
            }
            return new ResolvedOpenAiOptions(resolvedBaseUrl, resolvedModel, resolvedTemperature, resolvedMaxOutputTokens);
        }

        private boolean hasText(String value) {
            return value != null && !value.trim().isEmpty();
        }

        private Integer positiveOrNull(Integer value) {
            return value != null && value > 0 ? value : null;
        }
    }

    public static class SceneProperties {
        private String baseUrl;
        private String model;
        private Double temperature;
        private Integer maxOutputTokens;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }

        public void setMaxOutputTokens(Integer maxOutputTokens) {
            this.maxOutputTokens = maxOutputTokens;
        }
    }

    public static class ResolvedOpenAiOptions {
        private final String baseUrl;
        private final String model;
        private final double temperature;
        private final Integer maxOutputTokens;

        public ResolvedOpenAiOptions(String baseUrl, String model, double temperature, Integer maxOutputTokens) {
            this.baseUrl = baseUrl;
            this.model = model;
            this.temperature = temperature;
            this.maxOutputTokens = maxOutputTokens;
        }

        public String getBaseUrl() {
            return baseUrl;
        }

        public String getModel() {
            return model;
        }

        public double getTemperature() {
            return temperature;
        }

        public Integer getMaxOutputTokens() {
            return maxOutputTokens;
        }
    }
}

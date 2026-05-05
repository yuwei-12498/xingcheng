package com.citytrip.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class LlmStartupDiagnostics implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LlmStartupDiagnostics.class);

    private final LlmProperties llmProperties;

    public LlmStartupDiagnostics(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    @Override
    public void run(ApplicationArguments args) {
        LlmProperties.OpenAiProperties openai = llmProperties.getOpenai();
        LlmProperties.ResolvedOpenAiOptions chatOptions = openai == null ? null : openai.resolveChatOptions();
        LlmProperties.ResolvedOpenAiOptions textOptions = openai == null ? null : openai.resolveTextOptions();
        String maskedKey = openai == null ? "(missing)" : maskKey(openai.getApiKey());

        log.info("LLM startup config: provider={}, fallbackToMock={}, connectTimeout={}s, readTimeout={}s, chatModel={}, chatBaseUrl={}, textModel={}, textBaseUrl={}, apiKey={}",
                llmProperties.getProvider(),
                llmProperties.isFallbackToMock(),
                llmProperties.resolveConnectTimeoutSeconds(),
                llmProperties.resolveReadTimeoutSeconds(),
                chatOptions == null ? "(missing)" : safe(chatOptions.getModel()),
                chatOptions == null ? "(missing)" : safe(chatOptions.getBaseUrl()),
                textOptions == null ? "(missing)" : safe(textOptions.getModel()),
                textOptions == null ? "(missing)" : safe(textOptions.getBaseUrl()),
                maskedKey);

        if (llmProperties.isMockOnly()) {
            log.info("LLM provider is mock; real model validation is skipped.");
            return;
        }

        List<String> chatIssues = llmProperties.getRealChatConfigIssues();
        if (!chatIssues.isEmpty()) {
            log.warn("LLM real chat config issues: {}", String.join("; ", chatIssues));
        } else {
            log.info("LLM real chat config check passed.");
        }

        List<String> textIssues = llmProperties.getRealTextConfigIssues();
        if (!textIssues.isEmpty()) {
            log.warn("LLM real text config issues: {}", String.join("; ", textIssues));
        } else {
            log.info("LLM real text config check passed.");
        }

        List<String> warnings = llmProperties.getRealModelConfigWarnings();
        if (!warnings.isEmpty()) {
            log.warn("LLM real model config warnings: {}", String.join("; ", warnings));
        }
    }

    private String safe(String value) {
        return value == null || value.trim().isEmpty() ? "(empty)" : value.trim();
    }

    private String maskKey(String key) {
        if (key == null || key.trim().isEmpty()) {
            return "(empty)";
        }
        String value = key.trim();
        if (value.length() <= 8) {
            return "****";
        }
        return value.substring(0, 4) + "****" + value.substring(value.length() - 4);
    }
}

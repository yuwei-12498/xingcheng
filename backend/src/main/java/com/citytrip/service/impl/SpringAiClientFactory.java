package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.http.client.reactive.JdkClientHttpConnector;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.reactive.function.client.WebClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

@Component
public class SpringAiClientFactory {

    private final LlmProperties llmProperties;
    private final ConcurrentMap<String, ChatClient> chatClientCache = new ConcurrentHashMap<>();

    public SpringAiClientFactory(LlmProperties llmProperties) {
        this.llmProperties = llmProperties;
    }

    public ChatClient getOrCreate(LlmProperties.ResolvedOpenAiOptions options) {
        String cacheKey = buildCacheKey(options);
        return chatClientCache.computeIfAbsent(cacheKey, key -> buildChatClient(options));
    }

    private ChatClient buildChatClient(LlmProperties.ResolvedOpenAiOptions options) {
        OpenAiApi openAiApi = OpenAiApi.builder()
                .baseUrl(normalizeBaseUrl(options.getBaseUrl()))
                .apiKey(llmProperties.getOpenai().getApiKey())
                .completionsPath("/chat/completions")
                .embeddingsPath("/embeddings")
                .restClientBuilder(buildRestClientBuilder())
                .webClientBuilder(buildWebClientBuilder())
                .build();

        OpenAiChatModel chatModel = OpenAiChatModel.builder()
                .openAiApi(openAiApi)
                .defaultOptions(buildDefaultOptions(options))
                .observationRegistry(ObservationRegistry.NOOP)
                .build();

        return ChatClient.create(chatModel);
    }

    private OpenAiChatOptions buildDefaultOptions(LlmProperties.ResolvedOpenAiOptions options) {
        return OpenAiChatOptions.builder()
                .model(options.getModel())
                .temperature(options.getTemperature())
                .build();
    }

    private RestClient.Builder buildRestClientBuilder() {
        HttpClient httpClient = buildHttpClient();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(Duration.ofSeconds(llmProperties.resolveReadTimeoutSeconds()));
        return RestClient.builder().requestFactory(requestFactory);
    }

    private WebClient.Builder buildWebClientBuilder() {
        HttpClient httpClient = buildHttpClient();
        JdkClientHttpConnector connector = new JdkClientHttpConnector(httpClient);
        connector.setReadTimeout(Duration.ofSeconds(llmProperties.resolveReadTimeoutSeconds()));
        return WebClient.builder().clientConnector(connector);
    }

    private HttpClient buildHttpClient() {
        return HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(llmProperties.resolveConnectTimeoutSeconds()))
                .build();
    }

    private String buildCacheKey(LlmProperties.ResolvedOpenAiOptions options) {
        int keyHash = Objects.hash(
                normalizeBaseUrl(options.getBaseUrl()),
                options.getModel(),
                options.getTemperature(),
                llmProperties.resolveConnectTimeoutSeconds(),
                llmProperties.resolveReadTimeoutSeconds(),
                llmProperties.getOpenai() == null ? null : llmProperties.getOpenai().getApiKey()
        );
        return Integer.toHexString(keyHash);
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.trim().isEmpty()) {
            return "https://api.openai.com/v1";
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

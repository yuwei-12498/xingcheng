package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.citytrip.service.impl.vivo.VivoRequestIdFactory;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

@Component
public class OpenAiGatewayClient {
    private static final Logger log = LoggerFactory.getLogger(OpenAiGatewayClient.class);
    public static final String TOOL_CALL_PREFIX = "__tool_calls__:";

    private final LlmProperties llmProperties;
    private final ObjectMapper objectMapper;
    private final VivoRequestIdFactory vivoRequestIdFactory;

    @Autowired
    public OpenAiGatewayClient(LlmProperties llmProperties, ObjectMapper objectMapper) {
        this(llmProperties, objectMapper, new VivoRequestIdFactory());
    }

    public OpenAiGatewayClient(LlmProperties llmProperties,
                               ObjectMapper objectMapper,
                               VivoRequestIdFactory vivoRequestIdFactory) {
        this.llmProperties = llmProperties;
        this.objectMapper = objectMapper;
        this.vivoRequestIdFactory = vivoRequestIdFactory;
    }

    public String request(LlmProperties.ResolvedOpenAiOptions options,
                          String apiKey,
                          List<OpenAiMessage> messages) {
        OpenAiChatRequest request = buildRequest(options, messages, false);
        return executeWithRetry(options, apiKey, request, null);
    }

    public String stream(LlmProperties.ResolvedOpenAiOptions options,
                         String apiKey,
                         List<OpenAiMessage> messages,
                         Consumer<String> tokenConsumer) {
        OpenAiChatRequest request = buildRequest(options, messages, true);
        return executeWithRetry(options, apiKey, request, tokenConsumer);
    }

    private String executeWithRetry(LlmProperties.ResolvedOpenAiOptions options,
                                    String apiKey,
                                    OpenAiChatRequest request,
                                    Consumer<String> tokenConsumer) {
        try {
            return execute(options, apiKey, request, tokenConsumer);
        } catch (OpenAiGatewayException ex) {
            if (shouldRetryWithoutMaxTokens(request, ex)) {
                log.info("Gateway rejected token limit field, retrying without max_tokens. model={}, endpoint={}",
                        request.getModel(), normalizeBaseUrl(options.getBaseUrl()) + "/chat/completions");
                request.setMaxTokens(null);
                return execute(options, apiKey, request, tokenConsumer);
            }
            throw ex;
        } catch (IllegalStateException ex) {
            if (shouldRetryWithoutStreaming(request, ex, options)) {
                log.info("Gateway stream returned no visible content, retrying non-stream. model={}, endpoint={}",
                        request.getModel(), normalizeBaseUrl(options.getBaseUrl()) + "/chat/completions");
                return retryWithoutStreaming(options, apiKey, request, tokenConsumer);
            }
            throw ex;
        }
    }

    private boolean shouldRetryWithoutMaxTokens(OpenAiChatRequest request, OpenAiGatewayException ex) {
        if (request.getMaxTokens() == null || ex.getStatusCode() != 400) {
            return false;
        }
        String body = ex.getResponseBody();
        if (body == null) {
            return false;
        }
        String lower = body.toLowerCase(Locale.ROOT);
        return lower.contains("max_tokens")
                || lower.contains("max completion tokens")
                || lower.contains("max_completion_tokens")
                || lower.contains("unsupported parameter")
                || lower.contains("unknown field");
    }

    private boolean shouldRetryWithoutStreaming(OpenAiChatRequest request,
                                                IllegalStateException ex,
                                                LlmProperties.ResolvedOpenAiOptions options) {
        if (!Boolean.TRUE.equals(request.getStream()) || ex == null) {
            return false;
        }
        String message = ex.getMessage();
        if (!hasText(message) || !message.contains("OpenAI message content is empty")) {
            return false;
        }
        return looksLikeVivoBaseUrl(options.getBaseUrl())
                || LlmProperties.isVivoAllowedModel(request.getModel())
                || looksLikeMimoTarget(options.getBaseUrl())
                || looksLikeMimoTarget(request.getModel());
    }

    private String retryWithoutStreaming(LlmProperties.ResolvedOpenAiOptions options,
                                         String apiKey,
                                         OpenAiChatRequest request,
                                         Consumer<String> tokenConsumer) {
        request.setStream(false);
        String content = execute(options, apiKey, request, null);
        if (hasText(content) && tokenConsumer != null) {
            tokenConsumer.accept(content.trim());
        }
        return content;
    }

    private String execute(LlmProperties.ResolvedOpenAiOptions options,
                           String apiKey,
                           OpenAiChatRequest request,
                           Consumer<String> tokenConsumer) {
        String endpoint = buildChatEndpoint(options, request.getModel());
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(endpoint).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(llmProperties.resolveConnectTimeoutSeconds() * 1000);
            connection.setReadTimeout(llmProperties.resolveReadTimeoutSeconds() * 1000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", request.getStream() ? "text/event-stream" : "application/json");
            connection.setRequestProperty("Authorization", "Bearer " + apiKey.trim());
            if (shouldSendApiKeyHeader(options.getBaseUrl(), request.getModel())) {
                connection.setRequestProperty("api-key", apiKey.trim());
            }
            connection.setRequestProperty("User-Agent", "CityTripBackend/1.0");

            byte[] requestBody = objectMapper.writeValueAsBytes(request);
            try (OutputStream outputStream = connection.getOutputStream()) {
                outputStream.write(requestBody);
            }

            int statusCode = connection.getResponseCode();
            if (statusCode < 200 || statusCode >= 300) {
                String errorBody = readAll(connection.getErrorStream());
                throw OpenAiGatewayException.http(statusCode, errorBody, endpoint, request.getModel());
            }

            if (Boolean.TRUE.equals(request.getStream())) {
                try (InputStream inputStream = connection.getInputStream()) {
                    return readStreamingContent(inputStream, tokenConsumer);
                }
            }

            String responseBody = readAll(connection.getInputStream());
            String providerError = extractProviderError(responseBody);
            if (hasText(providerError)) {
                throw new IllegalStateException(providerError);
            }
            String content = extractJsonContent(responseBody);
            if (!hasText(content)) {
                throw new IllegalStateException("OpenAI message content is empty");
            }
            return content.trim();
        } catch (SocketTimeoutException ex) {
            throw OpenAiGatewayException.timeout(endpoint, request.getModel(), ex);
        } catch (OpenAiGatewayException ex) {
            throw ex;
        } catch (IOException ex) {
            throw OpenAiGatewayException.network(endpoint, request.getModel(), ex);
        } catch (Exception ex) {
            throw new IllegalStateException("Model request failed. model=" + request.getModel()
                    + ", endpoint=" + endpoint + ", reason=" + ex.getMessage(), ex);
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private OpenAiChatRequest buildRequest(LlmProperties.ResolvedOpenAiOptions options,
                                           List<OpenAiMessage> messages,
                                           boolean stream) {
        OpenAiChatRequest request = new OpenAiChatRequest();
        request.setModel(options.getModel());
        request.setTemperature(options.getTemperature());
        request.setMessages(messages);
        request.setStream(stream);
        request.setMaxTokens(options.getMaxOutputTokens());
        return request;
    }

    private String readStreamingContent(InputStream inputStream, Consumer<String> tokenConsumer) throws IOException {
        StringBuilder finalContent = new StringBuilder();
        StringBuilder rawBody = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                rawBody.append(line).append('\n');
                String token = extractStreamToken(line);
                if (hasText(token)) {
                    finalContent.append(token);
                    if (tokenConsumer != null) {
                        tokenConsumer.accept(token);
                    }
                }
            }
        }

        String content = finalContent.toString();
        if (!hasText(content)) {
            String providerError = extractProviderError(rawBody.toString());
            if (hasText(providerError)) {
                throw new IllegalStateException(providerError);
            }
            content = extractJsonContent(rawBody.toString());
            if (hasText(content) && tokenConsumer != null) {
                tokenConsumer.accept(content.trim());
            }
        }
        if (!hasText(content)) {
            throw new IllegalStateException("OpenAI message content is empty");
        }
        return content.trim();
    }

    private String extractStreamToken(String rawLine) {
        if (!hasText(rawLine)) {
            return null;
        }
        String line = rawLine.trim();
        if (!line.startsWith("data: ") || "data: [DONE]".equals(line)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(line.substring(6));
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }
            JsonNode contentNode = choices.get(0).path("delta").path("content");
            if (contentNode.isMissingNode() || contentNode.isNull()) {
                return null;
            }
            return contentNode.asText();
        } catch (Exception ex) {
            log.warn("Failed to parse SSE line: {}", line, ex);
            return null;
        }
    }

    private String extractJsonContent(String rawBody) {
        if (!hasText(rawBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                return null;
            }

            JsonNode messageContent = choices.get(0).path("message").path("content");
            if (!messageContent.isMissingNode() && !messageContent.isNull()) {
                return messageContent.asText();
            }

            JsonNode toolCalls = choices.get(0).path("message").path("tool_calls");
            if (toolCalls.isArray() && !toolCalls.isEmpty()) {
                return TOOL_CALL_PREFIX + toolCalls.toString();
            }

            JsonNode deltaContent = choices.get(0).path("delta").path("content");
            if (!deltaContent.isMissingNode() && !deltaContent.isNull()) {
                return deltaContent.asText();
            }
            return null;
        } catch (Exception ex) {
            return extractContentFromSseDump(rawBody);
        }
    }

    private String extractProviderError(String rawBody) {
        if (!hasText(rawBody)) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(rawBody);
            JsonNode error = root.path("error");
            if (error.isMissingNode() || error.isNull()) {
                return null;
            }

            String code = error.path("code").asText("");
            String message = error.path("message").asText("");
            if (!hasText(code) && !hasText(message)) {
                return null;
            }

            StringBuilder builder = new StringBuilder("Model provider error");
            if (hasText(code)) {
                builder.append(" [").append(code.trim()).append(']');
            }
            if (hasText(message)) {
                builder.append(": ").append(message.trim());
            }
            return builder.toString();
        } catch (Exception ex) {
            return null;
        }
    }

    private String extractContentFromSseDump(String rawBody) {
        StringBuilder content = new StringBuilder();
        for (String line : rawBody.split("\\r?\\n")) {
            String token = extractStreamToken(line);
            if (hasText(token)) {
                content.append(token);
            }
        }
        return content.length() == 0 ? null : content.toString();
    }

    private String readAll(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }
        }
        return sb.toString().trim();
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!hasText(baseUrl)) {
            return "https://api.openai.com";
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    private String buildChatEndpoint(LlmProperties.ResolvedOpenAiOptions options, String model) {
        String endpoint = normalizeBaseUrl(options.getBaseUrl()) + "/chat/completions";
        if (!shouldAttachVivoRequestId(options.getBaseUrl(), model)) {
            return endpoint;
        }
        return appendQueryParam(endpoint, "requestId", vivoRequestIdFactory.create());
    }

    private boolean shouldAttachVivoRequestId(String baseUrl, String model) {
        return looksLikeVivoBaseUrl(baseUrl) || LlmProperties.isVivoAllowedModel(model);
    }

    private boolean looksLikeVivoBaseUrl(String baseUrl) {
        return hasText(baseUrl) && baseUrl.toLowerCase(Locale.ROOT).contains("api-ai.vivo.com.cn");
    }

    private boolean shouldSendApiKeyHeader(String baseUrl, String model) {
        return looksLikeMimoTarget(baseUrl) || looksLikeMimoTarget(model);
    }

    private boolean looksLikeMimoTarget(String value) {
        return hasText(value) && value.toLowerCase(Locale.ROOT).contains("mimo");
    }

    private String appendQueryParam(String endpoint, String key, String value) {
        String encodedValue = URLEncoder.encode(value, StandardCharsets.UTF_8);
        return endpoint + (endpoint.contains("?") ? "&" : "?") + key + "=" + encodedValue;
    }

    private boolean hasText(String value) {
        return value != null && !value.trim().isEmpty();
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class OpenAiChatRequest {
        private String model;
        private List<OpenAiMessage> messages;
        private Double temperature;
        private Boolean stream;
        @JsonProperty("max_tokens")
        private Integer maxTokens;

        public String getModel() {
            return model;
        }

        public void setModel(String model) {
            this.model = model;
        }

        public List<OpenAiMessage> getMessages() {
            return messages;
        }

        public void setMessages(List<OpenAiMessage> messages) {
            this.messages = messages;
        }

        public Double getTemperature() {
            return temperature;
        }

        public void setTemperature(Double temperature) {
            this.temperature = temperature;
        }

        public Boolean getStream() {
            return stream;
        }

        public void setStream(Boolean stream) {
            this.stream = stream;
        }

        public Integer getMaxTokens() {
            return maxTokens;
        }

        public void setMaxTokens(Integer maxTokens) {
            this.maxTokens = maxTokens;
        }
    }

    public static class OpenAiMessage {
        private String role;
        private String content;

        public OpenAiMessage() {
        }

        public OpenAiMessage(String role, String content) {
            this.role = role;
            this.content = content;
        }

        public String getRole() {
            return role;
        }

        public void setRole(String role) {
            this.role = role;
        }

        public String getContent() {
            return content;
        }

        public void setContent(String content) {
            this.content = content;
        }
    }
}

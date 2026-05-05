package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.reactive.function.client.WebClientRequestException;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Flux;

import java.net.SocketTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.function.Consumer;

@Component
public class SpringAiGatewaySupport {

    private final SpringAiClientFactory springAiClientFactory;

    public SpringAiGatewaySupport(SpringAiClientFactory springAiClientFactory) {
        this.springAiClientFactory = springAiClientFactory;
    }

    public String call(LlmProperties.ResolvedOpenAiOptions options,
                       String systemPrompt,
                       String userPrompt) {
        try {
            return callOnce(options, systemPrompt, userPrompt, true);
        } catch (RuntimeException ex) {
            if (shouldRetryWithoutMaxTokens(ex)) {
                try {
                    return callOnce(options, systemPrompt, userPrompt, false);
                } catch (RuntimeException retryEx) {
                    throw translateFailure(retryEx, options);
                }
            }
            throw translateFailure(ex, options);
        }
    }

    public String stream(LlmProperties.ResolvedOpenAiOptions options,
                         String systemPrompt,
                         String userPrompt,
                         Consumer<String> tokenConsumer) {
        try {
            return streamOnce(options, systemPrompt, userPrompt, tokenConsumer, true);
        } catch (RuntimeException ex) {
            if (shouldRetryWithoutMaxTokens(ex)) {
                try {
                    return streamOnce(options, systemPrompt, userPrompt, tokenConsumer, false);
                } catch (RuntimeException retryEx) {
                    throw translateFailure(retryEx, options);
                }
            }
            throw translateFailure(ex, options);
        }
    }

    private String callOnce(LlmProperties.ResolvedOpenAiOptions options,
                            String systemPrompt,
                            String userPrompt,
                            boolean includeMaxTokens) {
        ChatClient client = springAiClientFactory.getOrCreate(options);
        String content = client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(buildOptions(options, includeMaxTokens))
                .call()
                .content();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("OpenAI message content is empty");
        }
        return content.trim();
    }

    private String streamOnce(LlmProperties.ResolvedOpenAiOptions options,
                              String systemPrompt,
                              String userPrompt,
                              Consumer<String> tokenConsumer,
                              boolean includeMaxTokens) {
        ChatClient client = springAiClientFactory.getOrCreate(options);
        StringBuilder finalContent = new StringBuilder();

        Flux<String> contentFlux = client.prompt()
                .system(systemPrompt)
                .user(userPrompt)
                .options(buildOptions(options, includeMaxTokens))
                .stream()
                .content();

        contentFlux.doOnNext(token -> {
                    if (StringUtils.hasText(token)) {
                        finalContent.append(token);
                        if (tokenConsumer != null) {
                            tokenConsumer.accept(token);
                        }
                    }
                })
                .blockLast();

        String content = finalContent.toString();
        if (!StringUtils.hasText(content)) {
            throw new IllegalStateException("OpenAI message content is empty");
        }
        return content.trim();
    }

    private OpenAiChatOptions buildOptions(LlmProperties.ResolvedOpenAiOptions options, boolean includeMaxTokens) {
        OpenAiChatOptions.Builder builder = OpenAiChatOptions.builder()
                .model(options.getModel())
                .temperature(options.getTemperature());
        if (includeMaxTokens && options.getMaxOutputTokens() != null && options.getMaxOutputTokens() > 0) {
            builder.maxTokens(options.getMaxOutputTokens());
        }
        return builder.build();
    }

    private boolean shouldRetryWithoutMaxTokens(Throwable throwable) {
        String details = collectThrowableMessages(throwable).toLowerCase(Locale.ROOT);
        return details.contains("max_tokens")
                || details.contains("max completion tokens")
                || details.contains("max_completion_tokens")
                || details.contains("unsupported parameter")
                || details.contains("unknown field");
    }

    private RuntimeException translateFailure(RuntimeException ex, LlmProperties.ResolvedOpenAiOptions options) {
        Throwable root = mostSpecificCause(ex);
        String endpoint = normalizeBaseUrl(options.getBaseUrl()) + "/chat/completions";
        String model = options.getModel();

        if (root instanceof RestClientResponseException responseEx) {
            return new IllegalStateException(
                    describeHttpFailure(responseEx.getStatusCode().value(), responseEx.getResponseBodyAsString(), model, endpoint),
                    ex
            );
        }
        if (root instanceof WebClientResponseException responseEx) {
            return new IllegalStateException(
                    describeHttpFailure(responseEx.getStatusCode().value(), responseEx.getResponseBodyAsString(), model, endpoint),
                    ex
            );
        }
        if (root instanceof ResourceAccessException
                || root instanceof WebClientRequestException
                || root instanceof HttpTimeoutException
                || root instanceof SocketTimeoutException) {
            return new IllegalStateException("Model request timeout or network error. endpoint=" + endpoint, ex);
        }

        String reason = StringUtils.hasText(root == null ? null : root.getMessage())
                ? root.getMessage().trim()
                : "unknown error";
        return new IllegalStateException(
                "Model request failed. model=" + model + ", endpoint=" + endpoint + ", reason=" + reason,
                ex
        );
    }

    private String describeHttpFailure(int code, String responseBody, String model, String endpoint) {
        String body = trimBody(responseBody);

        if (code == 401) {
            return "Authentication failed (401). Check OPENAI_API_KEY and gateway account permissions. model="
                    + model + ", endpoint=" + endpoint;
        }
        if (code == 429) {
            return "Request limited or quota exhausted (429). model=" + model + ", endpoint=" + endpoint;
        }
        if (code == 404) {
            return "Endpoint or model is unavailable (404). model=" + model + ", endpoint=" + endpoint
                    + ", body=" + body;
        }
        if (code == 502 || code == 503 || code == 504) {
            return "Gateway upstream is unavailable (HTTP " + code + "). model=" + model
                    + ", endpoint=" + endpoint + ", body=" + body;
        }
        if (code == 400 && body != null) {
            String lower = body.toLowerCase(Locale.ROOT);
            if (lower.contains("model")) {
                return "Model is unavailable or unsupported. model=" + model + ", body=" + body;
            }
            if (lower.contains("api key") || lower.contains("unauthorized") || lower.contains("invalid")) {
                return "Credentials are invalid. body=" + body;
            }
        }
        if (code >= 500) {
            return "Model gateway unavailable (HTTP " + code + "). endpoint=" + endpoint + ", body=" + body;
        }
        if (body != null) {
            String lower = body.toLowerCase(Locale.ROOT);
            if (lower.contains("insufficient_quota") || lower.contains("quota")) {
                return "Quota exhausted. model=" + model;
            }
        }
        return "HTTP error " + code + ". model=" + model + ", endpoint=" + endpoint + ", body=" + body;
    }

    private Throwable mostSpecificCause(Throwable throwable) {
        Throwable result = throwable;
        while (result != null && result.getCause() != null) {
            result = result.getCause();
        }
        return result == null ? throwable : result;
    }

    private String collectThrowableMessages(Throwable throwable) {
        StringBuilder details = new StringBuilder();
        Throwable current = throwable;
        while (current != null) {
            if (StringUtils.hasText(current.getMessage())) {
                details.append(current.getMessage()).append('\n');
            }
            current = current.getCause();
        }
        return details.toString();
    }

    private String trimBody(String body) {
        if (body == null) {
            return "";
        }
        String normalized = body.replaceAll("\\s+", " ").trim();
        if (normalized.length() <= 400) {
            return normalized;
        }
        return normalized.substring(0, 400) + "...";
    }

    private String normalizeBaseUrl(String baseUrl) {
        if (!StringUtils.hasText(baseUrl)) {
            return "https://api.openai.com/v1";
        }
        String value = baseUrl.trim();
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}

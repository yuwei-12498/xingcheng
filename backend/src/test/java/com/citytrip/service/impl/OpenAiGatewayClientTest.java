package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenAiGatewayClientTest {

    @Test
    void requestShouldAppendRequestIdWhenCallingVivoEndpoint() throws Exception {
        AtomicReference<String> capturedQuery = new AtomicReference<>();
        try (HttpServerHandle server = HttpServerHandle.custom(exchange -> {
            capturedQuery.set(exchange.getRequestURI().getQuery());
            HttpServerHandle.respondJson(exchange, """
                    {"choices":[{"message":{"content":"ok","role":"assistant"}}]}
                    """);
        })) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "Doubao-Seed-2.0-mini",
                    0.2D,
                    64
            );

            String answer = client.request(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "hi"))
            );

            assertThat(answer).isEqualTo("ok");
            assertThat(capturedQuery.get()).contains("requestId=");
        }
    }

    @Test
    void requestFailsClearlyWhenProviderReturnsJsonErrorWithHttp200() throws Exception {
        try (HttpServerHandle server = HttpServerHandle.json("""
                {"error":{"code":"401","message":"no model access permission"}}
                """)) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "BlueLM-7B-Chat",
                    0.2D,
                    128
            );

            assertThatThrownBy(() -> client.request(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "你好"))
            ))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("no model access permission");
        }
    }

    @Test
    void requestShouldSendApiKeyHeaderWhenCallingMimoModel() throws Exception {
        AtomicReference<String> capturedAuthorization = new AtomicReference<>();
        AtomicReference<String> capturedApiKey = new AtomicReference<>();
        AtomicReference<String> capturedQuery = new AtomicReference<>();

        try (HttpServerHandle server = HttpServerHandle.custom(exchange -> {
            capturedAuthorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            capturedApiKey.set(exchange.getRequestHeaders().getFirst("api-key"));
            capturedQuery.set(exchange.getRequestURI().getQuery());
            HttpServerHandle.respondJson(exchange, """
                    {"choices":[{"message":{"content":"ok","role":"assistant"}}]}
                    """);
        })) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "MiMo-V2-Omni",
                    0.2D,
                    128
            );

            String answer = client.request(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "hi"))
            );

            assertThat(answer).isEqualTo("ok");
            assertThat(capturedAuthorization.get()).isEqualTo("Bearer test-key");
            assertThat(capturedApiKey.get()).isEqualTo("test-key");
            assertThat(capturedQuery.get()).isNull();
        }
    }

    @Test
    void requestShouldSurfaceToolCallsForFollowUpLoop() throws Exception {
        try (HttpServerHandle server = HttpServerHandle.json("""
                {
                  "choices":[
                    {
                      "message":{
                        "role":"assistant",
                        "tool_calls":[
                          {
                            "id":"call_1",
                            "type":"function",
                            "function":{
                              "name":"search_poi",
                              "arguments":"{\\"keyword\\":\\"太古里\\"}"
                            }
                          }
                        ]
                      }
                    }
                  ]
                }
                """)) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "Doubao-Seed-2.0-mini",
                    0.2D,
                    128
            );

            String payload = client.request(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "太古里附近晚上还能去哪"))
            );

            assertThat(payload).startsWith(OpenAiGatewayClient.TOOL_CALL_PREFIX);
            assertThat(payload).contains("search_poi");
        }
    }

    @Test
    void streamShouldRetryNonStreamWhenVivoSseOnlyContainsReasoningContent() throws Exception {
        AtomicInteger callCount = new AtomicInteger();
        AtomicReference<String> firstRequestBody = new AtomicReference<>();
        AtomicReference<String> secondRequestBody = new AtomicReference<>();

        try (HttpServerHandle server = HttpServerHandle.custom(exchange -> {
            String requestBody = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            int current = callCount.incrementAndGet();
            if (current == 1) {
                firstRequestBody.set(requestBody);
                HttpServerHandle.respondEventStream(exchange, """
                        data:{"choices":[{"delta":{"content":"","reasoning_content":"thinking","role":"assistant"},"index":0}],"object":"chat.completion.chunk"}
                        
                        data:{"choices":[{"delta":{"content":"","reasoning_content":"...","role":"assistant"},"finish_reason":"length","index":0}],"object":"chat.completion.chunk"}
                        
                        data:[DONE]
                        """);
                return;
            }
            secondRequestBody.set(requestBody);
            HttpServerHandle.respondJson(exchange, """
                    {"choices":[{"message":{"content":"测试成功","role":"assistant"}}]}
                    """);
        })) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "Doubao-Seed-2.0-mini",
                    0.2D,
                    64
            );

            StringBuilder streamed = new StringBuilder();
            String answer = client.stream(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "你好")),
                    streamed::append
            );

            assertThat(answer).isEqualTo("测试成功");
            assertThat(streamed.toString()).isEqualTo("测试成功");
            assertThat(callCount.get()).isEqualTo(2);
            assertThat(firstRequestBody.get()).contains("\"stream\":true");
            assertThat(secondRequestBody.get()).contains("\"stream\":false");
        }
    }

    private static final class HttpServerHandle implements AutoCloseable {
        private final HttpServer server;

        private HttpServerHandle(HttpServer server) {
            this.server = server;
        }

        static HttpServerHandle json(String body) throws IOException {
            return custom(exchange -> respondJson(exchange, body));
        }

        static HttpServerHandle custom(ExchangeHandler handler) throws IOException {
            HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
            server.createContext("/v1/chat/completions", exchange -> handler.handle(exchange));
            server.start();
            return new HttpServerHandle(server);
        }

        String baseUrl() {
            return "http://127.0.0.1:" + server.getAddress().getPort() + "/v1";
        }

        @Override
        public void close() {
            server.stop(0);
        }

        private static void respondJson(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }

        private static void respondEventStream(HttpExchange exchange, String body) throws IOException {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/event-stream; charset=UTF-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream outputStream = exchange.getResponseBody()) {
                outputStream.write(bytes);
            }
        }
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws IOException;
    }
}

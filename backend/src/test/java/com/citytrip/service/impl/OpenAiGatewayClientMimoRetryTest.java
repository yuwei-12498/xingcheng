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

class OpenAiGatewayClientMimoRetryTest {

    @Test
    void streamShouldRetryNonStreamWhenMimoSseContainsNoVisibleContent() throws Exception {
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
                        
                        data:{"choices":[{"delta":{"content":"","reasoning_content":"...","role":"assistant"},"finish_reason":"stop","index":0}],"object":"chat.completion.chunk"}
                        
                        data:[DONE]
                        """);
                return;
            }
            secondRequestBody.set(requestBody);
            HttpServerHandle.respondJson(exchange, """
                    {"choices":[{"message":{"content":"retry-ok","role":"assistant"}}]}
                    """);
        })) {
            LlmProperties properties = new LlmProperties();
            properties.setConnectTimeoutSeconds(2);
            properties.setReadTimeoutSeconds(2);

            OpenAiGatewayClient client = new OpenAiGatewayClient(properties, new ObjectMapper());
            LlmProperties.ResolvedOpenAiOptions options = new LlmProperties.ResolvedOpenAiOptions(
                    server.baseUrl(),
                    "mimo-v2-omni",
                    0.2D,
                    64
            );

            StringBuilder streamed = new StringBuilder();
            String answer = client.stream(
                    options,
                    "test-key",
                    List.of(new OpenAiGatewayClient.OpenAiMessage("user", "hi")),
                    streamed::append
            );

            assertThat(answer).isEqualTo("retry-ok");
            assertThat(streamed.toString()).isEqualTo("retry-ok");
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

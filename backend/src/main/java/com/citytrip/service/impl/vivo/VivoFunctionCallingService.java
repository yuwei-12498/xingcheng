package com.citytrip.service.impl.vivo;

import com.citytrip.config.LlmProperties;
import com.citytrip.service.impl.OpenAiGatewayClient;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Service
public class VivoFunctionCallingService {
    public static final String NO_RESULT = "NO_RESULT";

    private final VivoToolRegistry registry;
    private final ObjectMapper objectMapper;

    @Autowired
    public VivoFunctionCallingService(VivoToolRegistry registry) {
        this(registry, new ObjectMapper());
    }

    public VivoFunctionCallingService(VivoToolRegistry registry, ObjectMapper objectMapper) {
        this.registry = registry;
        this.objectMapper = objectMapper;
    }

    public boolean shouldEnterToolLoop(String payload) {
        return payload != null && payload.startsWith(OpenAiGatewayClient.TOOL_CALL_PREFIX);
    }

    public String executeToolCall(String toolName, String argumentsJson) {
        VivoToolDefinition definition = registry.find(toolName)
                .orElseThrow(() -> new IllegalArgumentException("Unknown tool: " + toolName));
        try {
            String result = definition.executor().apply(argumentsJson);
            if (result == null || result.isBlank()) {
                return NO_RESULT;
            }
            return result;
        } catch (Exception ex) {
            try {
                return objectMapper.writeValueAsString(new ToolErrorPayload(toolName, "error", "tool temporarily unavailable"));
            } catch (Exception serializationEx) {
                return "{\"tool\":\"" + toolName + "\",\"status\":\"error\",\"message\":\"tool temporarily unavailable\"}";
            }
        }
    }

    public ToolLoopResult runToolLoop(String payload,
                                      List<OpenAiGatewayClient.OpenAiMessage> originalMessages,
                                      OpenAiGatewayClient gatewayClient,
                                      LlmProperties.ResolvedOpenAiOptions options,
                                      String apiKey) {
        List<ToolCall> toolCalls = parseToolCalls(payload).stream().limit(3).toList();
        if (toolCalls.isEmpty()) {
            return new ToolLoopResult(payload, List.of());
        }
        List<String> toolResults = new ArrayList<>();
        for (ToolCall toolCall : toolCalls) {
            toolResults.add(executeToolCall(toolCall.name(), toolCall.argumentsJson()));
        }
        if (gatewayClient == null || options == null || !StringUtils.hasText(apiKey)) {
            return new ToolLoopResult(String.join("\n", toolResults), toolResults);
        }
        List<OpenAiGatewayClient.OpenAiMessage> followUp = new ArrayList<>(originalMessages);
        followUp.add(new OpenAiGatewayClient.OpenAiMessage(
                "assistant",
                "Tool calls requested: " + payload.substring(OpenAiGatewayClient.TOOL_CALL_PREFIX.length())
        ));
        followUp.add(new OpenAiGatewayClient.OpenAiMessage(
                "user",
                "Tool results(JSON):\n" + String.join("\n", toolResults)
                        + "\nPlease answer the original user question grounded in these tool results."
        ));
        String finalAnswer = gatewayClient.request(options, apiKey, followUp);
        return new ToolLoopResult(StringUtils.hasText(finalAnswer) ? finalAnswer : String.join("\n", toolResults), toolResults);
    }

    private List<ToolCall> parseToolCalls(String payload) {
        if (!shouldEnterToolLoop(payload)) {
            return List.of();
        }
        try {
            JsonNode root = objectMapper.readTree(payload.substring(OpenAiGatewayClient.TOOL_CALL_PREFIX.length()));
            if (!root.isArray()) {
                return List.of();
            }
            List<ToolCall> toolCalls = new ArrayList<>();
            for (JsonNode node : root) {
                String name = node.path("function").path("name").asText("");
                String argumentsJson = node.path("function").path("arguments").asText("{}");
                if (StringUtils.hasText(name)) {
                    toolCalls.add(new ToolCall(name, argumentsJson));
                }
            }
            return toolCalls;
        } catch (Exception ex) {
            return List.of();
        }
    }

    private record ToolErrorPayload(String tool, String status, String message) {
    }

    private record ToolCall(String name, String argumentsJson) {
    }

    public record ToolLoopResult(String finalAnswer, List<String> toolResults) {
    }
}

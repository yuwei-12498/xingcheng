package com.citytrip.service.impl;

import com.citytrip.config.LlmProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class RealLlmGatewayServiceTest {

    @Test
    void generateChatFollowUpTipsShouldSanitizeAndLimitOutput() {
        OpenAiGatewayClient gatewayClient = mock(OpenAiGatewayClient.class);
        SafePromptBuilder promptBuilder = new SafePromptBuilder();
        LlmProperties properties = new LlmProperties();
        properties.getOpenai().setEnabled(true);
        properties.getOpenai().setApiKey("sk-test");
        properties.getOpenai().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().setModel("gpt-5.4");
        properties.getOpenai().getText().setBaseUrl("https://api.openai.com/v1");
        properties.getOpenai().getText().setModel("gpt-5.4");

        when(gatewayClient.request(any(), eq("sk-test"), any())).thenReturn("""
                1. 晚上去的话哪一站更适合夜景？
                2) 这条路线里哪一段最适合打车？
                - 雨天的话要不要把室外点位换掉？
                4. 这一行不应该被保留
                """);

        RealLlmGatewayService service = new RealLlmGatewayService(
                gatewayClient,
                properties,
                promptBuilder,
                new ObjectMapper()
        );

        List<String> tips = service.generateChatFollowUpTips("帮我规划成都夜游路线", "成都");

        assertThat(tips).containsExactly(
                "晚上去的话哪一站更适合夜景？",
                "这条路线里哪一段最适合打车？",
                "雨天的话要不要把室外点位换掉？"
        );
    }
}

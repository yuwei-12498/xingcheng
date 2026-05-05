package com.citytrip.service.domain.ai;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChatFactGuardServiceTest {

    private final ChatFactGuardService service = new ChatFactGuardService();

    @Test
    void shouldKeepAnswerWhenMentionsAreCoveredByEvidence() {
        ChatGeoSkillService.GeoFact fact = new ChatGeoSkillService.GeoFact(
                "春熙路",
                "商圈",
                "成都",
                "锦江",
                BigDecimal.valueOf(30.657),
                BigDecimal.valueOf(104.065),
                120,
                "local"
        );

        ChatFactGuardService.GuardResult result = service.guard("春熙路晚上也很热闹。", List.of(fact));

        assertThat(result.degraded()).isFalse();
        assertThat(result.answer()).contains("春熙路");
        assertThat(result.evidence()).isNotEmpty();
    }

    @Test
    void shouldMarkAnswerAsDegradedWhenMentionIsNotInEvidence() {
        ChatGeoSkillService.GeoFact fact = new ChatGeoSkillService.GeoFact(
                "春熙路",
                "商圈",
                "成都",
                "锦江",
                BigDecimal.valueOf(30.657),
                BigDecimal.valueOf(104.065),
                120,
                "local"
        );

        ChatFactGuardService.GuardResult result = service.guard(
                "春熙路。不存在景区。",
                List.of(fact)
        );

        assertThat(result.degraded()).isTrue();
        assertThat(result.answer()).contains("春熙路");
        assertThat(result.evidence()).isNotEmpty();
    }
}

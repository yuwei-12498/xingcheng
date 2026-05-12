package com.citytrip.service.ai.model;

import com.citytrip.service.TravelTimeService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class TravelModeDecisionServiceTest {

    @Test
    void pickBestShouldPreferCyclingForUrbanMidShortDistance() {
        TravelModeDecisionService service = new TravelModeDecisionService();

        TravelTimeService.TravelLegEstimate selected = service.pickBest(List.of(
                new TravelTimeService.TravelLegEstimate(21, BigDecimal.valueOf(2.7D), "walk"),
                new TravelTimeService.TravelLegEstimate(11, BigDecimal.valueOf(2.7D), "bike"),
                new TravelTimeService.TravelLegEstimate(13, BigDecimal.valueOf(2.7D), "taxi")
        ));

        assertThat(selected.transportMode()).isEqualTo("bike");
    }
}

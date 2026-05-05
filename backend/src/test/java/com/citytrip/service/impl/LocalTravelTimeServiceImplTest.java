package com.citytrip.service.impl;

import com.citytrip.model.entity.Poi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class LocalTravelTimeServiceImplTest {

    @Test
    void estimateTravelLegShouldBuildRenderablePathPointsForMapFallback() {
        LocalTravelTimeServiceImpl service = new LocalTravelTimeServiceImpl();

        Poi from = new Poi();
        from.setId(1L);
        from.setLatitude(BigDecimal.valueOf(30.6588D));
        from.setLongitude(BigDecimal.valueOf(104.1996D));
        from.setDistrict("武侯区");

        Poi to = new Poi();
        to.setId(2L);
        to.setLatitude(BigDecimal.valueOf(30.6710D));
        to.setLongitude(BigDecimal.valueOf(104.1030D));
        to.setDistrict("成华区");

        var leg = service.estimateTravelLeg(from, to);

        assertThat(leg.pathPoints()).isNotEmpty();
        assertThat(leg.pathPoints().size()).isGreaterThanOrEqualTo(3);
        assertThat(leg.pathPoints().get(0).latitude()).isEqualByComparingTo("30.6588");
        assertThat(leg.pathPoints().get(0).longitude()).isEqualByComparingTo("104.1996");
        assertThat(leg.pathPoints().get(leg.pathPoints().size() - 1).latitude()).isEqualByComparingTo("30.6710");
        assertThat(leg.pathPoints().get(leg.pathPoints().size() - 1).longitude()).isEqualByComparingTo("104.1030");
    }
}

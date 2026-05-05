package com.citytrip.service.impl;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.SegmentTransportAnalysisVO;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class MockLlmServiceImplTest {

    @Test
    void analyzeSegmentTransportReturnsReadableChineseFallbackCopy() {
        MockLlmServiceImpl service = new MockLlmServiceImpl();

        GenerateReqDTO req = new GenerateReqDTO();
        req.setDeparturePlaceName("酒店");

        ItineraryNodeVO toNode = new ItineraryNodeVO();
        toNode.setPoiName("宽窄巷子");
        toNode.setDepartureTravelTime(12);
        toNode.setDepartureDistanceKm(BigDecimal.valueOf(1.1D));

        SegmentTransportAnalysisVO analysis = service.analyzeSegmentTransport(req, null, toNode);

        assertThat(analysis.getTransportMode()).isEqualTo("步行");
        assertThat(analysis.getNarrative())
                .contains("酒店到宽窄巷子")
                .contains("约12分钟")
                .contains("用步行更稳妥");
        assertThat(analysis.getNarrative()).doesNotContain("??");
    }
}

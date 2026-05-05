package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ItineraryRequestNormalizerTest {

    @Test
    void shouldPromotePandaNaturalLanguageRequirementToMustVisitKeywords() {
        GenerateReqDTO req = new GenerateReqDTO();
        req.setNaturalLanguageRequirement("我想一个人去看大熊猫");

        GenerateReqDTO normalized = new ItineraryRequestNormalizer().normalize(req);

        assertThat(normalized.getNaturalLanguageRequirement()).isEqualTo("我想一个人去看大熊猫");
        assertThat(normalized.getMustVisitPoiNames())
                .contains("大熊猫繁育研究基地", "熊猫", "动物园");
    }
}

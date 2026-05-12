package com.citytrip.service.ai.rag;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PoiFactRepositoryTest {

    @Test
    void loadAllShouldReadCategorySplitFactsFromClasspath() {
        PoiFactRepository repository = new PoiFactRepository();

        List<PoiFactRecord> facts = repository.loadAll();

        assertThat(facts).extracting(PoiFactRecord::poiName)
                .contains(
                        "\u6210\u90fd\u535a\u7269\u9986",
                        "\u6625\u7199\u8def",
                        "\u592a\u53e4\u91cc",
                        "\u5bbd\u7a84\u5df7\u5b50",
                        "\u9526\u91cc",
                        "\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730",
                        "\u9752\u57ce\u5c71",
                        "\u90fd\u6c5f\u5830\u666f\u533a"
                );
        assertThat(facts).extracting(PoiFactRecord::categoryFile)
                .contains("museum", "shopping", "historic-street", "temple-culture", "scenic-nature", "family-animal");
    }

    @Test
    void loadAllShouldKeepOfficialSourceUrlsForCoreChengduPois() {
        PoiFactRepository repository = new PoiFactRepository();

        List<PoiFactRecord> facts = repository.loadAll();

        assertThat(facts).filteredOn(fact -> "\u6210\u90fd\u535a\u7269\u9986".equals(fact.poiName()))
                .extracting(PoiFactRecord::sourceUrl)
                .allMatch(url -> url.contains("cdmuseum.com"));
        assertThat(facts).filteredOn(fact -> "\u6210\u90fd\u5927\u718a\u732b\u7e41\u80b2\u7814\u7a76\u57fa\u5730".equals(fact.poiName()))
                .extracting(PoiFactRecord::sourceUrl)
                .allMatch(url -> url.contains("panda.org.cn"));
    }
}

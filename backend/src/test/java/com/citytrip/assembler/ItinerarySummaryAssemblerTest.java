package com.citytrip.assembler;

import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ItinerarySummaryAssemblerTest {

    private final ItinerarySummaryAssembler assembler = new ItinerarySummaryAssembler();

    @Test
    void resolveCoverImageUsesUserSelectedCoverFirst() {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setCoverImageUrl("data:image/png;base64,abc");
        itinerary.setNodes(List.of(node("成都动物园")));

        assertThat(assembler.resolveCoverImage(itinerary)).isEqualTo("data:image/png;base64,abc");
    }

    @Test
    void resolveCoverImageMigratesLegacyPresetToVersionedCover() {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setCoverImageUrl("/community-covers/cover-night.svg");
        itinerary.setNodes(List.of(node("成都动物园")));

        assertThat(assembler.resolveCoverImage(itinerary)).isEqualTo("/community-covers/v2/cover-night.svg");
    }

    @Test
    void resolveCoverImageMigratesLegacyDefaultCover() {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setCoverImageUrl("/community-cover.svg");
        itinerary.setNodes(List.of(node("成都动物园")));

        assertThat(assembler.resolveCoverImage(itinerary)).isEqualTo("/community-covers/v2/cover-citywalk.svg");
    }

    @Test
    void resolveCoverImageFallsBackToStablePresetForOldPosts() {
        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setCustomTitle("成都动物园轻松路线");
        itinerary.setSelectedOptionKey("default");
        itinerary.setNodes(List.of(node("成都动物园"), node("文殊院")));

        String first = assembler.resolveCoverImage(itinerary);
        String second = assembler.resolveCoverImage(itinerary);

        assertThat(first).startsWith("/community-covers/v2/cover-").endsWith(".svg");
        assertThat(second).isEqualTo(first);
    }

    private ItineraryNodeVO node(String name) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(name);
        return node;
    }
}

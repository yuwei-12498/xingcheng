package com.citytrip.service.ai.mcp;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItinerarySnapshotMcpCapabilityTest {

    @Test
    void shouldLoadSavedItinerarySnapshotAndNormalizePayload() throws Exception {
        SavedItineraryRepository repository = mock(SavedItineraryRepository.class);
        SavedItineraryCodec codec = mock(SavedItineraryCodec.class);

        SavedItinerary entity = new SavedItinerary();
        entity.setId(88L);
        entity.setShareNote("拍照和逛街比较顺");

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setSelectedOptionKey("option-1");
        itinerary.setRecommendReason("动线集中");
        itinerary.setTotalDuration(420);
        itinerary.setTotalCost(BigDecimal.valueOf(188));
        itinerary.setNodes(List.of(node("春熙路"), node("IFS"), node("太古里")));

        when(repository.reload(88L)).thenReturn(entity);
        when(codec.readItinerary(entity)).thenReturn(itinerary);

        ItinerarySnapshotMcpCapability capability = new ItinerarySnapshotMcpCapability(repository, codec);

        Object result = capability.execute(Map.of("itineraryId", 88L));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.toString())
                .contains("itinerary.snapshot")
                .contains("option-1")
                .contains("春熙路")
                .contains("太古里")
                .contains("拍照和逛街比较顺");
    }

    @Test
    void shouldReportUnavailableWhenSnapshotDependencyMissing() {
        ItinerarySnapshotMcpCapability capability = new ItinerarySnapshotMcpCapability(null, null);

        Object result = capability.execute(Map.of("itineraryId", 88L));

        assertThat(result.toString()).contains("itinerary.snapshot", "unavailable");
    }

    private ItineraryNodeVO node(String name) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setPoiName(name);
        return node;
    }
}

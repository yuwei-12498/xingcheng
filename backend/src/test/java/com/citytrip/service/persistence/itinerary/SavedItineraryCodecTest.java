package com.citytrip.service.persistence.itinerary;

import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SavedItineraryCodecTest {

    @Test
    void applyEntityMetadata_shouldBackfillMissingNodeKeysForCurrentAndOptionNodes() {
        SavedItineraryCodec codec = new SavedItineraryCodec(new ObjectMapper());

        ItineraryNodeVO currentNode = node(null, 1, 1, 101L, "武侯祠");
        ItineraryNodeVO optionNode = node(null, 1, 1, 101L, "武侯祠");
        ItineraryNodeVO preservedNode = node("custom-9001", 1, 2, -9001L, "自定义点");

        ItineraryOptionVO option = new ItineraryOptionVO();
        option.setOptionKey("balanced");
        option.setNodes(List.of(optionNode, preservedNode));

        ItineraryVO itinerary = new ItineraryVO();
        itinerary.setNodes(List.of(currentNode, preservedNode));
        itinerary.setOptions(List.of(option));
        itinerary.setSelectedOptionKey("balanced");

        SavedItinerary entity = new SavedItinerary();
        entity.setId(55L);

        ItineraryVO result = codec.applyEntityMetadata(itinerary, entity);

        assertThat(result.getNodes()).extracting(ItineraryNodeVO::getNodeKey)
                .containsExactly("node-1-1-101", "custom-9001");
        assertThat(result.getOptions().get(0).getNodes()).extracting(ItineraryNodeVO::getNodeKey)
                .containsExactly("node-1-1-101", "custom-9001");
    }

    private ItineraryNodeVO node(String nodeKey, int dayNo, int stepOrder, Long poiId, String poiName) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setNodeKey(nodeKey);
        node.setDayNo(dayNo);
        node.setStepOrder(stepOrder);
        node.setPoiId(poiId);
        node.setPoiName(poiName);
        return node;
    }
}

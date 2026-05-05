package com.citytrip.service.domain.ai;

import com.citytrip.model.dto.ChatReqDTO;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ChatEvidenceSkillServiceTest {

    private final ChatEvidenceSkillService service = new ChatEvidenceSkillService();

    @Test
    void shouldMergeSkillsRouteMetaAndGuardEvidence() {
        ChatRouteContextSkillService.RouteContext routeContext = new ChatRouteContextSkillService.RouteContext(
                "B",
                "summary",
                List.of(new ChatReqDTO.ChatRouteNode(), new ChatReqDTO.ChatRouteNode())
        );

        List<String> merged = service.mergeEvidence(
                List.of("poi=Du Fu Thatched Cottage(source=local)"),
                routeContext,
                Set.of("RouteContextSkill", "SegmentTransportSkill", "FirstLegPreciseETASkill"),
                "geo-route-api",
                List.of(
                        "route_first_stop=Kuanzhai Alley",
                        "route_path=Kuanzhai Alley->Wuhou Shrine->Taikoo Li",
                        "segment_count=3"
                )
        );

        assertThat(merged).isNotEmpty();
        assertThat(merged).anyMatch(item -> item.startsWith("skills="));
        assertThat(merged).contains("route_option=B");
        assertThat(merged).contains("route_nodes=2");
        assertThat(merged).contains("first_leg_source=geo-route-api");
        assertThat(merged).contains("route_first_stop=Kuanzhai Alley");
        assertThat(merged).contains("route_path=Kuanzhai Alley->Wuhou Shrine->Taikoo Li");
        assertThat(merged).contains("segment_count=3");
        assertThat(merged).contains("poi=Du Fu Thatched Cottage(source=local)");
    }
}
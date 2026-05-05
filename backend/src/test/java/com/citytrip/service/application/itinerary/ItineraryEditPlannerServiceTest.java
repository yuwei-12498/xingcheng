package com.citytrip.service.application.itinerary;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ItineraryEditApplyReqDTO;
import com.citytrip.model.dto.ItineraryEditOperationDTO;
import com.citytrip.model.entity.UserCustomPoi;
import com.citytrip.model.vo.ItineraryDayWindowVO;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.planning.RouteAnalysisService;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ItineraryEditPlannerServiceTest {

    @Mock
    private RouteAnalysisService routeAnalysisService;

    @Mock
    private UserCustomPoiCommandService userCustomPoiCommandService;

    @InjectMocks
    private ItineraryEditPlannerService itineraryEditPlannerService;

    @Test
    void prepareShouldApplyCrossDayMoveStayUpdateAndInlineCustomPoiInsertion() {
        ItineraryVO current = new ItineraryVO();
        current.setNodes(List.of(
                node("node-a", 1, 1, 101L, "武侯祠", 90),
                node("node-b", 1, 2, 102L, "锦里", 80),
                node("node-c", 2, 1, 103L, "杜甫草堂", 70)
        ));
        current.setDayWindows(new ArrayList<>(List.of(
                dayWindow(1, "09:00", "18:00"),
                dayWindow(2, "09:00", "18:00")
        )));

        GenerateReqDTO originalReq = new GenerateReqDTO();
        originalReq.setCityName("成都");
        originalReq.setTripDate("2026-05-01");
        originalReq.setTripDays(2.0D);
        originalReq.setStartTime("09:00");
        originalReq.setEndTime("18:00");

        ItineraryEditApplyReqDTO editReq = new ItineraryEditApplyReqDTO();
        editReq.setSource("form");
        editReq.setOperations(List.of(
                operation("update_stay", "node-b", null, null, null, 30, null, null, null),
                operation("move_node", "node-c", null, 1, 2, null, null, null, null),
                operation("update_day_window", null, 2, null, null, null, "10:00", "21:30", null),
                operation("insert_inline_custom_poi", null, 2, null, 1, 45, null, null, inlineCustomPoi())
        ));

        UserCustomPoi customPoi = new UserCustomPoi();
        customPoi.setId(9001L);
        customPoi.setUserId(7L);
        customPoi.setName("社区书店");
        customPoi.setCategory("文艺");
        customPoi.setAddress("武侯区某街道");
        customPoi.setLatitude(new BigDecimal("30.6401"));
        customPoi.setLongitude(new BigDecimal("104.0431"));
        customPoi.setSuggestedStayDuration(45);
        when(userCustomPoiCommandService.resolveForInsertion(eq(7L), eq("成都"), any(ItineraryEditOperationDTO.class)))
                .thenReturn(customPoi);

        when(routeAnalysisService.analyzeRoute(any(ItineraryRouteOptimizer.RouteOption.class), any(GenerateReqDTO.class), any()))
                .thenReturn(routeAnalysis(
                        node(null, 1, 1, 101L, "武侯祠", 90),
                        node(null, 1, 2, 103L, "杜甫草堂", 70),
                        node(null, 1, 3, 102L, "锦里", 30)
                ))
                .thenReturn(routeAnalysis(
                        node(null, 1, 1, -9001L, "社区书店", 45)
                ));

        ItineraryEditPlannerService.PreparedEdit prepared = itineraryEditPlannerService.prepare(7L, current, originalReq, editReq);

        assertThat(prepared.itinerary().getNodes())
                .extracting(ItineraryNodeVO::getNodeKey)
                .containsExactly("node-a", "node-c", "node-b", "custom-9001");
        assertThat(prepared.itinerary().getNodes())
                .extracting(ItineraryNodeVO::getPoiName)
                .containsExactly("武侯祠", "杜甫草堂", "锦里", "社区书店");
        assertThat(prepared.itinerary().getDayWindows())
                .extracting(ItineraryDayWindowVO::getStartTime, ItineraryDayWindowVO::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("09:00", "18:00"),
                        org.assertj.core.groups.Tuple.tuple("10:00", "21:30")
                );
        assertThat(prepared.updatedRequest().getTripDays()).isEqualTo(2.0D);
        assertThat(prepared.summary()).contains("第 2 天结束时间调整为 21:30");
        assertThat(prepared.summary()).contains("新增“社区书店”");

        ArgumentCaptor<GenerateReqDTO> reqCaptor = ArgumentCaptor.forClass(GenerateReqDTO.class);
        verify(routeAnalysisService, org.mockito.Mockito.times(2))
                .analyzeRoute(any(ItineraryRouteOptimizer.RouteOption.class), reqCaptor.capture(), any());
        assertThat(reqCaptor.getAllValues())
                .extracting(GenerateReqDTO::getStartTime, GenerateReqDTO::getEndTime)
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("09:00", "18:00"),
                        org.assertj.core.groups.Tuple.tuple("10:00", "21:30")
                );
    }

    private ItineraryEditOperationDTO operation(String type,
                                                String nodeKey,
                                                Integer dayNo,
                                                Integer targetDayNo,
                                                Integer targetIndex,
                                                Integer stayDuration,
                                                String startTime,
                                                String endTime,
                                                ItineraryEditOperationDTO.CustomPoiDraft customPoiDraft) {
        ItineraryEditOperationDTO dto = new ItineraryEditOperationDTO();
        dto.setType(type);
        dto.setNodeKey(nodeKey);
        dto.setDayNo(dayNo);
        dto.setTargetDayNo(targetDayNo);
        dto.setTargetIndex(targetIndex);
        dto.setStayDuration(stayDuration);
        dto.setStartTime(startTime);
        dto.setEndTime(endTime);
        dto.setCustomPoiDraft(customPoiDraft);
        return dto;
    }

    private ItineraryEditOperationDTO.CustomPoiDraft inlineCustomPoi() {
        ItineraryEditOperationDTO.CustomPoiDraft draft = new ItineraryEditOperationDTO.CustomPoiDraft();
        draft.setName("社区书店");
        draft.setRoughLocation("武侯区某街道");
        draft.setCategory("文艺");
        draft.setReason("下午想加一个可休息的阅读点");
        return draft;
    }

    private ItineraryNodeVO node(String nodeKey, int dayNo, int stepOrder, Long poiId, String poiName, int stayDuration) {
        ItineraryNodeVO node = new ItineraryNodeVO();
        node.setNodeKey(nodeKey);
        node.setDayNo(dayNo);
        node.setStepOrder(stepOrder);
        node.setPoiId(poiId);
        node.setPoiName(poiName);
        node.setStayDuration(stayDuration);
        return node;
    }

    private ItineraryDayWindowVO dayWindow(int dayNo, String startTime, String endTime) {
        ItineraryDayWindowVO window = new ItineraryDayWindowVO();
        window.setDayNo(dayNo);
        window.setStartTime(startTime);
        window.setEndTime(endTime);
        return window;
    }

    private RouteAnalysisService.RouteAnalysis routeAnalysis(ItineraryNodeVO... nodes) {
        return new RouteAnalysisService.RouteAnalysis(
                new ItineraryRouteOptimizer.RouteOption(List.of(), "", 0D),
                List.of(nodes),
                0,
                BigDecimal.ZERO,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                0,
                List.of()
        );
    }
}

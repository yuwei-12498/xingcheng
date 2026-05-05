package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoRouteStep;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RouteAnalysisServiceTest {

    @Test
    void analyzeRouteResetsTimelineBySignatureDayBoundariesAndUsesDepartureForEachDayFirstStop() {
        TravelTimeService travelTimeService = mock(TravelTimeService.class);
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, new SegmentRouteGuideService());

        GenerateReqDTO req = new GenerateReqDTO();
        req.setStartTime("09:00");
        req.setEndTime("18:00");
        req.setDepartureLatitude(30.650D);
        req.setDepartureLongitude(104.060D);

        Poi departurePoi = departurePoi();
        Poi day1Poi1 = createPoi(1L, "Day1-A", "scenic");
        Poi day1Poi2 = createPoi(2L, "Day1-B", "scenic");
        Poi day2Poi1 = createPoi(3L, "Day2-A", "museum");
        Poi day2Poi2 = createPoi(4L, "Day2-B", "museum");

        ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(
                List.of(day1Poi1, day1Poi2, day2Poi1, day2Poi2),
                "1-2|3-4",
                180.0D
        );

        stubCommonRouteOptimizer(routeOptimizer, req);
        when(routeOptimizer.buildDeparturePoi(req)).thenReturn(departurePoi);
        when(travelTimeService.estimateTravelLeg(eq(departurePoi), eq(day1Poi1)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(8, BigDecimal.valueOf(1.5D), "步行"));
        when(travelTimeService.estimateTravelLeg(eq(day1Poi1), eq(day1Poi2)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(10, BigDecimal.valueOf(2.2D), "骑行"));
        when(travelTimeService.estimateTravelLeg(eq(departurePoi), eq(day2Poi1)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(15, BigDecimal.valueOf(6.5D), "打车"));
        when(travelTimeService.estimateTravelLeg(eq(day2Poi1), eq(day2Poi2)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(12, BigDecimal.valueOf(3.0D), "公交+步行"));

        RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

        assertThat(analysis.nodes()).hasSize(4);
        assertThat(analysis.nodes()).extracting(node -> node.getDayNo()).containsExactly(1, 1, 2, 2);
        assertThat(analysis.nodes()).extracting(node -> node.getStepOrder()).containsExactly(1, 2, 1, 2);
        assertThat(analysis.nodes().get(2).getStartTime()).isEqualTo("09:15");
        assertThat(analysis.nodes().get(2).getTravelTime()).isEqualTo(15);
        assertThat(analysis.nodes().get(2).getTravelDistanceKm()).isEqualByComparingTo("6.5");
        assertThat(analysis.nodes().get(2).getTravelTransportMode()).isEqualTo("打车");
        assertThat(analysis.nodes().get(2).getDepartureTravelTime()).isEqualTo(15);
        assertThat(analysis.nodes().get(2).getDepartureDistanceKm()).isEqualByComparingTo("6.5");
        assertThat(analysis.nodes().get(2).getDepartureTransportMode()).isEqualTo("打车");
        assertThat(analysis.nodes().get(2).getSegmentRouteGuide()).isNull();
        assertThat(analysis.nodes().get(2).getRoutePathPoints()).isEmpty();
        verify(travelTimeService).estimateTravelLeg(eq(departurePoi), eq(day2Poi1));
        verify(travelTimeService, never()).estimateTravelLeg(eq(day1Poi2), eq(day2Poi1));
    }

    @Test
    void analyzeRouteShouldFillPerLegTransportModeDistanceAndDeferDetailedGuide() {
        TravelTimeService travelTimeService = mock(TravelTimeService.class);
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, new SegmentRouteGuideService());

        GenerateReqDTO req = new GenerateReqDTO();
        req.setStartTime("09:00");
        req.setEndTime("18:00");
        req.setDepartureLatitude(30.650D);
        req.setDepartureLongitude(104.060D);

        Poi departurePoi = departurePoi();
        Poi p1 = createPoi(1L, "A", "scenic");
        Poi p2 = createPoi(2L, "B", "shopping");
        ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(List.of(p1, p2), "1-2", 100.0D);

        stubCommonRouteOptimizer(routeOptimizer, req);
        when(routeOptimizer.buildDeparturePoi(req)).thenReturn(departurePoi);
        when(travelTimeService.estimateTravelLeg(eq(departurePoi), eq(p1)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(18, BigDecimal.valueOf(5.2D), "地铁+步行"));
        when(travelTimeService.estimateTravelLeg(eq(p1), eq(p2)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(
                        27,
                        BigDecimal.valueOf(8.4D),
                        "地铁+步行",
                        List.of(point(30.652D, 104.062D), point(30.646D, 104.048D)),
                        new GeoRouteEstimate(
                                27,
                                BigDecimal.valueOf(8.4D),
                                "地铁+步行",
                                List.of(point(30.652D, 104.062D), point(30.646D, 104.048D)),
                                List.of(
                                        new GeoRouteStep("步行 300 米到天府广场地铁站 B 口", "walk", 300, 4, null, null, "天府广场", "B口", null, null, List.of()),
                                        new GeoRouteStep("乘 1 号线往文殊院方向 2 站", "metro", 6200, 15, "1号线", "天府广场", "文殊院", null, null, 2, List.of()),
                                        new GeoRouteStep("从 C 口出站后步行 450 米到景点入口", "walk", 450, 6, null, null, null, null, "C口", null, List.of())
                                )
                        )
                ));

        RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

        assertThat(analysis.nodes()).hasSize(2);
        assertThat(analysis.nodes().get(0).getTravelTransportMode()).isEqualTo("地铁+步行");
        assertThat(analysis.nodes().get(0).getDepartureTransportMode()).isEqualTo("地铁+步行");
        assertThat(analysis.nodes().get(1).getTravelTransportMode()).isEqualTo("地铁+步行");
        assertThat(analysis.nodes().get(1).getTravelDistanceKm()).isEqualByComparingTo("8.4");
        assertThat(analysis.nodes().get(1).getSegmentRouteGuide()).isNull();
        assertThat(analysis.nodes().get(1).getRoutePathPoints()).isEmpty();
    }

    @Test
    void analyzeRouteShouldDeferIncomingRouteGeometryToOnDemandCalculation() {
        TravelTimeService travelTimeService = mock(TravelTimeService.class);
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, new SegmentRouteGuideService());

        GenerateReqDTO req = new GenerateReqDTO();
        req.setStartTime("09:00");
        req.setEndTime("18:00");
        req.setDepartureLatitude(30.650D);
        req.setDepartureLongitude(104.060D);

        Poi departurePoi = departurePoi();
        Poi p1 = createPoi(1L, "A", "scenic");
        p1.setLatitude(BigDecimal.valueOf(30.652D));
        p1.setLongitude(BigDecimal.valueOf(104.062D));
        Poi p2 = createPoi(2L, "B", "shopping");
        p2.setLatitude(BigDecimal.valueOf(30.660D));
        p2.setLongitude(BigDecimal.valueOf(104.072D));
        ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(List.of(p1, p2), "1-2", 100.0D);

        stubCommonRouteOptimizer(routeOptimizer, req);
        when(routeOptimizer.buildDeparturePoi(req)).thenReturn(departurePoi);
        when(travelTimeService.estimateTravelLeg(eq(departurePoi), eq(p1)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(
                        8,
                        BigDecimal.valueOf(1.2D),
                        "步行",
                        List.of(
                                point(30.650D, 104.060D),
                                point(30.651D, 104.061D),
                                point(30.652D, 104.062D)
                        )
                ));
        when(travelTimeService.estimateTravelLeg(eq(p1), eq(p2)))
                .thenReturn(new TravelTimeService.TravelLegEstimate(
                        16,
                        BigDecimal.valueOf(2.8D),
                        "公交+步行",
                        List.of(
                                point(30.652D, 104.062D),
                                point(30.656D, 104.067D),
                                point(30.660D, 104.072D)
                        )
                ));

        RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

        assertThat(analysis.nodes()).hasSize(2);
        assertThat(analysis.nodes().get(0).getTravelTime()).isEqualTo(8);
        assertThat(analysis.nodes().get(0).getTravelDistanceKm()).isEqualByComparingTo("1.2");
        assertThat(analysis.nodes().get(0).getRoutePathPoints()).isEmpty();
        assertThat(analysis.nodes().get(0).getSegmentRouteGuide()).isNull();
        assertThat(analysis.nodes().get(1).getTravelTime()).isEqualTo(16);
        assertThat(analysis.nodes().get(1).getTravelDistanceKm()).isEqualByComparingTo("2.8");
        assertThat(analysis.nodes().get(1).getRoutePathPoints()).isEmpty();
        assertThat(analysis.nodes().get(1).getSegmentRouteGuide()).isNull();
    }

    @Test
    void analyzeRouteAddsPoiSpecificWarmNotes() {
        TravelTimeService travelTimeService = mock(TravelTimeService.class);
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, new SegmentRouteGuideService());

        GenerateReqDTO req = new GenerateReqDTO();
        req.setStartTime("09:00");
        req.setEndTime("18:00");

        Poi qingchengshan = createPoi(1L, "青城山", "nature");
        Poi ifs = createPoi(2L, "IFS 国际金融中心", "shopping");
        ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(List.of(qingchengshan, ifs), "1-2", 100.0D);

        stubCommonRouteOptimizer(routeOptimizer, req);
        when(travelTimeService.estimateTravelTimeMinutes(any(Poi.class), any(Poi.class))).thenReturn(20);

        RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

        assertThat(analysis.nodes()).hasSize(2);
        assertThat(analysis.nodes().get(0).getStatusNote()).contains("防滑鞋");
        assertThat(analysis.nodes().get(1).getStatusNote()).contains("主入口");
    }

    @Test
    void analyzeRouteRemovesLegacyBusinessStatusWarnings() {
        TravelTimeService travelTimeService = mock(TravelTimeService.class);
        ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
        RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, new SegmentRouteGuideService());

        GenerateReqDTO req = new GenerateReqDTO();
        req.setStartTime("09:00");

        Poi stalePoi = createPoi(1L, "春熙路商圈", "shopping");
        stalePoi.setStatusStale(true);
        stalePoi.setOpenTime(null);
        stalePoi.setCloseTime(null);
        stalePoi.setAvailabilityNote("当前场馆状态超过 14 天未核验，请出发前再次确认。");
        ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(List.of(stalePoi), "1", 60.0D);

        stubCommonRouteOptimizer(routeOptimizer, req);

        RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

        assertThat(analysis.nodes()).hasSize(1);
        assertThat(analysis.nodes().get(0).getStatusNote())
                .doesNotContain("14 天未核验")
                .doesNotContain("营业状态更新时间较久")
                .doesNotContain("营业时间信息不完整");
        assertThat(analysis.alerts())
                .noneMatch(alert -> alert.contains("营业状态更新时间较久"))
                .noneMatch(alert -> alert.contains("14"))
                .noneMatch(alert -> alert.contains("营业时间信息不完整"));
    }

    private void stubCommonRouteOptimizer(ItineraryRouteOptimizer routeOptimizer, GenerateReqDTO req) {
        when(routeOptimizer.normalizeRequest(req)).thenReturn(req);
        when(routeOptimizer.parseTimeMinutes("09:00", ItineraryRouteOptimizer.DEFAULT_START_MINUTE)).thenReturn(540);
        when(routeOptimizer.resolveOpenMinute(any(Poi.class), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
        when(routeOptimizer.formatTime(anyInt())).thenAnswer(invocation -> {
            int minute = invocation.getArgument(0);
            int hour = Math.floorMod(minute / 60, 24);
            int mins = Math.floorMod(minute, 60);
            return String.format("%02d:%02d", hour, mins);
        });
    }

    private Poi departurePoi() {
        Poi departurePoi = new Poi();
        departurePoi.setId(-1L);
        departurePoi.setLatitude(BigDecimal.valueOf(30.650D));
        departurePoi.setLongitude(BigDecimal.valueOf(104.060D));
        departurePoi.setCityName("成都");
        departurePoi.setName("当前位置");
        return departurePoi;
    }

    private Poi createPoi(Long id, String name, String category) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setCategory(category);
        poi.setDistrict("成都");
        poi.setAddress("成都市测试地址");
        poi.setOpenTime(LocalTime.of(9, 0));
        poi.setCloseTime(LocalTime.of(21, 0));
        poi.setStayDuration(90);
        poi.setAvgCost(BigDecimal.valueOf(60));
        poi.setLatitude(BigDecimal.valueOf(30.6D));
        poi.setLongitude(BigDecimal.valueOf(104.0D));
        return poi;
    }

    private GeoPoint point(double latitude, double longitude) {
        return new GeoPoint(BigDecimal.valueOf(latitude), BigDecimal.valueOf(longitude));
    }
}

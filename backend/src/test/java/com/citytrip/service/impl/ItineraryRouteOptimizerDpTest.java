package com.citytrip.service.impl;

import com.citytrip.config.AlgorithmWeightsProperties;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.PoiService;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.domain.scoring.DynamicAlgorithmWeightProvider;
import com.citytrip.service.geo.GeoPoint;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ItineraryRouteOptimizerDpTest {

    private static final double SCORE_WEIGHT = 6.0D;
    private static final double WAIT_PENALTY_WEIGHT = 0.5D;
    private static final double TRAVEL_PENALTY_WEIGHT = 1.0D;
    private static final double CROWD_PENALTY_WEIGHT = 4.0D;

    @Test
    void paretoDpFindsExactBestRouteWithinOneSecond() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        List<Poi> pois = buildChengduPois();
        Map<Long, Integer> indexByPoiId = buildIndexByPoiId(pois);
        int[][] travelMatrix = buildChengduTravelMatrix();
        TravelTimeService travelTimeService = new MatrixTravelTimeService(indexByPoiId, travelMatrix);
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);
        GenerateReqDTO request = buildRequest();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
            List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 4);

            assertThat(prepared).hasSize(6);
            assertThat(ranked).isNotEmpty();

            ItineraryRouteOptimizer.RouteOption top = ranked.get(0);
            ExhaustiveBestRoute bruteForce = bruteForceBestRoute(prepared, request, travelTimeService, 4, optimizer);

            assertThat(top.signature()).isEqualTo(bruteForce.signature());
            assertThat(top.utility()).isCloseTo(bruteForce.utility(), withinDelta(0.0001D));
            assertThat(top.path()).hasSizeLessThanOrEqualTo(4);
            assertThat(top.path()).extracting(Poi::getName).doesNotContain("Chunxi Road");
        });
    }

    @Test
    void fallsBackToBeamSearchWhenCandidateCountExceedsThreshold() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        List<Poi> pois = new ArrayList<>();
        for (int i = 0; i < 16; i++) {
            Poi poi = createPoi(
                    1000L + i,
                    "Linear POI " + i,
                    "culture",
                    "Qingyang",
                    "09:00",
                    "18:00",
                    60,
                    20,
                    4.0D + (16 - i) * 0.05D,
                    0.30D + i * 0.05D,
                    "culture,history"
            );
            pois.add(poi);
        }
        Map<Long, Integer> indexByPoiId = buildIndexByPoiId(pois);
        int[][] travelMatrix = new int[16][16];
        for (int i = 0; i < 16; i++) {
            for (int j = 0; j < 16; j++) {
                travelMatrix[i][j] = i == j ? 0 : Math.abs(i - j) * 6 + 8;
            }
        }
        TravelTimeService travelTimeService = new MatrixTravelTimeService(indexByPoiId, travelMatrix);
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);
        GenerateReqDTO request = buildRequest();

        assertTimeoutPreemptively(Duration.ofSeconds(1), () -> {
            List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
            List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 5);

            assertThat(prepared).hasSize(16);
            assertThat(ranked).isNotEmpty();
            assertThat(ranked.get(0).path().size()).isLessThanOrEqualTo(5);
        });
    }

    @Test
    void handlesSingleCandidateZeroBudgetAndTenMinuteWindowWithoutThrowing() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        Poi onlyPoi = createPoi(
                501L,
                "Free Mini Museum",
                "museum",
                "Qingyang",
                "09:00",
                "18:00",
                30,
                0,
                4.20D,
                0.10D,
                "culture,history,indoor"
        );
        List<Poi> pois = List.of(onlyPoi);
        Map<Long, Integer> indexByPoiId = buildIndexByPoiId(pois);
        TravelTimeService travelTimeService = new MatrixTravelTimeService(indexByPoiId, new int[][]{{0}});
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-18");
        request.setBudgetLevel("0");
        request.setThemes(List.of("culture"));
        request.setIsRainy(false);
        request.setIsNight(false);
        request.setWalkingLevel("low");
        request.setCompanionType("solo");
        request.setStartTime("09:00");
        request.setEndTime("09:10");

        List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 3);
        ItineraryRouteOptimizer.RouteOption best = optimizer.bestRoute(prepared, request, 3);

        assertThat(prepared).hasSize(1);
        assertThat(prepared.get(0).getTempScore()).isNotNull().isPositive();
        assertThat(ranked).isEmpty();
        assertThat(best.path()).isEmpty();
        assertThat(best.signature()).isEmpty();
        assertThat(best.utility()).isZero();
    }

    @Test
    void prioritizesLowWalkingRainFriendlyGroupPoiForMultiPersonRainyTrip() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        Poi groupFriendly = createPoi(701L, "Community Art Center", "museum", "Qingyang",
                "09:00", "18:00", 60, 20, 4.00D, 0.10D, "culture,indoor,group");
        groupFriendly.setIndoor(0);
        groupFriendly.setRainFriendly(1);
        groupFriendly.setWalkingLevel("low");
        groupFriendly.setSuitableFor("friends,family,team");

        Poi soloFriendly = createPoi(702L, "Quiet Reading Room", "museum", "Qingyang",
                "09:00", "18:00", 60, 20, 4.20D, 0.10D, "culture,indoor,quiet");
        soloFriendly.setIndoor(1);
        soloFriendly.setRainFriendly(1);
        soloFriendly.setWalkingLevel("low");
        soloFriendly.setSuitableFor("solo,quiet");

        Poi highWalking = createPoi(703L, "Mountain Trail", "trail", "Dujiangyan",
                "09:00", "18:00", 60, 20, 5.50D, 0.10D, "culture,outdoor,group");
        highWalking.setIndoor(0);
        highWalking.setRainFriendly(1);
        highWalking.setWalkingLevel("high");
        highWalking.setSuitableFor("friends,team");

        Poi notRainFriendly = createPoi(704L, "Open Plaza", "plaza", "Jinjiang",
                "09:00", "18:00", 60, 20, 5.50D, 0.10D, "culture,outdoor,group");
        notRainFriendly.setIndoor(0);
        notRainFriendly.setRainFriendly(0);
        notRainFriendly.setWalkingLevel("low");
        notRainFriendly.setSuitableFor("friends,team");

        List<Poi> pois = List.of(groupFriendly, soloFriendly, highWalking, notRainFriendly);
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(
                poiService,
                new MatrixTravelTimeService(buildIndexByPoiId(pois), new int[][]{
                        {0, 5, 5, 5},
                        {5, 0, 5, 5},
                        {5, 5, 0, 5},
                        {5, 5, 5, 0}
                })
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-26");
        request.setThemes(List.of("culture"));
        request.setIsRainy(true);
        request.setWalkingLevel("low");
        request.setCompanionType("多人");
        request.setStartTime("09:00");
        request.setEndTime("18:00");

        List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);

        assertThat(prepared).extracting(Poi::getName)
                .containsExactly("Community Art Center", "Quiet Reading Room");
    }

    @Test
    void prioritizesRouteContainingMustVisitPoi() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        List<Poi> pois = new ArrayList<>();
        pois.add(createPoi(801L, "宽窄巷子", "district", "Qingyang", "09:00", "22:00", 90, 100, 4.8D, 0.5D, "culture,food"));
        pois.add(createPoi(802L, "春熙路", "shopping", "Jinjiang", "10:00", "23:00", 90, 120, 4.9D, 0.6D, "shopping,night"));
        pois.add(createPoi(803L, "IFS国际金融中心", "shopping", "Jinjiang", "10:00", "23:00", 90, 120, 3.0D, 0.4D, "shopping,landmark"));

        Map<Long, Integer> indexByPoiId = buildIndexByPoiId(pois);
        int[][] travelMatrix = new int[][]{
                {0, 10, 12},
                {10, 0, 8},
                {12, 8, 0}
        };
        TravelTimeService travelTimeService = new MatrixTravelTimeService(indexByPoiId, travelMatrix);
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-24");
        request.setThemes(List.of("shopping"));
        request.setWalkingLevel("medium");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        request.setMustVisitPoiNames(List.of("IFS国际金融中心"));

        List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 2);

        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).path()).extracting(Poi::getName).contains("IFS国际金融中心");
    }

    @Test
    void enforcesTotalBudgetAsHardConstraintDuringSearch() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Poi museum = createPoi(1501L, "Budget Museum", "museum", "Qingyang", "09:00", "18:00", 60, 70, 4.5D, 0.1D, "culture");
        Poi foodStreet = createPoi(1502L, "Budget Food Street", "food", "Jinjiang", "09:00", "22:00", 60, 55, 4.4D, 0.1D, "food");
        Poi park = createPoi(1503L, "Budget Park", "park", "Qingyang", "09:00", "18:00", 60, 20, 4.0D, 0.1D, "nature");
        museum.setTempScore(18.0D);
        foodStreet.setTempScore(17.5D);
        park.setTempScore(10.0D);

        List<Poi> pois = List.of(museum, foodStreet, park);
        TravelTimeService travelTimeService = new MatrixTravelTimeService(buildIndexByPoiId(pois), new int[][]{
                {0, 10, 8},
                {10, 0, 6},
                {8, 6, 0}
        });
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-05-05");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        request.setTotalBudget(90D);

        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(pois, request, 2);

        assertThat(ranked).isNotEmpty();
        BigDecimal topCost = ranked.get(0).path().stream()
                .map(Poi::getAvgCost)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(topCost.doubleValue()).isLessThanOrEqualTo(90D);
        assertThat(ranked.get(0).path()).extracting(Poi::getName)
                .doesNotContainSequence("Budget Museum", "Budget Food Street");
    }

    @Test
    void returnsEmptyWhenMustVisitPoiCannotFitWithinTimeWindow() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Poi easyStop = createPoi(1601L, "Easy Stop", "museum", "Qingyang", "09:00", "18:00", 60, 20, 5.0D, 0.1D, "culture");
        Poi mustVisit = createPoi(1602L, "Hard Must Visit", "heritage", "Wuhou", "09:00", "09:20", 60, 30, 6.0D, 0.1D, "history");
        easyStop.setTempScore(18.0D);
        mustVisit.setTempScore(25.0D);

        List<Poi> pois = List.of(easyStop, mustVisit);
        TravelTimeService travelTimeService = new MatrixTravelTimeService(buildIndexByPoiId(pois), new int[][]{
                {0, 15},
                {15, 0}
        });
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-05-05");
        request.setStartTime("09:00");
        request.setEndTime("10:00");
        request.setMustVisitPoiNames(List.of("Hard Must Visit"));

        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(pois, request, 1);

        assertThat(ranked).isEmpty();
    }


    @Test
    void prioritizesIfsPoiWhenMustVisitKeywordUsesAlias() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        List<Poi> pois = new ArrayList<>();
        pois.add(createPoi(901L, "IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3", "shopping", "Jinjiang", "10:00", "23:00", 90, 120, 3.0D, 0.3D, "shopping,landmark"));
        pois.add(createPoi(902L, "\u5bbd\u7a84\u5df7\u5b50", "district", "Qingyang", "09:00", "22:00", 90, 100, 4.8D, 0.4D, "culture,food"));

        Map<Long, Integer> indexByPoiId = buildIndexByPoiId(pois);
        int[][] travelMatrix = new int[][]{
                {0, 10},
                {10, 0}
        };
        TravelTimeService travelTimeService = new MatrixTravelTimeService(indexByPoiId, travelMatrix);
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-24");
        request.setThemes(List.of("shopping"));
        request.setWalkingLevel("medium");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        request.setMustVisitPoiNames(List.of("IFS\u91d1\u878d\u4e2d\u5fc3"));

        List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 1);

        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).path()).extracting(Poi::getName).contains("IFS\u56fd\u9645\u91d1\u878d\u4e2d\u5fc3");
    }

    @Test
    void prioritizesPandaRouteWhenNaturalLanguageMentionsPanda() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        List<Poi> pois = new ArrayList<>();
        pois.add(createPoi(911L, "春熙路", "shopping", "Jinjiang", "10:00", "23:00", 90, 120, 5.0D, 0.2D, "shopping,night"));
        pois.add(createPoi(912L, "成都大熊猫繁育研究基地", "scenic", "Chenghua", "08:00", "17:30", 120, 55, 2.0D, 0.2D, "自然,动物,熊猫"));
        pois.add(createPoi(913L, "成都动物园", "scenic", "Chenghua", "08:00", "17:30", 100, 20, 2.5D, 0.2D, "自然,动物"));

        TravelTimeService travelTimeService = new MatrixTravelTimeService(buildIndexByPoiId(pois), new int[][]{
                {0, 20, 16},
                {20, 0, 8},
                {16, 8, 0}
        });
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-05-03");
        request.setWalkingLevel("medium");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        request.setNaturalLanguageRequirement("我想一个人去看大熊猫");

        List<Poi> prepared = optimizer.prepareCandidates(pois, request, false);
        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(prepared, request, 1);

        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).path())
                .extracting(Poi::getName)
                .containsAnyOf("成都大熊猫繁育研究基地", "成都动物园");
    }

    @Test
    void prioritizesReachableFirstStopWhenDepartureLegIsFarAndInconvenient() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setStatusStale(false);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });

        Poi nearPoi = createPoi(1001L, "Near Museum", "museum", "Qingyang", "09:00", "18:00", 90, 0, 3.0D, 0.0D, "culture");
        nearPoi.setTempScore(9.0D);
        nearPoi.setLatitude(BigDecimal.valueOf(30.6600D));
        nearPoi.setLongitude(BigDecimal.valueOf(104.0600D));

        Poi farPoi = createPoi(1002L, "Far Landmark", "landmark", "Longquanyi", "09:00", "18:00", 90, 0, 6.0D, 0.0D, "culture");
        farPoi.setTempScore(17.0D);
        farPoi.setLatitude(BigDecimal.valueOf(30.7400D));
        farPoi.setLongitude(BigDecimal.valueOf(104.2100D));

        TravelTimeService travelTimeService = new TravelTimeService() {
            @Override
            public int estimateTravelTimeMinutes(Poi from, Poi to) {
                return estimateTravelLeg(from, to).estimatedMinutes();
            }

            @Override
            public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
                if (from != null && from.getId() != null && from.getId() < 0 && to != null && Long.valueOf(1001L).equals(to.getId())) {
                    return new TravelLegEstimate(
                            8,
                            BigDecimal.valueOf(1.2D),
                            "???",
                            List.of(
                                    new GeoPoint(BigDecimal.valueOf(30.6590D), BigDecimal.valueOf(104.0580D)),
                                    new GeoPoint(BigDecimal.valueOf(30.6600D), BigDecimal.valueOf(104.0600D))
                            )
                    );
                }
                if (from != null && from.getId() != null && from.getId() < 0 && to != null && Long.valueOf(1002L).equals(to.getId())) {
                    return new TravelLegEstimate(
                            50,
                            BigDecimal.valueOf(16.0D),
                            "???+???",
                            List.of(
                                    new GeoPoint(BigDecimal.valueOf(30.6590D), BigDecimal.valueOf(104.0580D)),
                                    new GeoPoint(BigDecimal.valueOf(30.7000D), BigDecimal.valueOf(104.1200D)),
                                    new GeoPoint(BigDecimal.valueOf(30.7400D), BigDecimal.valueOf(104.2100D))
                            )
                    );
                }
                return new TravelLegEstimate(12, BigDecimal.valueOf(2.0D), "???");
            }
        };

        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(poiService, travelTimeService);
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-25");
        request.setThemes(List.of("culture"));
        request.setWalkingLevel("medium");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        request.setDepartureLatitude(30.6590D);
        request.setDepartureLongitude(104.0580D);
        request.setDeparturePlaceName("My Hotel");

        List<ItineraryRouteOptimizer.RouteOption> ranked = optimizer.rankRoutes(List.of(nearPoi, farPoi), request, 1);

        assertThat(ranked).isNotEmpty();
        assertThat(ranked.get(0).path()).extracting(Poi::getName).containsExactly("Near Museum");
    }

    @Test
    void routeSearchReadsUpdatedRouteWeightsAtRuntime() {
        PoiService poiService = mock(PoiService.class);
        DynamicAlgorithmWeightProvider provider = new DynamicAlgorithmWeightProvider(new AlgorithmWeightsProperties());
        Poi poi = createPoi(1201L, "Weight Probe Museum", "museum", "Qingyang",
                "09:00", "18:00", 60, 0, 3.0D, 0.0D, "culture");
        poi.setTempScore(10.0D);
        TravelTimeService travelTimeService = new MatrixTravelTimeService(
                buildIndexByPoiId(List.of(poi)),
                new int[][]{{0}}
        );
        ItineraryRouteOptimizer optimizer = new ItineraryRouteOptimizer(
                poiService,
                travelTimeService,
                null,
                null,
                null,
                provider
        );
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-25");
        request.setStartTime("09:00");
        request.setEndTime("18:00");

        double before = optimizer.rankRoutes(List.of(poi), request, 1).get(0).utility();

        provider.update(provider.current().withRouteScoreWeight(3.0D));
        double after = optimizer.rankRoutes(List.of(poi), request, 1).get(0).utility();

        assertThat(before).isEqualTo(60.0D);
        assertThat(after).isEqualTo(30.0D);
    }

    private GenerateReqDTO buildRequest() {
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-15");
        request.setBudgetLevel("medium");
        request.setThemes(List.of("culture", "history"));
        request.setIsRainy(false);
        request.setIsNight(false);
        request.setWalkingLevel("medium");
        request.setCompanionType("friends");
        request.setStartTime("09:00");
        request.setEndTime("18:00");
        return request;
    }

    private List<Poi> buildChengduPois() {
        List<Poi> pois = new ArrayList<>();
        pois.add(createPoi(101L, "Chengdu Museum", "museum", "Qingyang", "09:00", "17:00", 120, 0, 4.80D, 0.60D, "culture,history,indoor"));
        pois.add(createPoi(102L, "Wenshu Monastery", "temple", "Qingyang", "08:30", "17:30", 60, 0, 3.80D, 0.20D, "culture,quiet"));
        pois.add(createPoi(103L, "Kuanzhai Alley", "district", "Qingyang", "12:00", "22:00", 100, 90, 4.00D, 3.00D, "culture,food"));
        pois.add(createPoi(104L, "Wuhou Shrine", "heritage", "Wuhou", "09:00", "18:00", 90, 50, 4.60D, 0.80D, "culture,history"));
        pois.add(createPoi(105L, "Du Fu Cottage", "heritage", "Qingyang", "09:00", "18:00", 90, 50, 4.40D, 0.70D, "culture,history"));
        pois.add(createPoi(106L, "Chunxi Road", "shopping", "Jinjiang", "10:00", "23:00", 120, 200, 3.90D, 3.50D, "shopping,night"));
        return pois;
    }

    private Poi createPoi(long id,
                          String name,
                          String category,
                          String district,
                          String openTime,
                          String closeTime,
                          int stayDuration,
                          int avgCost,
                          double priorityScore,
                          double crowdPenalty,
                          String tags) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setCategory(category);
        poi.setDistrict(district);
        poi.setOpenTime(LocalTime.parse(openTime));
        poi.setCloseTime(LocalTime.parse(closeTime));
        poi.setStayDuration(stayDuration);
        poi.setAvgCost(BigDecimal.valueOf(avgCost));
        poi.setPriorityScore(BigDecimal.valueOf(priorityScore));
        poi.setCrowdPenalty(BigDecimal.valueOf(crowdPenalty));
        poi.setIndoor(1);
        poi.setNightAvailable(0);
        poi.setRainFriendly(1);
        poi.setWalkingLevel("medium");
        poi.setTags(tags);
        poi.setSuitableFor("friends,solo,family");
        poi.setDescription(name + " sample");
        return poi;
    }

    private Map<Long, Integer> buildIndexByPoiId(List<Poi> pois) {
        Map<Long, Integer> indexByPoiId = new LinkedHashMap<>();
        for (int i = 0; i < pois.size(); i++) {
            indexByPoiId.put(pois.get(i).getId(), i);
        }
        return indexByPoiId;
    }

    private int[][] buildChengduTravelMatrix() {
        return new int[][]{
                {0, 10, 16, 22, 25, 18},
                {10, 0, 14, 28, 26, 16},
                {16, 14, 0, 18, 16, 20},
                {22, 28, 18, 0, 12, 30},
                {25, 26, 16, 12, 0, 32},
                {18, 16, 20, 30, 32, 0}
        };
    }

    private ExhaustiveBestRoute bruteForceBestRoute(List<Poi> candidates,
                                                    GenerateReqDTO request,
                                                    TravelTimeService travelTimeService,
                                                    int maxStops,
                                                    ItineraryRouteOptimizer optimizer) {
        BestHolder holder = new BestHolder();
        dfs(candidates, request, travelTimeService, optimizer, 0, -1,
                optimizer.parseTimeMinutes(request.getStartTime(), ItineraryRouteOptimizer.DEFAULT_START_MINUTE),
                0.0D, new ArrayList<>(), maxStops, holder);
        return new ExhaustiveBestRoute(holder.bestSignature, holder.bestUtility);
    }

    private void dfs(List<Poi> candidates,
                     GenerateReqDTO request,
                     TravelTimeService travelTimeService,
                     ItineraryRouteOptimizer optimizer,
                     int mask,
                     int lastIndex,
                     int currentMinute,
                     double utility,
                     List<Integer> path,
                     int maxStops,
                     BestHolder holder) {
        if (!path.isEmpty()) {
            String signature = path.stream()
                    .map(index -> String.valueOf(candidates.get(index).getId()))
                    .reduce((left, right) -> left + "-" + right)
                    .orElse("");
            if (utility > holder.bestUtility
                    || (Math.abs(utility - holder.bestUtility) < 1e-9 && path.size() > holder.bestSize)
                    || (Math.abs(utility - holder.bestUtility) < 1e-9 && path.size() == holder.bestSize
                    && currentMinute < holder.bestFinishMinute)) {
                holder.bestUtility = utility;
                holder.bestSignature = signature;
                holder.bestSize = path.size();
                holder.bestFinishMinute = currentMinute;
            }
        }

        if (path.size() >= maxStops) {
            return;
        }

        int endMinute = optimizer.parseTimeMinutes(request.getEndTime(), ItineraryRouteOptimizer.DEFAULT_END_MINUTE);
        int startMinute = optimizer.parseTimeMinutes(request.getStartTime(), ItineraryRouteOptimizer.DEFAULT_START_MINUTE);

        for (int nextIndex = 0; nextIndex < candidates.size(); nextIndex++) {
            if ((mask & (1 << nextIndex)) != 0) {
                continue;
            }
            Poi poi = candidates.get(nextIndex);
            int travelTime = lastIndex < 0 ? 0 : travelTimeService.estimateTravelTimeMinutes(candidates.get(lastIndex), poi);
            int arrival = currentMinute + travelTime;
            int visitStart = Math.max(arrival, optimizer.resolveOpenMinute(poi, startMinute));
            int waitTime = Math.max(0, visitStart - arrival);
            int visitEnd = visitStart + poi.getStayDuration();

            if (visitEnd > optimizer.resolveCloseMinute(poi, endMinute) || visitEnd > endMinute) {
                continue;
            }

            double nextUtility = utility
                    + poi.getTempScore() * SCORE_WEIGHT
                    - travelTime * TRAVEL_PENALTY_WEIGHT
                    - waitTime * WAIT_PENALTY_WEIGHT
                    - resolveVisitCrowdPenalty(poi, visitStart) * CROWD_PENALTY_WEIGHT;

            path.add(nextIndex);
            dfs(candidates, request, travelTimeService, optimizer,
                    mask | (1 << nextIndex), nextIndex, visitEnd, nextUtility, path, maxStops, holder);
            path.remove(path.size() - 1);
        }
    }

    private double resolveVisitCrowdPenalty(Poi poi, int visitStartMinute) {
        double penalty = poi.getCrowdPenalty() == null ? 0.0D : poi.getCrowdPenalty().doubleValue();
        double factor = 1.0D;
        if (visitStartMinute >= 11 * 60 && visitStartMinute < 14 * 60) {
            factor += 0.25D;
        }
        return penalty * factor;
    }

    private org.assertj.core.data.Offset<Double> withinDelta(double delta) {
        return org.assertj.core.data.Offset.offset(delta);
    }

    private record ExhaustiveBestRoute(String signature, double utility) {
    }

    private static final class BestHolder {
        private double bestUtility = Double.NEGATIVE_INFINITY;
        private String bestSignature = "";
        private int bestSize = 0;
        private int bestFinishMinute = Integer.MAX_VALUE;
    }

    private static final class MatrixTravelTimeService implements TravelTimeService {
        private final Map<Long, Integer> indexByPoiId;
        private final int[][] travelMatrix;

        private MatrixTravelTimeService(Map<Long, Integer> indexByPoiId, int[][] travelMatrix) {
            this.indexByPoiId = new HashMap<>(indexByPoiId);
            this.travelMatrix = travelMatrix;
        }

        @Override
        public int estimateTravelTimeMinutes(Poi from, Poi to) {
            if (from == null || to == null || from.getId() == null || to.getId() == null) {
                return 0;
            }
            Integer fromIndex = indexByPoiId.get(from.getId());
            Integer toIndex = indexByPoiId.get(to.getId());
            if (fromIndex == null || toIndex == null) {
                return 0;
            }
            return travelMatrix[fromIndex][toIndex];
        }
    }
}

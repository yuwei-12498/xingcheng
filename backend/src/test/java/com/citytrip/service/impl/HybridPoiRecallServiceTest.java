package com.citytrip.service.impl;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.mapper.UserBehaviorEventMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.query.UserPoiPreferenceStat;
import com.citytrip.service.PoiService;
import com.citytrip.service.TravelTimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class HybridPoiRecallServiceTest {

    @Test
    void recallsPersonalizedPoiFromBehaviorFacts() {
        UserBehaviorEventMapper userBehaviorEventMapper = mock(UserBehaviorEventMapper.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
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

        List<Poi> candidates = buildCandidates();
        Map<Long, Integer> indexByPoiId = buildIndex(candidates);
        ItineraryRouteOptimizer routeOptimizer = new ItineraryRouteOptimizer(
                poiService,
                new MatrixTravelTimeService(indexByPoiId)
        );
        HybridPoiRecallService service = new HybridPoiRecallService(
                userBehaviorEventMapper,
                poiMapper,
                routeOptimizer,
                false,
                null,
                new ObjectMapper()
        );

        when(userBehaviorEventMapper.selectUserPoiPreferences(11L, 180)).thenReturn(List.of(
                stat(11L, 101L, 6.8D),
                stat(11L, 102L, 5.9D)
        ));
        when(userBehaviorEventMapper.selectSimilarUserIdsByPoiIds(anyList(), eq(11L), eq(180), eq(120))).thenReturn(List.of(21L, 22L));
        when(userBehaviorEventMapper.selectUserPoiPreferencesByUserIdsAndPoiIds(anyList(), anyList(), eq(180))).thenReturn(List.of(
                stat(21L, 101L, 5.0D),
                stat(21L, 103L, 7.6D),
                stat(21L, 104L, 1.8D),
                stat(22L, 102L, 4.6D),
                stat(22L, 103L, 8.2D),
                stat(22L, 104L, 2.4D)
        ));
        when(poiMapper.selectBatchIds(anyList())).thenReturn(List.of(candidates.get(0), candidates.get(1)));

        HybridPoiRecallService.RecallResult result = service.recall(11L, buildRequest(), candidates, 3);

        assertThat(result.filteredCandidates()).hasSize(4);
        assertThat(result.recalledCandidates()).hasSize(3);
        assertThat(result.recallStrategy()).startsWith("hybrid-usercf-content-v1");
        assertThat(result.recalledCandidates().get(0).getId()).isEqualTo(103L);
    }

    @Test
    void profileTopKShouldPreferHighestAffinityTagsInsteadOfLowestOnes() {
        UserBehaviorEventMapper userBehaviorEventMapper = mock(UserBehaviorEventMapper.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> invocation.getArgument(0));

        List<Poi> candidates = new ArrayList<>();
        candidates.add(createPoi(201L, "Shopping Seed", "shopping", "Jinjiang", 4.2D, 0.2D, "shopping,landmark,night"));
        candidates.add(createPoi(202L, "Culture Seed", "museum", "Qingyang", 4.2D, 0.2D, "culture,history,indoor"));
        candidates.add(createPoi(203L, "Shopping Candidate", "shopping", "Jinjiang", 3.9D, 0.2D, "shopping,landmark,outdoor"));
        candidates.add(createPoi(204L, "Culture Candidate", "museum", "Qingyang", 3.9D, 0.2D, "culture,history,quiet"));

        ItineraryRouteOptimizer routeOptimizer = new ItineraryRouteOptimizer(
                poiService,
                new MatrixTravelTimeService(buildIndex(candidates))
        );
        HybridPoiRecallService service = new HybridPoiRecallService(
                userBehaviorEventMapper,
                poiMapper,
                routeOptimizer,
                false,
                null,
                new ObjectMapper()
        );

        when(userBehaviorEventMapper.selectUserPoiPreferences(31L, 180)).thenReturn(List.of(
                stat(31L, 201L, 12.0D),
                stat(31L, 202L, 4.0D)
        ));
        when(userBehaviorEventMapper.selectSimilarUserIdsByPoiIds(anyList(), eq(31L), eq(180), eq(120)))
                .thenReturn(List.of(41L));
        when(userBehaviorEventMapper.selectUserPoiPreferencesByUserIdsAndPoiIds(anyList(), anyList(), eq(180))).thenReturn(List.of(
                stat(41L, 201L, 9.0D),
                stat(41L, 203L, 8.5D),
                stat(41L, 204L, 8.4D)
        ));
        when(poiMapper.selectBatchIds(anyList())).thenReturn(List.of(candidates.get(0), candidates.get(1)));

        GenerateReqDTO request = buildRequest();
        request.setThemes(List.of("shopping"));

        HybridPoiRecallService.RecallResult result = service.recall(31L, request, candidates, 3);

        assertThat(result.recalledCandidates()).isNotEmpty();
        assertThat(result.recalledCandidates().get(0).getId()).isEqualTo(203L);
    }

    @Test
    void fallback_shouldAvoidRecentlyRepeatedPoi_whenNoNeighborFound() {
        UserBehaviorEventMapper userBehaviorEventMapper = mock(UserBehaviorEventMapper.class);
        PoiMapper poiMapper = mock(PoiMapper.class);
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

        List<Poi> candidates = buildCandidates();
        Map<Long, Integer> indexByPoiId = buildIndex(candidates);
        ItineraryRouteOptimizer routeOptimizer = new ItineraryRouteOptimizer(
                poiService,
                new MatrixTravelTimeService(indexByPoiId)
        );
        HybridPoiRecallService service = new HybridPoiRecallService(
                userBehaviorEventMapper,
                poiMapper,
                routeOptimizer,
                false,
                null,
                new ObjectMapper()
        );

        when(userBehaviorEventMapper.selectUserPoiPreferences(11L, 180)).thenReturn(List.of(
                stat(11L, 101L, 20.0D)
        ));
        when(userBehaviorEventMapper.selectSimilarUserIdsByPoiIds(anyList(), eq(11L), eq(180), eq(120)))
                .thenReturn(List.of());

        HybridPoiRecallService.RecallResult result = service.recall(11L, buildRequest(), candidates, 3);

        assertThat(result.recallStrategy()).contains("no-neighbor");
        assertThat(result.recalledCandidates()).hasSize(3);
        assertThat(result.recalledCandidates().get(0).getId()).isNotEqualTo(101L);
    }

    private GenerateReqDTO buildRequest() {
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDays(1.0D);
        request.setTripDate("2026-04-18");
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

    private List<Poi> buildCandidates() {
        List<Poi> pois = new ArrayList<>();
        pois.add(createPoi(101L, "Du Fu Cottage", "heritage", "Qingyang", 4.9D, 0.4D, "culture,history,poetry"));
        pois.add(createPoi(102L, "Wenshu Monastery", "temple", "Qingyang", 4.6D, 0.3D, "culture,history,quiet"));
        pois.add(createPoi(103L, "Chengdu Museum", "museum", "Qingyang", 4.1D, 0.2D, "history,exhibition,indoor"));
        pois.add(createPoi(104L, "Chunxi Road", "shopping", "Jinjiang", 3.8D, 0.8D, "food,shopping,night"));
        return pois;
    }

    private Poi createPoi(long id,
                          String name,
                          String category,
                          String district,
                          double priorityScore,
                          double crowdPenalty,
                          String tags) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName(name);
        poi.setCategory(category);
        poi.setDistrict(district);
        poi.setOpenTime(LocalTime.of(9, 0));
        poi.setCloseTime(LocalTime.of(18, 0));
        poi.setStayDuration(90);
        poi.setAvgCost(BigDecimal.valueOf(40));
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

    private Map<Long, Integer> buildIndex(List<Poi> pois) {
        Map<Long, Integer> indexByPoiId = new LinkedHashMap<>();
        for (int i = 0; i < pois.size(); i++) {
            indexByPoiId.put(pois.get(i).getId(), i);
        }
        return indexByPoiId;
    }

    private UserPoiPreferenceStat stat(Long userId, Long poiId, Double score) {
        UserPoiPreferenceStat stat = new UserPoiPreferenceStat();
        stat.setUserId(userId);
        stat.setPoiId(poiId);
        stat.setPreferenceScore(score);
        return stat;
    }

    private static final class MatrixTravelTimeService implements TravelTimeService {
        private final Map<Long, Integer> indexByPoiId;
        private final int[][] matrix = {
                {0, 10, 15, 25},
                {10, 0, 12, 28},
                {15, 12, 0, 18},
                {25, 28, 18, 0}
        };

        private MatrixTravelTimeService(Map<Long, Integer> indexByPoiId) {
            this.indexByPoiId = new HashMap<>(indexByPoiId);
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
            return matrix[fromIndex][toIndex];
        }
    }
}

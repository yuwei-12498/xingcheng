package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.PoiService;
import com.citytrip.service.domain.scoring.DefaultPoiScoringStrategy;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CandidatePreparationServiceTest {

    @Test
    void preparesCandidatesByFilteringScoringAndRemovingUnavailablePois() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setOperatingStatus("OPEN");
                poi.setStatusStale(false);
            }
            pois.stream()
                    .filter(poi -> "Unavailable Museum".equals(poi.getName()))
                    .findFirst()
                    .ifPresent(poi -> poi.setAvailableOnTripDate(false));
            return pois;
        });
        CandidatePreparationService service = new CandidatePreparationService(
                poiService,
                new ItineraryRequestNormalizer(),
                new DefaultPoiScoringStrategy(new ItineraryRequestNormalizer())
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-04-26");
        request.setThemes(List.of("culture"));
        request.setIsRainy(true);
        request.setWalkingLevel("low");
        request.setCompanionType("friends");
        request.setMustVisitPoiNames(List.of("Community"));

        Poi groupFriendly = poi("Community Art Center", "culture,indoor,group", "friends,family,team", "low");
        groupFriendly.setIndoor(0);
        groupFriendly.setRainFriendly(1);
        Poi soloFriendly = poi("Quiet Reading Room", "culture,indoor,quiet", "solo,quiet", "low");
        soloFriendly.setIndoor(1);
        soloFriendly.setRainFriendly(1);
        Poi highWalking = poi("Mountain Trail", "culture,outdoor,group", "friends,team", "high");
        highWalking.setIndoor(0);
        highWalking.setRainFriendly(1);
        Poi notRainFriendly = poi("Open Plaza", "culture,outdoor,group", "friends,team", "low");
        notRainFriendly.setIndoor(0);
        notRainFriendly.setRainFriendly(0);
        Poi unavailable = poi("Unavailable Museum", "culture,indoor,group", "friends,team", "low");
        unavailable.setIndoor(1);
        unavailable.setRainFriendly(1);

        List<Poi> prepared = service.prepareCandidates(
                List.of(soloFriendly, notRainFriendly, groupFriendly, highWalking, unavailable),
                request,
                false
        );

        assertThat(prepared).extracting(Poi::getName)
                .containsExactly("Community Art Center", "Quiet Reading Room");
        assertThat(prepared).allSatisfy(poi -> assertThat(poi.getTempScore()).isNotNull().isPositive());
    }

    @Test
    void preparesCandidatesShouldRankHighQualityExternalPoiAheadOfGenericLocalPoi() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });
        CandidatePreparationService service = new CandidatePreparationService(
                poiService,
                new ItineraryRequestNormalizer(),
                new DefaultPoiScoringStrategy(new ItineraryRequestNormalizer())
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-04-26");
        request.setThemes(List.of("culture"));
        request.setIsRainy(true);
        request.setWalkingLevel("low");
        request.setCompanionType("friends");

        Poi external = poi("External Museum", "culture,indoor,group", "friends,family,team", "low");
        external.setSourceType("external");
        external.setIndoor(1);
        external.setRainFriendly(1);
        external.setNightAvailable(1);
        external.setStatusStale(true);
        external.setPriorityScore(BigDecimal.valueOf(5.8D));
        external.setAvgCost(BigDecimal.valueOf(88));
        external.setCrowdPenalty(BigDecimal.valueOf(0.15D));
        assertThat(writeProperty(external, "setExternalDataCompleteness", Double.class, 0.95D)).isTrue();
        assertThat(writeProperty(external, "setExternalBusinessDetailsProvided", Boolean.class, true)).isTrue();

        Poi local = poi("Local Gallery", "culture,indoor,group", "friends,family,team", "low");
        local.setSourceType("local");
        local.setIndoor(1);
        local.setRainFriendly(1);
        local.setNightAvailable(1);
        local.setStatusStale(false);
        local.setPriorityScore(BigDecimal.valueOf(6.2D));
        local.setAvgCost(BigDecimal.valueOf(88));
        local.setCrowdPenalty(BigDecimal.valueOf(0.1D));

        List<Poi> prepared = service.prepareCandidates(List.of(local, external), request, false);

        assertThat(prepared).extracting(Poi::getName)
                .containsExactly("External Museum", "Local Gallery");
    }

    @Test
    void shouldKeepHotpotAndDropHardwareWhenFoodIntentIsExplicit() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });
        CandidatePreparationService service = new CandidatePreparationService(
                poiService,
                new ItineraryRequestNormalizer(),
                new DefaultPoiScoringStrategy(new ItineraryRequestNormalizer())
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-05-10");
        request.setThemes(List.of("美食"));
        request.setPreferredPoiCategories(List.of("火锅", "餐饮"));
        request.setExcludedPoiCategories(List.of("五金", "家装", "装修材料", "纱窗"));
        request.setBudgetLevel("低");
        request.setBudgetTight(true);
        request.setWalkingLevel("low");

        Poi hotpot = poi("蜀大侠火锅", "美食,火锅,餐饮", "friends,team", "low");
        hotpot.setCategory("火锅");
        hotpot.setPriorityScore(BigDecimal.valueOf(3.0D));
        hotpot.setAvgCost(BigDecimal.valueOf(80));

        Poi hardware = poi("自然风纱窗", "五金,家具,室内装修材料零售", "friends,team", "low");
        hardware.setCategory("五金、家具及室内装修材料零售");
        hardware.setPriorityScore(BigDecimal.valueOf(9.0D));
        hardware.setAvgCost(BigDecimal.valueOf(20));

        List<Poi> prepared = service.prepareCandidates(List.of(hardware, hotpot), request, false);

        assertThat(prepared).extracting(Poi::getName).containsExactly("蜀大侠火锅");
    }



    @Test
    void shouldKeepBarbecueAndDropHardwareWhenFoodIntentIsExplicit() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });
        CandidatePreparationService service = new CandidatePreparationService(
                poiService,
                new ItineraryRequestNormalizer(),
                new DefaultPoiScoringStrategy(new ItineraryRequestNormalizer())
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-05-10");
        request.setThemes(List.of("\u7f8e\u98df"));
        request.setPreferredPoiCategories(List.of("\u70e4\u8089", "\u70e7\u70e4", "\u9910\u996e"));
        request.setExcludedPoiCategories(List.of("\u4e94\u91d1", "\u5bb6\u88c5", "\u88c5\u4fee\u6750\u6599", "\u7eb1\u7a97"));
        request.setWalkingLevel("low");

        Poi barbecue = poi("\u5927\u7b7e\u95e8\u70e4\u8089", "\u7f8e\u98df,\u70e4\u8089,\u70e7\u70e4,\u9910\u996e", "friends,team", "low");
        barbecue.setCategory("\u70e4\u8089");
        barbecue.setPriorityScore(BigDecimal.valueOf(3.0D));

        Poi hardware = poi("\u81ea\u7136\u98ce\u7eb1\u7a97", "\u4e94\u91d1,\u5bb6\u5177,\u5ba4\u5185\u88c5\u4fee\u6750\u6599\u96f6\u552e", "friends,team", "low");
        hardware.setCategory("\u4e94\u91d1\u3001\u5bb6\u5177\u53ca\u5ba4\u5185\u88c5\u4fee\u6750\u6599\u96f6\u552e");
        hardware.setPriorityScore(BigDecimal.valueOf(9.0D));

        List<Poi> prepared = service.prepareCandidates(List.of(hardware, barbecue), request, false);

        assertThat(prepared).extracting(Poi::getName).containsExactly("\u5927\u7b7e\u95e8\u70e4\u8089");
    }

    @Test
    void shouldKeepNetCafeAndDropMuseumOrHardwareWhenLeisureIntentIsExplicit() {
        PoiService poiService = mock(PoiService.class);
        when(poiService.enrichOperatingStatus(anyList(), any(LocalDate.class))).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            List<Poi> pois = invocation.getArgument(0);
            for (Poi poi : pois) {
                poi.setAvailableOnTripDate(true);
                poi.setOperatingStatus("OPEN");
            }
            return pois;
        });
        CandidatePreparationService service = new CandidatePreparationService(
                poiService,
                new ItineraryRequestNormalizer(),
                new DefaultPoiScoringStrategy(new ItineraryRequestNormalizer())
        );

        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-05-10");
        request.setThemes(List.of("\u4f11\u95f2"));
        request.setPreferredPoiCategories(List.of("\u7f51\u5427", "\u7f51\u5496", "\u7535\u7ade", "\u5a31\u4e50"));
        request.setExcludedPoiCategories(List.of("\u535a\u7269\u9986", "\u666f\u533a", "\u4e94\u91d1", "\u5bb6\u88c5", "\u7eb1\u7a97"));
        request.setWalkingLevel("low");

        Poi netCafe = poi("\u718a\u732b\u7535\u7ade\u7f51\u5496", "\u4f11\u95f2,\u5a31\u4e50,\u7f51\u5496,\u7535\u7ade", "friends,team", "low");
        netCafe.setCategory("\u7f51\u5427");
        netCafe.setPriorityScore(BigDecimal.valueOf(3.0D));

        Poi museum = poi("\u6210\u90fd\u535a\u7269\u9986", "\u6587\u5316,\u535a\u7269\u9986", "friends,team", "low");
        museum.setCategory("\u535a\u7269\u9986");
        museum.setPriorityScore(BigDecimal.valueOf(9.0D));

        Poi hardware = poi("\u81ea\u7136\u98ce\u7eb1\u7a97", "\u4e94\u91d1,\u5bb6\u5177,\u5ba4\u5185\u88c5\u4fee\u6750\u6599\u96f6\u552e", "friends,team", "low");
        hardware.setCategory("\u4e94\u91d1\u3001\u5bb6\u5177\u53ca\u5ba4\u5185\u88c5\u4fee\u6750\u6599\u96f6\u552e");
        hardware.setPriorityScore(BigDecimal.valueOf(8.0D));

        List<Poi> prepared = service.prepareCandidates(List.of(museum, hardware, netCafe), request, false);

        assertThat(prepared).extracting(Poi::getName).containsExactly("\u718a\u732b\u7535\u7ade\u7f51\u5496");
    }

    private Poi poi(String name, String tags, String suitableFor, String walkingLevel) {
        Poi poi = new Poi();
        poi.setName(name);
        poi.setCategory("museum");
        poi.setDistrict("Qingyang");
        poi.setTags(tags);
        poi.setSuitableFor(suitableFor);
        poi.setWalkingLevel(walkingLevel);
        poi.setPriorityScore(BigDecimal.valueOf(4.0D));
        poi.setAvgCost(BigDecimal.valueOf(20));
        poi.setStayDuration(60);
        poi.setOpenTime(LocalTime.of(9, 0));
        poi.setCloseTime(LocalTime.of(18, 0));
        poi.setCrowdPenalty(BigDecimal.valueOf(0.1D));
        return poi;
    }

    private boolean writeProperty(Poi poi, String setterName, Class<?> parameterType, Object value) {
        try {
            Method method = Poi.class.getMethod(setterName, parameterType);
            method.invoke(poi, value);
            return true;
        } catch (ReflectiveOperationException ex) {
            return false;
        }
    }
}

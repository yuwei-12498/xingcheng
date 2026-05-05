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

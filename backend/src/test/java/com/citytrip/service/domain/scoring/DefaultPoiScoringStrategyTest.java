package com.citytrip.service.domain.scoring;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DefaultPoiScoringStrategyTest {

    @Test
    void rainyGroupTripRewardsGroupFriendlyLowWalkingPoiAndFiltersConstraints() {
        DefaultPoiScoringStrategy scoringStrategy = new DefaultPoiScoringStrategy(null);
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-04-26");
        request.setThemes(List.of("culture"));
        request.setIsRainy(true);
        request.setWalkingLevel("low");
        request.setCompanionType("friends");
        request.setMustVisitPoiNames(List.of("Community"));

        Poi groupFriendly = poi("Community Art Center", "museum", "culture,indoor,group", "friends,family,team", 4.0D, 20);
        groupFriendly.setIndoor(0);
        groupFriendly.setRainFriendly(1);
        groupFriendly.setWalkingLevel("low");

        Poi soloFriendly = poi("Quiet Reading Room", "museum", "culture,indoor,quiet", "solo,quiet", 4.2D, 20);
        soloFriendly.setIndoor(1);
        soloFriendly.setRainFriendly(1);
        soloFriendly.setWalkingLevel("low");

        Poi highWalking = poi("Mountain Trail", "trail", "culture,outdoor,group", "friends,team", 5.5D, 20);
        highWalking.setIndoor(0);
        highWalking.setRainFriendly(1);
        highWalking.setWalkingLevel("high");

        Poi notRainFriendly = poi("Open Plaza", "plaza", "culture,outdoor,group", "friends,team", 5.5D, 20);
        notRainFriendly.setIndoor(0);
        notRainFriendly.setRainFriendly(0);
        notRainFriendly.setWalkingLevel("low");

        ScoreBreakdown groupScore = scoringStrategy.score(request, groupFriendly);
        ScoreBreakdown soloScore = scoringStrategy.score(request, soloFriendly);

        assertThat(scoringStrategy.matchesWeatherConstraint(request, groupFriendly)).isTrue();
        assertThat(scoringStrategy.matchesWalkingConstraint(request, groupFriendly)).isTrue();
        assertThat(scoringStrategy.matchesWalkingConstraint(request, highWalking)).isFalse();
        assertThat(scoringStrategy.matchesWeatherConstraint(request, notRainFriendly)).isFalse();
        assertThat(groupScore.total()).isGreaterThan(soloScore.total());
        assertThat(groupScore.components()).containsKeys("mustVisit", "companion", "groupFit", "rain", "walking");
    }

    @Test
    void readsUpdatedWeightsAtRuntimeWithoutRecreatingStrategy() {
        DynamicAlgorithmWeightProvider provider = new DynamicAlgorithmWeightProvider(new com.citytrip.config.AlgorithmWeightsProperties());
        DefaultPoiScoringStrategy scoringStrategy = new DefaultPoiScoringStrategy(new com.citytrip.service.domain.planning.ItineraryRequestNormalizer(), provider);
        GenerateReqDTO request = new GenerateReqDTO();
        request.setCompanionType("friends");

        Poi poi = poi("Friend Cafe", "food", "culture,group", "friends", 4.0D, 20);
        double before = scoringStrategy.score(request, poi).component("companion");

        provider.update(provider.current().withCompanionMatchScore(8.0D));

        double after = scoringStrategy.score(request, poi).component("companion");
        assertThat(before).isEqualTo(2.5D);
        assertThat(after).isEqualTo(8.0D);
    }

    @Test
    void highQualityExternalPoiShouldOutscoreSlightlyHigherPriorityLocalPoi() {
        DefaultPoiScoringStrategy scoringStrategy = new DefaultPoiScoringStrategy(null);
        GenerateReqDTO request = new GenerateReqDTO();
        request.setTripDate("2026-04-26");
        request.setThemes(List.of("culture"));
        request.setIsRainy(true);
        request.setWalkingLevel("low");
        request.setCompanionType("friends");

        Poi external = poi("External Museum", "museum", "culture,indoor,group", "friends,family,team", 5.8D, 88);
        external.setSourceType("external");
        external.setIndoor(1);
        external.setRainFriendly(1);
        external.setWalkingLevel("low");
        external.setNightAvailable(1);
        external.setStatusStale(true);
        external.setCrowdPenalty(BigDecimal.valueOf(0.15D));
        assertThat(writeProperty(external, "setExternalDataCompleteness", Double.class, 0.95D)).isTrue();
        assertThat(writeProperty(external, "setExternalBusinessDetailsProvided", Boolean.class, true)).isTrue();

        Poi local = poi("Local Gallery", "museum", "culture,indoor,group", "friends,family,team", 6.2D, 88);
        local.setSourceType("local");
        local.setIndoor(1);
        local.setRainFriendly(1);
        local.setWalkingLevel("low");
        local.setNightAvailable(1);
        local.setStatusStale(false);
        local.setCrowdPenalty(BigDecimal.valueOf(0.1D));

        ScoreBreakdown externalScore = scoringStrategy.score(request, external);
        ScoreBreakdown localScore = scoringStrategy.score(request, local);

        assertThat(externalScore.total()).isGreaterThan(localScore.total());
        assertThat(externalScore.components()).containsKeys("externalRealtime", "externalDataCompleteness", "externalBusinessDetails");
        assertThat(localScore.components()).containsKey("localPriorityDamping");
        assertThat(localScore.component("localPriorityDamping")).isLessThan(0D);
    }

    private Poi poi(String name, String category, String tags, String suitableFor, double priority, int avgCost) {
        Poi poi = new Poi();
        poi.setName(name);
        poi.setCategory(category);
        poi.setTags(tags);
        poi.setSuitableFor(suitableFor);
        poi.setPriorityScore(BigDecimal.valueOf(priority));
        poi.setAvgCost(BigDecimal.valueOf(avgCost));
        poi.setStayDuration(60);
        poi.setOpenTime(java.time.LocalTime.of(9, 0));
        poi.setCloseTime(java.time.LocalTime.of(18, 0));
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

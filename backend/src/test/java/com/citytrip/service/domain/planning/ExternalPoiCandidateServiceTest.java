package com.citytrip.service.domain.planning;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ExternalPoiCandidateServiceTest {

    @Test
    void recallForReplanShouldSearchEachExternalPoiAgainAndUseReturnedBusinessDefaults() throws Exception {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        ExternalPoiCandidateService service = new ExternalPoiCandidateService(geoSearchService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setCityName("Chengdu");
        request.setCityCode("CD");

        Poi local = new Poi();
        local.setName("Local Museum");
        local.setCategory("museum");
        local.setDistrict("Jinjiang");

        GeoPoiCandidate raw = candidate("Skyline Museum", "museum", "Jinjiang", "104.080001", "30.650001");
        GeoPoiCandidate detail = candidate("Skyline Museum", "museum", "Jinjiang", "104.080001", "30.650001");
        setDetail(detail, "setOpeningHours", String.class, "10:30-21:45");
        setDetail(detail, "setAvgCost", BigDecimal.class, BigDecimal.valueOf(128));
        setDetail(detail, "setStayDurationMinutes", Integer.class, 75);

        when(geoSearchService.searchByKeyword("museum", "Chengdu", 8)).thenReturn(List.of(raw));
        when(geoSearchService.searchByKeyword("Jinjiang 景点", "Chengdu", 8)).thenReturn(List.of());
        when(geoSearchService.searchByKeyword("Skyline Museum", "Chengdu", 1)).thenReturn(List.of(detail));

        List<Poi> result = service.recallForReplan(List.of(local), request, 8);

        assertThat(result).hasSize(1);
        Poi poi = result.get(0);
        assertThat(poi.getSourceType()).isEqualTo("external");
        assertThat(poi.getOpenTime()).isEqualTo(LocalTime.of(10, 30));
        assertThat(poi.getCloseTime()).isEqualTo(LocalTime.of(21, 45));
        assertThat(poi.getAvgCost()).isEqualByComparingTo("128");
        assertThat(poi.getStayDuration()).isEqualTo(75);
        assertThat(poi.getAvailabilityNote()).contains("地图");
        verify(geoSearchService).searchByKeyword("Skyline Museum", "Chengdu", 1);
    }

    @Test
    void recallForReplanShouldInferRainSafeMallDefaultsForExternalPoi() throws Exception {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        ExternalPoiCandidateService service = new ExternalPoiCandidateService(geoSearchService);

        GenerateReqDTO request = new GenerateReqDTO();
        request.setCityName("Chengdu");
        request.setCityCode("CD");

        Poi local = new Poi();
        local.setName("Local Shopping Center");
        local.setCategory("shopping mall");
        local.setDistrict("Gaoxin");

        GeoPoiCandidate raw = candidate("IFS Mall", "shopping mall", "Gaoxin", "104.070001", "30.660001");
        GeoPoiCandidate detail = candidate("IFS Mall", "shopping mall", "Gaoxin", "104.070001", "30.660001");
        setDetail(detail, "setAddress", String.class, "No. 1 Renmin Rd");
        setDetail(detail, "setExternalId", String.class, "amap-ifs-001");
        setDetail(detail, "setOpeningHours", String.class, "10:00-22:00");
        setDetail(detail, "setAvgCost", BigDecimal.class, BigDecimal.valueOf(180));
        setDetail(detail, "setStayDurationMinutes", Integer.class, 120);

        when(geoSearchService.searchByKeyword("shopping mall", "Chengdu", 8)).thenReturn(List.of(raw));
        when(geoSearchService.searchByKeyword("Gaoxin 景点", "Chengdu", 8)).thenReturn(List.of());
        when(geoSearchService.searchByKeyword("IFS Mall", "Chengdu", 1)).thenReturn(List.of(detail));

        Poi poi = service.recallForReplan(List.of(local), request, 8).get(0);

        assertThat(poi.getIndoor()).isEqualTo(1);
        assertThat(poi.getRainFriendly()).isEqualTo(1);
        assertThat(poi.getNightAvailable()).isEqualTo(1);
        assertThat(poi.getWalkingLevel()).isEqualTo("low");
        assertThat(readGetter(poi, "getExternalDataCompleteness")).isEqualTo(1.0D);
        assertThat(readGetter(poi, "getExternalBusinessDetailsProvided")).isEqualTo(true);
    }

    private GeoPoiCandidate candidate(String name,
                                      String category,
                                      String district,
                                      String lng,
                                      String lat) {
        GeoPoiCandidate candidate = new GeoPoiCandidate();
        candidate.setName(name);
        candidate.setCategory(category);
        candidate.setDistrict(district);
        candidate.setCityName("Chengdu");
        candidate.setLongitude(new BigDecimal(lng));
        candidate.setLatitude(new BigDecimal(lat));
        return candidate;
    }

    private void setDetail(GeoPoiCandidate candidate,
                           String setterName,
                           Class<?> parameterType,
                           Object value) throws Exception {
        Method method = GeoPoiCandidate.class.getMethod(setterName, parameterType);
        method.invoke(candidate, value);
    }

    private Object readGetter(Poi poi, String getterName) {
        try {
            Method method = Poi.class.getMethod(getterName);
            return method.invoke(poi);
        } catch (ReflectiveOperationException ex) {
            return null;
        }
    }
}

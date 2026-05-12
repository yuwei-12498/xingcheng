package com.citytrip.service.ai.mcp;

import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class GeoNearbyMcpCapabilityTest {

    @Test
    void shouldCallGeoSearchNearbyAndNormalizeResults() {
        GeoSearchService geoSearchService = mock(GeoSearchService.class);
        GeoPoiCandidate candidate = new GeoPoiCandidate();
        candidate.setName("成都博物馆");
        candidate.setCategory("景点");
        candidate.setDistrict("青羊区");
        candidate.setCityName("成都");
        candidate.setAddress("小河街1号");
        candidate.setDistanceMeters(280D);
        candidate.setLatitude(BigDecimal.valueOf(30.658));
        candidate.setLongitude(BigDecimal.valueOf(104.063));
        candidate.setSource("amap");

        when(geoSearchService.searchNearby(any(GeoPoint.class), eq("成都"), eq("景点"), eq(1200), eq(2)))
                .thenReturn(List.of(candidate));

        GeoNearbyMcpCapability capability = new GeoNearbyMcpCapability(geoSearchService);

        Object result = capability.execute(Map.of(
                "center", Map.of("latitude", 30.657D, "longitude", 104.066D),
                "city", "成都",
                "category", "景点",
                "radiusMeters", 1200,
                "limit", 2
        ));

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result.toString())
                .contains("geo.nearby")
                .contains("成都博物馆")
                .contains("景点")
                .contains("280");
    }

    @Test
    void shouldReportUnavailableWhenGeoServiceMissing() {
        GeoNearbyMcpCapability capability = new GeoNearbyMcpCapability(null);

        Object result = capability.execute(Map.of(
                "center", Map.of("latitude", 30.657D, "longitude", 104.066D),
                "city", "成都"
        ));

        assertThat(result.toString()).contains("geo.nearby", "unavailable");
    }

    private static final class NoopGeoSearchService implements GeoSearchService {
        @Override
        public Optional<GeoPoint> geocode(String keyword, String cityName) {
            return Optional.empty();
        }

        @Override
        public List<GeoPoiCandidate> searchByKeyword(String keyword, String cityName, int limit) {
            return List.of();
        }

        @Override
        public List<GeoPoiCandidate> searchNearby(GeoPoint center, String cityName, String category, int radiusMeters, int limit) {
            return List.of();
        }

        @Override
        public Optional<GeoRouteEstimate> estimateTravel(GeoPoint from, GeoPoint to, String cityName, String preferredMode) {
            return Optional.empty();
        }
    }
}

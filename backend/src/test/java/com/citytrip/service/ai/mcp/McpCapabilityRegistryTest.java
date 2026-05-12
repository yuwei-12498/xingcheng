package com.citytrip.service.ai.mcp;

import org.junit.jupiter.api.Test;

import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoSearchService;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertTrue;

class McpCapabilityRegistryTest {

    @Test
    void registryShouldExposeNamedCapabilities() {
        McpCapabilityRegistry registry = new McpCapabilityRegistry(List.of(
                new StubCapability("geo.search"),
                new StubCapability("route.amap")
        ));

        assertTrue(registry.find("geo.search").isPresent());
        assertTrue(registry.find("route.amap").isPresent());
    }

    @Test
    void geoSearchCapabilityShouldCallGeoServiceInsteadOfEchoingInput() {
        GeoSearchMcpCapability capability = new GeoSearchMcpCapability(new StubGeoSearchService());
        Map<String, Object> input = Map.of("keyword", "万象城", "city", "成都", "limit", 2);

        Object result = capability.execute(input);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result).isNotEqualTo(input);
        assertThat(result.toString()).contains("geo.search", "ok", "成都万象城", "成华区");
    }

    @Test
    void amapRouteCapabilityShouldCallTravelTimeServiceInsteadOfEchoingInput() {
        AmapRouteMcpCapability capability = new AmapRouteMcpCapability(new StubTravelTimeService());
        Map<String, Object> input = Map.of(
                "from", Map.of("name", "春熙路", "lat", 30.657D, "lng", 104.080D),
                "to", Map.of("name", "成都万象城", "lat", 30.659D, "lng", 104.114D)
        );

        Object result = capability.execute(input);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result).isNotEqualTo(input);
        assertThat(result.toString()).contains("route.amap", "地铁+步行", "18");
    }

    @Test
    void capabilitiesShouldReportUnavailableWhenBackendServiceMissing() {
        assertThat(new GeoSearchMcpCapability(null).execute(Map.of("keyword", "万象城")).toString())
                .contains("geo.search", "unavailable");

        Object routeResult = new AmapRouteMcpCapability(null).execute(Map.of(
                "from", Map.of("name", "春熙路"),
                "to", Map.of("name", "成都万象城")
        ));
        assertThat(routeResult.toString()).contains("route.amap", "unavailable");
    }

    @Test
    void routeContextCapabilityShouldNormalizeContextInsteadOfEchoingInput() {
        RouteContextMcpCapability capability = new RouteContextMcpCapability();
        Map<String, Object> input = Map.of("summary", "城市文化路线", "nodes", List.of("成都博物馆", "宽窄巷子"));

        Object result = capability.execute(input);

        assertThat(result).isInstanceOf(Map.class);
        assertThat(result).isNotEqualTo(input);
        assertThat(result.toString()).contains("route.context", "城市文化路线", "成都博物馆");
    }

    private record StubCapability(String name) implements McpCapability {
        @Override
        public String capabilityName() {
            return name;
        }

        @Override
        public Object execute(Object input) {
            return input;
        }
    }

    private static final class StubGeoSearchService implements GeoSearchService {
        @Override
        public Optional<GeoPoint> geocode(String keyword, String cityName) {
            return Optional.empty();
        }

        @Override
        public List<GeoPoiCandidate> searchByKeyword(String keyword, String cityName, int limit) {
            GeoPoiCandidate candidate = new GeoPoiCandidate();
            candidate.setName(cityName + keyword);
            candidate.setAddress("双成二路");
            candidate.setCategory("购物中心");
            candidate.setDistrict("成华区");
            candidate.setCityName(cityName);
            candidate.setLatitude(BigDecimal.valueOf(30.659D));
            candidate.setLongitude(BigDecimal.valueOf(104.114D));
            candidate.setSource("amap");
            candidate.setDistanceMeters(320D);
            return List.of(candidate);
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

    private static final class StubTravelTimeService implements TravelTimeService {
        @Override
        public int estimateTravelTimeMinutes(Poi from, Poi to) {
            return 18;
        }

        @Override
        public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
            return new TravelLegEstimate(18, BigDecimal.valueOf(5.2D), "地铁+步行");
        }
    }
}

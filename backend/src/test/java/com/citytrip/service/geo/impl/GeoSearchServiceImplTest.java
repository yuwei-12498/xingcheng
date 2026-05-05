package com.citytrip.service.geo.impl;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoRouteEstimate;
import com.citytrip.service.geo.GeoRouteStep;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class GeoSearchServiceImplTest {

    @Test
    void searchByKeyword_shouldUseOfficialVivoQueryShape() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api-ai.vivo.com.cn/search/geo");
        properties.setKeywordSearchPath("/geo/search");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api-ai.vivo.com.cn/search/geo?"),
                containsString("keywords=%E6%98%A5%E7%86%99%E8%B7%AF"),
                containsString("city=%E6%88%90%E9%83%BD"),
                containsString("page_num=1"),
                containsString("page_size=5"),
                containsString("requestId=")
        )))
                .andRespond(withSuccess("""
                        {
                          "statusCode": 4,
                          "statusInfo": "cookie is null",
                          "pois": [
                            {
                              "name": "春熙路",
                              "typeName": "商圈",
                              "city": "成都市",
                              "district": "锦江区",
                              "location": "104.081703,30.65731",
                              "mid": "poi-001",
                              "distance": 180.2,
                              "score": 0.96
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        List<GeoPoiCandidate> candidates = service.searchByKeyword("春熙路", "成都", 5);

        server.verify();
        assertEquals(1, candidates.size());
        assertEquals("春熙路", candidates.get(0).getName());
        assertEquals("商圈", candidates.get(0).getCategory());
        assertEquals("poi-001", candidates.get(0).getExternalId());
    }

    @Test
    void searchByKeyword_shouldParseBusinessDefaultsWhenProviderReturnsThem() throws Exception {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setKeywordSearchPath("/geo/search");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(startsWith("https://api.example.com/geo/search?")))
                .andRespond(withSuccess("""
                        {
                          "pois": [
                            {
                              "name": "外部新馆",
                              "typeName": "博物馆",
                              "location": "104.081703,30.65731",
                              "businessHours": "10:00-22:30",
                              "avgCost": "118.5",
                              "stayDurationMinutes": 75
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        List<GeoPoiCandidate> candidates = service.searchByKeyword("外部新馆", "成都", 1);

        server.verify();
        assertEquals(1, candidates.size());
        GeoPoiCandidate candidate = candidates.get(0);
        assertEquals("10:00-22:30", GeoPoiCandidate.class.getMethod("getOpeningHours").invoke(candidate));
        assertEquals(new BigDecimal("118.5"), GeoPoiCandidate.class.getMethod("getAvgCost").invoke(candidate));
        assertEquals(75, GeoPoiCandidate.class.getMethod("getStayDurationMinutes").invoke(candidate));
    }

    @Test
    void searchNearby_shouldSortByDistance_andFilterToRadiusWhenAvailable() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api-ai.vivo.com.cn/search/geo");
        properties.setNearbySearchPath("/geo/nearby");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api-ai.vivo.com.cn/search/geo?"),
                containsString("keywords=%E9%85%92%E5%BA%97"),
                containsString("location=104.0843,30.6568")
        )))
                .andRespond(withSuccess("""
                        {
                          "statusCode": 4,
                          "statusInfo": "cookie is null",
                          "pois": [
                            {
                              "name": "A酒店",
                              "typeName": "酒店",
                              "location": "104.05,30.65",
                              "distance": 4500.0,
                              "score": 0.9
                            },
                            {
                              "name": "B酒店",
                              "typeName": "酒店",
                              "location": "104.0849,30.6571",
                              "distance": 480.0,
                              "score": 0.6
                            },
                            {
                              "name": "C酒店",
                              "typeName": "酒店",
                              "location": "104.0873,30.6596",
                              "distance": 900.0,
                              "score": 0.7
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        List<GeoPoiCandidate> candidates = service.searchNearby(
                new GeoPoint(BigDecimal.valueOf(30.6568), BigDecimal.valueOf(104.0843)),
                "成都",
                "hotel",
                1000,
                5
        );

        server.verify();
        assertEquals(2, candidates.size());
        assertEquals("B酒店", candidates.get(0).getName());
        assertEquals("C酒店", candidates.get(1).getName());
        assertTrue(candidates.get(0).getDistanceMeters() <= candidates.get(1).getDistanceMeters());
    }

    @Test
    void estimateTravel_shouldParseDurationDistanceAndModeFromRouteApi() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "paths": [
                              {
                                "durationSeconds": 1620,
                                "distanceMeters": 8400,
                                "transportMode": "地铁+步行"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "成都",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(27, estimate.durationMinutes());
        assertEquals(new BigDecimal("8.4"), estimate.distanceKm());
        assertEquals("地铁+步行", estimate.transportMode());
    }

    @Test
    void estimateTravel_shouldParseGeometryFromStepsWhenTopLevelPolylineMissing() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "paths": [
                              {
                                "durationSeconds": 1620,
                                "distanceMeters": 8400,
                                "transportMode": "??+??",
                                "steps": [
                                  {
                                    "polyline": "104.081703,30.65731;104.076,30.654"
                                  },
                                  {
                                    "polyline": "104.071,30.651;104.048,30.646"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "??",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(4, estimate.pathPoints().size());
        assertEquals(new BigDecimal("30.654"), estimate.pathPoints().get(1).latitude());
        assertEquals(new BigDecimal("104.071"), estimate.pathPoints().get(2).longitude());
    }

    @Test
    void estimateTravel_shouldParseStructuredProviderSteps() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "paths": [
                              {
                                "durationMinutes": 38,
                                "distanceKm": 12.6,
                                "mode": "transit",
                                "steps": [
                                  {
                                    "instruction": "Walk to station",
                                    "type": "walk",
                                    "distanceMeters": 320,
                                    "durationMinutes": 5,
                                    "entranceName": "Exit A",
                                    "polyline": "104.081703,30.65731;104.079,30.655"
                                  },
                                  {
                                    "instruction": "Take metro line 2",
                                    "type": "subway",
                                    "distanceMeters": 10600,
                                    "durationSeconds": 1380,
                                    "lineName": "Metro Line 2",
                                    "fromStation": "Tianfu Square",
                                    "toStation": "Chengdu East",
                                    "exitName": "Exit C",
                                    "stopCount": 8,
                                    "polyline": "104.079,30.655;104.06,30.649;104.048,30.646"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "Chengdu",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(2, estimate.steps().size());

        GeoRouteStep walk = estimate.steps().get(0);
        assertEquals("Walk to station", walk.instruction());
        assertEquals("walk", walk.type());
        assertEquals(320, walk.distanceMeters());
        assertEquals(5, walk.durationMinutes());
        assertEquals("Exit A", walk.entranceName());
        assertEquals(2, walk.pathPoints().size());

        GeoRouteStep metro = estimate.steps().get(1);
        assertEquals("Take metro line 2", metro.instruction());
        assertEquals("metro", metro.type());
        assertEquals(10600, metro.distanceMeters());
        assertEquals(23, metro.durationMinutes());
        assertEquals("Metro Line 2", metro.lineName());
        assertEquals("Tianfu Square", metro.fromStation());
        assertEquals("Chengdu East", metro.toStation());
        assertEquals("Exit C", metro.exitName());
        assertEquals(8, metro.stopCount());
        assertEquals(3, metro.pathPoints().size());
        assertNotNull(metro.pathPoints().get(1));
    }

    @Test
    void estimateTravel_shouldKeepTopLevelSummaryAndPolylineWhenProviderHasNoSteps() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "paths": [
                              {
                                "durationSeconds": 960,
                                "distanceMeters": 4200,
                                "transportMode": "bus",
                                "polyline": "104.081703,30.65731;104.072,30.653;104.048,30.646"
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "Chengdu",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(16, estimate.durationMinutes());
        assertEquals(new BigDecimal("4.2"), estimate.distanceKm());
        assertEquals("bus", estimate.transportMode());
        assertEquals(3, estimate.pathPoints().size());
        assertTrue(estimate.steps().isEmpty());
    }

    @Test
    void estimateTravel_shouldParseDirectRouteObjectWithStepsAndPolyline() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "durationSeconds": 1500,
                            "distanceMeters": 6900,
                            "transportMode": "transit",
                            "polyline": "104.081703,30.65731;104.072,30.653;104.048,30.646",
                            "legs": [
                              {
                                "steps": [
                                  {
                                    "instruction": "Walk to bus stop",
                                    "type": "walk",
                                    "distanceMeters": 300,
                                    "durationMinutes": 4,
                                    "polyline": "104.081703,30.65731;104.079,30.655"
                                  },
                                  {
                                    "instruction": "Take bus 21",
                                    "type": "bus",
                                    "distanceMeters": 6600,
                                    "durationSeconds": 1260,
                                    "lineName": "Bus 21",
                                    "fromStation": "Stop A",
                                    "toStation": "Stop B",
                                    "stopCount": 9,
                                    "polyline": "104.079,30.655;104.072,30.653;104.048,30.646"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "Chengdu",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(25, estimate.durationMinutes());
        assertEquals(new BigDecimal("6.9"), estimate.distanceKm());
        assertEquals("transit", estimate.transportMode());
        assertEquals(3, estimate.pathPoints().size());
        assertEquals(2, estimate.steps().size());
        assertEquals("walk", estimate.steps().get(0).type());
        assertEquals("bus", estimate.steps().get(1).type());
        assertEquals("Bus 21", estimate.steps().get(1).lineName());
    }

    @Test
    void estimateTravel_shouldNotMisparseAmbiguousDurationAndDistanceFields() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "duration": 120,
                            "distance": 79,
                            "mode": "transit",
                            "polyline": "104.081703,30.65731;104.048,30.646"
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "Chengdu",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isEmpty());
    }

    @Test
    void estimateTravel_shouldPreferTopLevelStepsOverLegStepsWhenBothExist() {
        GeoSearchProperties properties = createBaseProperties();
        properties.setBaseUrl("https://api.example.com");
        properties.setRoutePath("/geo/route");

        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);

        server.expect(once(), requestTo(allOf(
                startsWith("https://api.example.com/geo/route?"),
                containsString("origin=104.081703,30.65731"),
                containsString("destination=104.048,30.646")
        )))
                .andRespond(withSuccess("""
                        {
                          "route": {
                            "durationSeconds": 600,
                            "distanceMeters": 1200,
                            "steps": [
                              {
                                "instruction": "Top-level step",
                                "type": "walk",
                                "distanceMeters": 1200,
                                "durationSeconds": 600,
                                "polyline": "104.081703,30.65731;104.048,30.646"
                              }
                            ],
                            "legs": [
                              {
                                "steps": [
                                  {
                                    "instruction": "Leg duplicate",
                                    "type": "walk",
                                    "distanceMeters": 1200,
                                    "durationSeconds": 600,
                                    "polyline": "104.081703,30.65731;104.048,30.646"
                                  },
                                  {
                                    "instruction": "Leg extra",
                                    "type": "bus",
                                    "distanceMeters": 800,
                                    "durationSeconds": 300,
                                    "polyline": "104.048,30.646;104.045,30.644"
                                  }
                                ]
                              }
                            ]
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
        Optional<GeoRouteEstimate> estimateOptional = service.estimateTravel(
                new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
                "Chengdu",
                null
        );

        server.verify();
        assertTrue(estimateOptional.isPresent());
        GeoRouteEstimate estimate = estimateOptional.get();
        assertEquals(1, estimate.steps().size());
        assertEquals("Top-level step", estimate.steps().get(0).instruction());
        assertEquals(2, estimate.pathPoints().size());
    }

    private GeoSearchProperties createBaseProperties() {
        GeoSearchProperties properties = new GeoSearchProperties();
        properties.setEnabled(true);
        properties.setDefaultCityName("成都");
        properties.setDefaultLimit(8);
        properties.setNearbyRadiusMeters(1200);
        return properties;
    }
}

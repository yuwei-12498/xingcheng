package com.citytrip.service.impl;

import com.citytrip.config.AmapProperties;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.TravelTimeService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.ExpectedCount.once;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AmapTravelTimeServiceImplTest {

    @Test
    void estimatesDrivingLegFromAmapResponse() {
        AmapProperties properties = enabledProperties(List.of("driving"));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo(allOf(
                startsWith("https://restapi.amap.com/v3/direction/driving?"),
                containsString("origin=104.06,30.66"),
                containsString("destination=104.08,30.67"),
                containsString("key=test-key")
        ))).andRespond(withSuccess("""
                {
                  "status": "1",
                  "route": {
                    "paths": [
                      {
                        "duration": "900",
                        "distance": "6500",
                        "steps": [
                          {
                            "instruction": "沿人民南路行驶",
                            "duration": "900",
                            "distance": "6500",
                            "polyline": "104.060000,30.660000;104.070000,30.665000;104.080000,30.670000"
                          }
                        ]
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        AmapTravelTimeServiceImpl service = new AmapTravelTimeServiceImpl(
                properties,
                restTemplate,
                new ObjectMapper(),
                new StubLocalTravelTimeService(99)
        );

        TravelTimeService.TravelLegEstimate estimate = service.estimateTravelLeg(poi(1L, 30.66D, 104.06D), poi(2L, 30.67D, 104.08D));

        server.verify();
        assertThat(estimate.estimatedMinutes()).isEqualTo(15);
        assertThat(estimate.estimatedDistanceKm()).isEqualByComparingTo("6.5");
        assertThat(estimate.transportMode()).isEqualTo("\u6253\u8f66");
        assertThat(estimate.pathPoints()).hasSize(3);
        assertThat(estimate.detailedRoute()).isNotNull();
        assertThat(estimate.detailedRoute().steps()).hasSize(1);
        assertThat(estimate.detailedRoute().steps().get(0).type()).isEqualTo("taxi");
    }

    @Test
    void estimatesTransitLegFromAmapResponse() {
        AmapProperties properties = enabledProperties(List.of("transit"));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo(allOf(
                startsWith("https://restapi.amap.com/v3/direction/transit/integrated?"),
                containsString("city=Chengdu"),
                containsString("cityd=Chengdu")
        ))).andRespond(withSuccess("""
                {
                  "status": "1",
                  "route": {
                    "transits": [
                      {
                        "duration": "1800",
                        "distance": "8200",
                        "segments": [
                          {
                            "walking": {
                              "distance": "300",
                              "steps": [{
                                "instruction": "步行至地铁站",
                                "duration": "300",
                                "distance": "300",
                                "polyline": "104.060000,30.660000;104.061000,30.661000"
                              }]
                            },
                            "bus": {
                              "buslines": [
                                {
                                  "name": "地铁1号线",
                                  "duration": "1200",
                                  "distance": "5000",
                                  "via_num": "3",
                                  "departure_stop": {"name": "起点站"},
                                  "arrival_stop": {"name": "终点站"},
                                  "polyline": "104.061000,30.661000;104.080000,30.670000"
                                }
                              ]
                            }
                          }
                        ]
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        AmapTravelTimeServiceImpl service = new AmapTravelTimeServiceImpl(
                properties,
                restTemplate,
                new ObjectMapper(),
                new StubLocalTravelTimeService(99)
        );

        TravelTimeService.TravelLegEstimate estimate = service.estimateTravelLeg(poi(1L, 30.66D, 104.06D), poi(2L, 30.67D, 104.08D));

        server.verify();
        assertThat(estimate.estimatedMinutes()).isEqualTo(30);
        assertThat(estimate.estimatedDistanceKm()).isEqualByComparingTo("8.2");
        assertThat(estimate.transportMode()).isEqualTo("\u516c\u4ea4+\u6b65\u884c");
        assertThat(estimate.pathPoints()).hasSize(3);
        assertThat(estimate.detailedRoute().steps()).hasSize(2);
        assertThat(estimate.detailedRoute().steps().get(1).lineName()).isEqualTo("地铁1号线");
    }

    @Test
    void signsTransitRequestAfterModeSpecificParametersAreAdded() {
        AmapProperties properties = enabledProperties(List.of("transit"));
        properties.setSignRequests(true);
        properties.setSecurityKey("test-secret");
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo(allOf(
                startsWith("https://restapi.amap.com/v3/direction/transit/integrated?"),
                containsString("city=Chengdu"),
                containsString("cityd=Chengdu"),
                containsString("sig=cdfa6593d9c734969de7f772897aefc7")
        ))).andRespond(withSuccess("""
                {
                  "status": "1",
                  "route": {
                    "transits": [
                      {
                        "duration": "600",
                        "distance": "1200",
                        "segments": []
                      }
                    ]
                  }
                }
                """, MediaType.APPLICATION_JSON));

        AmapTravelTimeServiceImpl service = new AmapTravelTimeServiceImpl(
                properties,
                restTemplate,
                new ObjectMapper(),
                new StubLocalTravelTimeService(99)
        );

        TravelTimeService.TravelLegEstimate estimate = service.estimateTravelLeg(poi(1L, 30.66D, 104.06D), poi(2L, 30.67D, 104.08D));

        server.verify();
        assertThat(estimate.estimatedMinutes()).isEqualTo(10);
    }

    @Test
    void fallsBackToLocalEstimatorWhenAmapIsRateLimited() {
        AmapProperties properties = enabledProperties(List.of("driving"));
        RestTemplate restTemplate = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.createServer(restTemplate);
        server.expect(once(), requestTo(startsWith("https://restapi.amap.com/v3/direction/driving?")))
                .andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));
        AmapTravelTimeServiceImpl service = new AmapTravelTimeServiceImpl(
                properties,
                restTemplate,
                new ObjectMapper(),
                new StubLocalTravelTimeService(42)
        );

        TravelTimeService.TravelLegEstimate estimate = service.estimateTravelLeg(poi(1L, 30.66D, 104.06D), poi(2L, 30.67D, 104.08D));

        server.verify();
        assertThat(estimate.estimatedMinutes()).isEqualTo(42);
        assertThat(estimate.transportMode()).isEqualTo("fallback");
    }

    private AmapProperties enabledProperties(List<String> modes) {
        AmapProperties properties = new AmapProperties();
        properties.setEnabled(true);
        properties.setApiKey("test-key");
        properties.setBaseUrl("https://restapi.amap.com");
        properties.setTravelModes(modes);
        return properties;
    }

    private Poi poi(long id, double lat, double lng) {
        Poi poi = new Poi();
        poi.setId(id);
        poi.setName("poi-" + id);
        poi.setCityName("Chengdu");
        poi.setLatitude(BigDecimal.valueOf(lat));
        poi.setLongitude(BigDecimal.valueOf(lng));
        return poi;
    }

    private static final class StubLocalTravelTimeService extends LocalTravelTimeServiceImpl {
        private final int minutes;

        private StubLocalTravelTimeService(int minutes) {
            this.minutes = minutes;
        }

        @Override
        public TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
            return new TravelLegEstimate(minutes, BigDecimal.ONE, "fallback");
        }
    }
}

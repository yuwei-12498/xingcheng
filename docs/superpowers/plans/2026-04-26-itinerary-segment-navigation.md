# Itinerary Segment Navigation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add real per-segment navigation guides to the itinerary result page so every stop card can show a factual “how to get here” summary plus expandable step-by-step details and a mini map, all computed during itinerary generation.

**Architecture:** Extend the GEO route contract so provider step facts survive the backend boundary, thread the detailed route through `TravelLegEstimate`, and assemble a new `segmentRouteGuide` onto each `ItineraryNodeVO`. Normalize that new guide object on the frontend, render it with dedicated guide-card and mini-map components, and synchronize guide expansion with the existing left-side segment highlight model.

**Tech Stack:** Spring Boot 3.4, Java 17, Jackson, Vue 3, Vite, Vitest, Leaflet

---

## File Structure

### Backend route-contract and guide assembly

- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteStep.java`  
  Provider-facing structured route-step contract. Carries only factual fields parsed from the route provider.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteEstimate.java`  
  Extend the existing route estimate to carry `steps` alongside top-level duration, distance, mode, and geometry.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`  
  Parse structured route steps, step-level station facts, and step path geometry from the provider response.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\TravelTimeService.java`  
  Extend `TravelLegEstimate` with `detailedRoute` so later services can reuse the same route-provider result.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\GeoEnhancedTravelTimeServiceImpl.java`  
  Populate `TravelLegEstimate.detailedRoute` from the already-fetched GEO provider route.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteGuideVO.java`  
  Node-level navigation guide payload returned to the frontend.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteStepVO.java`  
  Step-level navigation payload used by the result page detail panel.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ItineraryNodeVO.java`  
  Add `segmentRouteGuide`.
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\SegmentRouteGuideService.java`  
  Convert factual travel-leg data into `SegmentRouteGuideVO` without inventing route facts.
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java`  
  Attach `segmentRouteGuide` to each node and preserve day-start semantics.

### Backend regression and API-surface hardening

- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java`  
  Deep-copy itinerary data without dropping nested `segmentRouteGuide` fields.
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\ai\ItineraryAiDecorationServiceTest.java`  
  Verify AI decoration does not overwrite or strip the real guide object.
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\controller\AuthAndItineraryControllerTest.java`  
  Verify itinerary endpoints serialize `segmentRouteGuide`.

### Frontend normalization and UI

- Modify: `F:\dachuang\frontend\src\store\itinerary.js`  
  Preserve `segmentRouteGuide` exactly through snapshot normalization and reload.
- Modify: `F:\dachuang\frontend\src\utils\resultUi.js`  
  Add helper functions for guide titles and summary fallback formatting.
- Create: `F:\dachuang\frontend\src\components\itinerary\SegmentRouteGuideCard.vue`  
  Stop-card subpanel for summary, expand/collapse, incomplete-state notice, and left-text/right-map layout.
- Create: `F:\dachuang\frontend\src\components\itinerary\SegmentMiniMap.vue`  
  Mini Leaflet map that renders only the active segment and its step overlays.
- Modify: `F:\dachuang\frontend\src\views\Result.vue`  
  Embed `SegmentRouteGuideCard` in each stop card, keep a single expanded card, and sync it with the existing map segment selection model.

### Tests

- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\impl\GeoSearchServiceImplTest.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\SegmentRouteGuideServiceTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\RouteAnalysisServiceTest.java`
- Modify: `F:\dachuang\frontend\src\store\__tests__\itinerary.test.js`
- Modify: `F:\dachuang\frontend\src\utils\__tests__\resultUi.test.js`
- Create: `F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentRouteGuideCard.test.js`
- Create: `F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentMiniMap.test.js`
- Modify: `F:\dachuang\frontend\src\views\__tests__\Result.test.js`

---

### Task 1: Extend the GEO route contract with factual provider steps

**Files:**
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteStep.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteEstimate.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java`
- Test: `F:\dachuang\backend\src\test\java\com\citytrip\service\geo\impl\GeoSearchServiceImplTest.java`

- [ ] **Step 1: Write the failing GEO parsing tests for structured route steps**

```java
@Test
void estimateTravel_shouldParseStructuredRouteSteps() {
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
                            "transportMode": "地铁+步行",
                            "steps": [
                              {
                                "instruction": "步行 300 米到天府广场地铁站 B 口",
                                "type": "walk",
                                "distanceMeters": 300,
                                "durationSeconds": 240,
                                "toStation": "天府广场",
                                "entranceName": "B口",
                                "polyline": "104.081703,30.65731;104.079,30.655"
                              },
                              {
                                "instruction": "乘 1 号线往文殊院方向 2 站",
                                "type": "metro",
                                "distanceMeters": 6200,
                                "durationSeconds": 900,
                                "lineName": "1号线",
                                "fromStation": "天府广场",
                                "toStation": "文殊院",
                                "stopCount": 2,
                                "polyline": "104.079,30.655;104.061,30.657"
                              },
                              {
                                "instruction": "从 C 口出站后步行 450 米到景点入口",
                                "type": "walk",
                                "distanceMeters": 450,
                                "durationSeconds": 360,
                                "exitName": "C口",
                                "polyline": "104.061,30.657;104.048,30.646"
                              }
                            ]
                          }
                        ]
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

    GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
    GeoRouteEstimate estimate = service.estimateTravel(
            new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
            new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
            "成都",
            null
    ).orElseThrow();

    server.verify();
    assertEquals(3, estimate.steps().size());
    assertEquals("metro", estimate.steps().get(1).type());
    assertEquals("1号线", estimate.steps().get(1).lineName());
    assertEquals(2, estimate.steps().get(1).stopCount());
    assertEquals("C口", estimate.steps().get(2).exitName());
}

@Test
void estimateTravel_shouldKeepSummaryFactsWhenProviderOnlyReturnsTopLevelPath() {
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
                            "transportMode": "打车",
                            "polyline": "104.081703,30.65731;104.064,30.651;104.048,30.646"
                          }
                        ]
                      }
                    }
                    """, MediaType.APPLICATION_JSON));

    GeoSearchServiceImpl service = new GeoSearchServiceImpl(properties, restTemplate, new ObjectMapper());
    GeoRouteEstimate estimate = service.estimateTravel(
            new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
            new GeoPoint(BigDecimal.valueOf(30.646), BigDecimal.valueOf(104.048)),
            "成都",
            null
    ).orElseThrow();

    server.verify();
    assertTrue(estimate.steps().isEmpty());
    assertEquals("打车", estimate.transportMode());
    assertEquals(3, estimate.pathPoints().size());
}
```

- [ ] **Step 2: Run the GEO test file and confirm it fails on missing `steps` support**

Run: `mvn -Dtest=GeoSearchServiceImplTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL with compilation errors for missing `GeoRouteStep` or missing `GeoRouteEstimate.steps()`.

- [ ] **Step 3: Add the new `GeoRouteStep` contract and extend `GeoRouteEstimate`**

```java
package com.citytrip.service.geo;

import java.util.List;

public record GeoRouteStep(Integer stepOrder,
                           String type,
                           String instruction,
                           Integer distanceMeters,
                           Integer durationMinutes,
                           String lineName,
                           String fromStation,
                           String toStation,
                           String entranceName,
                           String exitName,
                           Integer stopCount,
                           List<GeoPoint> pathPoints) {

    public GeoRouteStep {
        pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
    }
}
```

```java
package com.citytrip.service.geo;

import java.math.BigDecimal;
import java.util.List;

public record GeoRouteEstimate(Integer durationMinutes,
                               BigDecimal distanceKm,
                               String transportMode,
                               List<GeoPoint> pathPoints,
                               List<GeoRouteStep> steps) {

    public GeoRouteEstimate(Integer durationMinutes,
                            BigDecimal distanceKm,
                            String transportMode) {
        this(durationMinutes, distanceKm, transportMode, List.of(), List.of());
    }

    public GeoRouteEstimate(Integer durationMinutes,
                            BigDecimal distanceKm,
                            String transportMode,
                            List<GeoPoint> pathPoints) {
        this(durationMinutes, distanceKm, transportMode, pathPoints, List.of());
    }

    public GeoRouteEstimate {
        pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
        steps = steps == null ? List.of() : List.copyOf(steps);
    }
}
```

- [ ] **Step 4: Parse provider step facts and path geometry inside `GeoSearchServiceImpl`**

```java
private Optional<GeoRouteEstimate> parseRouteEstimate(JsonNode root) {
    JsonNode routeNode = resolvePrimaryRouteNode(root);
    Integer minutes = extractDurationMinutes(routeNode);
    BigDecimal distanceKm = extractDistanceKm(routeNode);
    List<GeoRouteStep> steps = extractRouteSteps(routeNode);
    List<GeoPoint> pathPoints = extractPathPoints(routeNode);
    if (pathPoints.isEmpty() && !steps.isEmpty()) {
        pathPoints = mergeRouteStepPathPoints(steps);
    }

    if (minutes == null && distanceKm == null && pathPoints.isEmpty() && steps.isEmpty()) {
        return Optional.empty();
    }

    if (minutes == null) {
        minutes = inferMinutesFromDistance(distanceKm);
    }
    if (distanceKm == null) {
        distanceKm = inferDistanceFromMinutes(minutes);
    }

    String mode = extractTransportMode(routeNode);
    if (!StringUtils.hasText(mode)) {
        mode = inferTransportMode(distanceKm, minutes);
    }

    return Optional.of(new GeoRouteEstimate(minutes, distanceKm, mode, pathPoints, steps));
}

private List<GeoRouteStep> extractRouteSteps(JsonNode routeNode) {
    List<JsonNode> nodes = extractStepNodes(routeNode);
    if (nodes.isEmpty()) {
        return List.of();
    }
    List<GeoRouteStep> steps = new ArrayList<>();
    for (int index = 0; index < nodes.size(); index++) {
        GeoRouteStep step = parseRouteStep(nodes.get(index), index + 1);
        if (step != null) {
            steps.add(step);
        }
    }
    return List.copyOf(steps);
}

private List<JsonNode> extractStepNodes(JsonNode routeNode) {
    for (String field : List.of("steps", "segments")) {
        JsonNode direct = routeNode.path(field);
        if (direct.isArray() && direct.size() > 0) {
            return asList(direct);
        }
    }
    JsonNode legs = routeNode.path("legs");
    if (!legs.isArray() || legs.isEmpty()) {
        return List.of();
    }
    List<JsonNode> merged = new ArrayList<>();
    for (JsonNode leg : legs) {
        JsonNode legSteps = leg.path("steps");
        if (legSteps.isArray() && legSteps.size() > 0) {
            merged.addAll(asList(legSteps));
        }
    }
    return merged;
}

private GeoRouteStep parseRouteStep(JsonNode node, int stepOrder) {
    String instruction = firstText(node, "instruction", "instructionText", "narrative", "name");
    Integer distanceMeters = firstInteger(node, "distanceMeters", "distanceMeter", "distanceM", "distance");
    Integer durationMinutes = extractDurationMinutes(node);
    return new GeoRouteStep(
            stepOrder,
            normalizeStepType(firstText(node, "type", "stepType", "vehicle", "transportMode"), instruction),
            instruction,
            distanceMeters,
            durationMinutes,
            firstText(node, "lineName", "line", "routeName"),
            firstText(node, "fromStation", "departureStop", "originStation"),
            firstText(node, "toStation", "arrivalStop", "destinationStation"),
            firstText(node, "entranceName", "entrance", "in"),
            firstText(node, "exitName", "exit", "out"),
            firstInteger(node, "stopCount", "stationCount", "passStopNum"),
            dedupeConsecutivePathPoints(parseGeometryNode(node))
    );
}

private List<GeoPoint> mergeRouteStepPathPoints(List<GeoRouteStep> steps) {
    List<GeoPoint> merged = new ArrayList<>();
    for (GeoRouteStep step : steps) {
        if (step == null || step.pathPoints().isEmpty()) {
            continue;
        }
        merged.addAll(step.pathPoints());
    }
    return dedupeConsecutivePathPoints(merged);
}

private String normalizeStepType(String rawType, String instruction) {
    String source = StringUtils.hasText(rawType) ? rawType.trim().toLowerCase(Locale.ROOT) : "";
    String hint = StringUtils.hasText(instruction) ? instruction.toLowerCase(Locale.ROOT) : "";
    if (source.contains("metro") || source.contains("subway") || hint.contains("地铁")) {
        return "metro";
    }
    if (source.contains("bus") || hint.contains("公交")) {
        return "bus";
    }
    if (source.contains("taxi") || source.contains("car") || hint.contains("打车")) {
        return "taxi";
    }
    if (source.contains("enter") || hint.contains("进站") || hint.contains("入口")) {
        return "enter";
    }
    if (source.contains("exit") || hint.contains("出站") || hint.contains("出口")) {
        return "exit";
    }
    if (source.contains("transfer") || hint.contains("换乘")) {
        return "transfer";
    }
    return "walk";
}
```

- [ ] **Step 5: Re-run the GEO parser tests**

Run: `mvn -Dtest=GeoSearchServiceImplTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS and both new tests confirm step parsing plus top-level-path fallback.

- [ ] **Step 6: Commit the contract extension in one focused change**

```bash
git add ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteStep.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\geo\GeoRouteEstimate.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\geo\impl\GeoSearchServiceImpl.java ^
  F:\dachuang\backend\src\test\java\com\citytrip\service\geo\impl\GeoSearchServiceImplTest.java
git commit -m "feat: parse structured geo route steps"
```

---
### Task 2: Assemble `segmentRouteGuide` on each itinerary node

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\TravelTimeService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\impl\GeoEnhancedTravelTimeServiceImpl.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteGuideVO.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteStepVO.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ItineraryNodeVO.java`
- Create: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\SegmentRouteGuideService.java`
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java`
- Create: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\SegmentRouteGuideServiceTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\RouteAnalysisServiceTest.java`

- [ ] **Step 1: Write the failing guide-assembly tests**

```java
@Test
void buildGuideUsesRealProviderStepsWithoutInventingFacts() {
    SegmentRouteGuideService service = new SegmentRouteGuideService();
    TravelTimeService.TravelLegEstimate leg = new TravelTimeService.TravelLegEstimate(
            27,
            BigDecimal.valueOf(8.4D),
            "地铁+步行",
            List.of(
                    new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                    new GeoPoint(BigDecimal.valueOf(30.64600), BigDecimal.valueOf(104.048000))
            ),
            new GeoRouteEstimate(
                    27,
                    BigDecimal.valueOf(8.4D),
                    "地铁+步行",
                    List.of(
                            new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                            new GeoPoint(BigDecimal.valueOf(30.64600), BigDecimal.valueOf(104.048000))
                    ),
                    List.of(
                            new GeoRouteStep(1, "walk", "步行 300 米到天府广场地铁站 B 口", 300, 4, null, null, "天府广场", "B口", null, null, List.of()),
                            new GeoRouteStep(2, "metro", "乘 1 号线往文殊院方向 2 站", 6200, 15, "1号线", "天府广场", "文殊院", null, null, 2, List.of()),
                            new GeoRouteStep(3, "walk", "从 C 口出站后步行 450 米到景点入口", 450, 6, null, null, null, null, "C口", null, List.of())
                    )
            )
    );

    SegmentRouteGuideVO guide = service.buildGuide(leg);

    assertThat(guide.getDetailAvailable()).isTrue();
    assertThat(guide.getSummary()).isEqualTo("步行 300 米 → 地铁 2 站 → 步行 450 米");
    assertThat(guide.getSteps()).hasSize(3);
    assertThat(guide.getSteps().get(1).getLineName()).isEqualTo("1号线");
    assertThat(guide.getSteps().get(1).getStopCount()).isEqualTo(2);
    assertThat(guide.getSteps().get(2).getExitName()).isEqualTo("C口");
}

@Test
void buildGuideMarksIncompleteWhenStructuredStepsAreMissing() {
    SegmentRouteGuideService service = new SegmentRouteGuideService();
    TravelTimeService.TravelLegEstimate leg = new TravelTimeService.TravelLegEstimate(
            14,
            BigDecimal.valueOf(5.1D),
            "打车",
            List.of(
                    new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                    new GeoPoint(BigDecimal.valueOf(30.64600), BigDecimal.valueOf(104.048000))
            ),
            new GeoRouteEstimate(
                    14,
                    BigDecimal.valueOf(5.1D),
                    "打车",
                    List.of(
                            new GeoPoint(BigDecimal.valueOf(30.65731), BigDecimal.valueOf(104.081703)),
                            new GeoPoint(BigDecimal.valueOf(30.64600), BigDecimal.valueOf(104.048000))
                    ),
                    List.of()
            )
    );

    SegmentRouteGuideVO guide = service.buildGuide(leg);

    assertThat(guide.getDetailAvailable()).isFalse();
    assertThat(guide.getSummary()).isEqualTo("打车约 14 分钟，约 5.1 公里");
    assertThat(guide.getIncompleteReason()).isEqualTo("该段暂未获取完整导航详情");
    assertThat(guide.getPathPoints()).hasSize(2);
}
```

```java
@Test
void analyzeRouteAttachesSegmentRouteGuideToEachNode() {
    TravelTimeService travelTimeService = mock(TravelTimeService.class);
    ItineraryRouteOptimizer routeOptimizer = mock(ItineraryRouteOptimizer.class);
    SegmentRouteGuideService segmentRouteGuideService = new SegmentRouteGuideService();
    RouteAnalysisService service = new RouteAnalysisService(travelTimeService, routeOptimizer, segmentRouteGuideService);

    GenerateReqDTO req = new GenerateReqDTO();
    req.setStartTime("09:00");

    Poi departurePoi = new Poi();
    departurePoi.setId(-1L);
    departurePoi.setLatitude(BigDecimal.valueOf(30.650D));
    departurePoi.setLongitude(BigDecimal.valueOf(104.060D));

    Poi p1 = createPoi(1L, "宽窄巷子", "scenic");
    Poi p2 = createPoi(2L, "博物馆", "museum");
    ItineraryRouteOptimizer.RouteOption route = new ItineraryRouteOptimizer.RouteOption(List.of(p1, p2), "1-2", 100.0D);

    when(routeOptimizer.normalizeRequest(req)).thenReturn(req);
    when(routeOptimizer.parseTimeMinutes("09:00", ItineraryRouteOptimizer.DEFAULT_START_MINUTE)).thenReturn(540);
    when(routeOptimizer.resolveOpenMinute(any(Poi.class), anyInt())).thenAnswer(invocation -> invocation.getArgument(1));
    when(routeOptimizer.formatTime(anyInt())).thenAnswer(invocation -> String.format("%02d:%02d", invocation.getArgument(0) / 60, invocation.getArgument(0) % 60));
    when(routeOptimizer.buildDeparturePoi(req)).thenReturn(departurePoi);
    when(travelTimeService.estimateTravelLeg(eq(departurePoi), eq(p1))).thenReturn(new TravelTimeService.TravelLegEstimate(
            18,
            BigDecimal.valueOf(4.2D),
            "地铁+步行",
            List.of(
                    new GeoPoint(BigDecimal.valueOf(30.650D), BigDecimal.valueOf(104.060D)),
                    new GeoPoint(BigDecimal.valueOf(30.652D), BigDecimal.valueOf(104.062D))
            ),
            new GeoRouteEstimate(
                    18,
                    BigDecimal.valueOf(4.2D),
                    "地铁+步行",
                    List.of(
                            new GeoPoint(BigDecimal.valueOf(30.650D), BigDecimal.valueOf(104.060D)),
                            new GeoPoint(BigDecimal.valueOf(30.652D), BigDecimal.valueOf(104.062D))
                    ),
                    List.of(
                            new GeoRouteStep(1, "walk", "步行 220 米到地铁站", 220, 3, null, null, "地铁站", "B口", null, null, List.of()),
                            new GeoRouteStep(2, "metro", "乘 1 号线 2 站", 3600, 9, "1号线", "天府广场", "宽窄巷子", null, null, 2, List.of())
                    )
            )
    ));
    when(travelTimeService.estimateTravelLeg(eq(p1), eq(p2))).thenReturn(new TravelTimeService.TravelLegEstimate(
            12,
            BigDecimal.valueOf(2.1D),
            "公交+步行",
            List.of(
                    new GeoPoint(BigDecimal.valueOf(30.652D), BigDecimal.valueOf(104.062D)),
                    new GeoPoint(BigDecimal.valueOf(30.660D), BigDecimal.valueOf(104.072D))
            ),
            new GeoRouteEstimate(
                    12,
                    BigDecimal.valueOf(2.1D),
                    "公交+步行",
                    List.of(
                            new GeoPoint(BigDecimal.valueOf(30.652D), BigDecimal.valueOf(104.062D)),
                            new GeoPoint(BigDecimal.valueOf(30.660D), BigDecimal.valueOf(104.072D))
                    ),
                    List.of(
                            new GeoRouteStep(1, "bus", "公交 4 站", 1800, 8, "107路", "宽窄巷子", "博物馆", null, null, 4, List.of()),
                            new GeoRouteStep(2, "walk", "步行 260 米到入口", 260, 4, null, null, null, null, null, null, List.of())
                    )
            )
    ));

    RouteAnalysisService.RouteAnalysis analysis = service.analyzeRoute(route, req, Map.of());

    assertThat(analysis.nodes()).hasSize(2);
    assertThat(analysis.nodes().get(0).getSegmentRouteGuide()).isNotNull();
    assertThat(analysis.nodes().get(0).getSegmentRouteGuide().getSummary()).contains("地铁 2 站");
    assertThat(analysis.nodes().get(1).getSegmentRouteGuide().getSummary()).contains("公交 4 站");
}
```

- [ ] **Step 2: Run the planning-domain tests and confirm the missing guide types break the build**

Run: `mvn -Dtest=SegmentRouteGuideServiceTest,RouteAnalysisServiceTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL with missing `SegmentRouteGuideVO`, `SegmentRouteStepVO`, `SegmentRouteGuideService`, or constructor mismatch in `RouteAnalysisService`.

- [ ] **Step 3: Add the new VO classes and mount point on `ItineraryNodeVO`**

```java
package com.citytrip.model.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

@Data
public class SegmentRouteGuideVO {
    private String summary;
    private String transportMode;
    private Integer durationMinutes;
    private BigDecimal distanceKm;
    private Boolean detailAvailable;
    private String incompleteReason;
    private List<SegmentRouteStepVO> steps = List.of();
    private List<RoutePathPointVO> pathPoints = List.of();
    private String source;
}
```

```java
package com.citytrip.model.vo;

import lombok.Data;

import java.util.List;

@Data
public class SegmentRouteStepVO {
    private Integer stepOrder;
    private String type;
    private String instruction;
    private Integer distanceMeters;
    private Integer durationMinutes;
    private String lineName;
    private String fromStation;
    private String toStation;
    private String entranceName;
    private String exitName;
    private Integer stopCount;
    private List<RoutePathPointVO> pathPoints = List.of();
}
```

```java
/**
 * 上一段 -> 当前节点 的真实结构化导航说明。
 * stepOrder = 1 时表示 出发位置 -> 当日首站。
 */
private SegmentRouteGuideVO segmentRouteGuide;
```

- [ ] **Step 4: Thread `detailedRoute` through `TravelLegEstimate` and GEO-enhanced travel time**

```java
default TravelLegEstimate estimateTravelLeg(Poi from, Poi to) {
    return new TravelLegEstimate(estimateTravelTimeMinutes(from, to), null, null, List.of(), null);
}

record TravelLegEstimate(int estimatedMinutes,
                         BigDecimal estimatedDistanceKm,
                         String transportMode,
                         List<GeoPoint> pathPoints,
                         GeoRouteEstimate detailedRoute) {

    public TravelLegEstimate(int estimatedMinutes,
                             BigDecimal estimatedDistanceKm,
                             String transportMode) {
        this(estimatedMinutes, estimatedDistanceKm, transportMode, List.of(), null);
    }

    public TravelLegEstimate(int estimatedMinutes,
                             BigDecimal estimatedDistanceKm,
                             String transportMode,
                             List<GeoPoint> pathPoints) {
        this(estimatedMinutes, estimatedDistanceKm, transportMode, pathPoints, null);
    }

    public TravelLegEstimate {
        pathPoints = pathPoints == null ? List.of() : List.copyOf(pathPoints);
    }
}
```

```java
return new TravelLegEstimate(
        precise.durationMinutes(),
        distanceKm,
        StringUtils.hasText(precise.transportMode()) ? precise.transportMode().trim() : null,
        precise.pathPoints(),
        precise
);
```

- [ ] **Step 5: Implement `SegmentRouteGuideService` as the single factual guide assembler**

```java
package com.citytrip.service.domain.planning;

import com.citytrip.model.vo.RoutePathPointVO;
import com.citytrip.model.vo.SegmentRouteGuideVO;
import com.citytrip.model.vo.SegmentRouteStepVO;
import com.citytrip.service.TravelTimeService;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoRouteStep;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

@Service
public class SegmentRouteGuideService {

    public SegmentRouteGuideVO buildGuide(TravelTimeService.TravelLegEstimate legEstimate) {
        SegmentRouteGuideVO guide = new SegmentRouteGuideVO();
        if (legEstimate == null) {
            guide.setDetailAvailable(false);
            guide.setSummary("该段导航数据待补充");
            guide.setIncompleteReason("该段导航数据获取失败，请以地图实时导航为准");
            guide.setSteps(List.of());
            guide.setPathPoints(List.of());
            guide.setSource("route-provider");
            return guide;
        }

        guide.setTransportMode(trimmed(legEstimate.transportMode()));
        guide.setDurationMinutes(Math.max(0, legEstimate.estimatedMinutes()));
        guide.setDistanceKm(scaleDistance(legEstimate.estimatedDistanceKm()));
        guide.setPathPoints(toRoutePathPoints(legEstimate.pathPoints()));
        guide.setSource("route-provider");

        List<SegmentRouteStepVO> steps = legEstimate.detailedRoute() == null
                ? List.of()
                : legEstimate.detailedRoute().steps().stream().map(this::toStepVo).toList();
        guide.setSteps(steps);

        if (!steps.isEmpty()) {
            guide.setDetailAvailable(true);
            guide.setSummary(buildSummaryFromSteps(steps, guide.getTransportMode(), guide.getDurationMinutes(), guide.getDistanceKm()));
            return guide;
        }

        guide.setDetailAvailable(false);
        guide.setSummary(buildFallbackSummary(guide.getTransportMode(), guide.getDurationMinutes(), guide.getDistanceKm()));
        guide.setIncompleteReason(guide.getPathPoints().isEmpty()
                ? "该段导航数据获取失败，请以地图实时导航为准"
                : "该段暂未获取完整导航详情");
        return guide;
    }

    private SegmentRouteStepVO toStepVo(GeoRouteStep step) {
        SegmentRouteStepVO vo = new SegmentRouteStepVO();
        vo.setStepOrder(step.stepOrder());
        vo.setType(step.type());
        vo.setInstruction(trimmed(step.instruction()));
        vo.setDistanceMeters(step.distanceMeters());
        vo.setDurationMinutes(step.durationMinutes());
        vo.setLineName(trimmed(step.lineName()));
        vo.setFromStation(trimmed(step.fromStation()));
        vo.setToStation(trimmed(step.toStation()));
        vo.setEntranceName(trimmed(step.entranceName()));
        vo.setExitName(trimmed(step.exitName()));
        vo.setStopCount(step.stopCount());
        vo.setPathPoints(toRoutePathPoints(step.pathPoints()));
        return vo;
    }

    private String buildSummaryFromSteps(List<SegmentRouteStepVO> steps,
                                         String transportMode,
                                         Integer durationMinutes,
                                         BigDecimal distanceKm) {
        List<String> parts = steps.stream()
                .map(this::compactStep)
                .filter(StringUtils::hasText)
                .distinct()
                .limit(4)
                .toList();
        return parts.isEmpty() ? buildFallbackSummary(transportMode, durationMinutes, distanceKm) : String.join(" → ", parts);
    }

    private String compactStep(SegmentRouteStepVO step) {
        if (step == null) {
            return null;
        }
        String type = trimmed(step.getType());
        if ("walk".equals(type) && step.getDistanceMeters() != null) {
            return "步行 " + step.getDistanceMeters() + " 米";
        }
        if ("metro".equals(type) && step.getStopCount() != null) {
            return "地铁 " + step.getStopCount() + " 站";
        }
        if ("bus".equals(type) && step.getStopCount() != null) {
            return "公交 " + step.getStopCount() + " 站";
        }
        if ("taxi".equals(type) && step.getDurationMinutes() != null) {
            return "打车约 " + step.getDurationMinutes() + " 分钟";
        }
        if (StringUtils.hasText(step.getInstruction())) {
            return step.getInstruction().trim();
        }
        return null;
    }

    private String buildFallbackSummary(String transportMode, Integer durationMinutes, BigDecimal distanceKm) {
        String mode = StringUtils.hasText(transportMode) ? transportMode.trim() : "出行";
        if (durationMinutes != null && durationMinutes > 0 && distanceKm != null && distanceKm.compareTo(BigDecimal.ZERO) > 0) {
            return mode + "约 " + durationMinutes + " 分钟，约 " + distanceKm.setScale(1, RoundingMode.HALF_UP).toPlainString() + " 公里";
        }
        if (durationMinutes != null && durationMinutes > 0) {
            return mode + "约 " + durationMinutes + " 分钟";
        }
        return mode;
    }

    private List<RoutePathPointVO> toRoutePathPoints(List<GeoPoint> points) {
        if (points == null || points.isEmpty()) {
            return List.of();
        }
        return points.stream()
                .filter(point -> point != null && point.valid())
                .map(point -> {
                    RoutePathPointVO vo = new RoutePathPointVO();
                    vo.setLatitude(point.latitude());
                    vo.setLongitude(point.longitude());
                    return vo;
                })
                .toList();
    }

    private BigDecimal scaleDistance(BigDecimal distanceKm) {
        return distanceKm == null ? null : distanceKm.setScale(1, RoundingMode.HALF_UP);
    }

    private String trimmed(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
```

- [ ] **Step 6: Attach the guide in `RouteAnalysisService` and keep multi-day first-stop semantics**

```java
private final SegmentRouteGuideService segmentRouteGuideService;

public RouteAnalysisService(TravelTimeService travelTimeService,
                            ItineraryRouteOptimizer routeOptimizer,
                            SegmentRouteGuideService segmentRouteGuideService) {
    this.travelTimeService = travelTimeService;
    this.routeOptimizer = routeOptimizer;
    this.segmentRouteGuideService = segmentRouteGuideService;
}
```

```java
node.setRoutePathPoints(toRoutePathPoints(legEstimate == null ? null : legEstimate.pathPoints()));
node.setSegmentRouteGuide(segmentRouteGuideService.buildGuide(legEstimate));
```

```java
if (index > 0 && dayStartIndexes.contains(index)) {
    dayNo++;
    dayStepOrder = 0;
    prev = null;
    currentMinute = safeAddInt(safeDayOffset(dayNo - 1), startMinute);
}
```

The day-boundary logic already resets `prev = null`; keep that behavior so each day’s first node uses `departurePoi -> 当日首站`, not `前一日末站 -> 次日首站`.

- [ ] **Step 7: Re-run the guide-assembly tests**

Run: `mvn -Dtest=SegmentRouteGuideServiceTest,RouteAnalysisServiceTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS and the route-analysis tests show every node now carries a factual `segmentRouteGuide`.

- [ ] **Step 8: Commit the backend guide assembly slice**

```bash
git add ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\TravelTimeService.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\impl\GeoEnhancedTravelTimeServiceImpl.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteGuideVO.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\model\vo\SegmentRouteStepVO.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\model\vo\ItineraryNodeVO.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\SegmentRouteGuideService.java ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\domain\planning\RouteAnalysisService.java ^
  F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\SegmentRouteGuideServiceTest.java ^
  F:\dachuang\backend\src\test\java\com\citytrip\service\domain\planning\RouteAnalysisServiceTest.java
git commit -m "feat: attach segment route guides to itinerary nodes"
```

---
### Task 3: Preserve `segmentRouteGuide` in frontend snapshots and add guide-format helpers

**Files:**
- Modify: `F:\dachuang\frontend\src\store\itinerary.js`
- Modify: `F:\dachuang\frontend\src\utils\resultUi.js`
- Modify: `F:\dachuang\frontend\src\store\__tests__\itinerary.test.js`
- Modify: `F:\dachuang\frontend\src\utils\__tests__\resultUi.test.js`

- [ ] **Step 1: Write the failing snapshot and helper tests**

```js
it('keeps segmentRouteGuide facts when normalizing itinerary snapshots', () => {
  const snapshot = normalizeItinerarySnapshot({
    nodes: [{
      poiId: 1,
      poiName: '宽窄巷子',
      segmentRouteGuide: {
        summary: '步行 300 米 → 地铁 2 站 → 步行 450 米',
        transportMode: '地铁+步行',
        durationMinutes: 27,
        distanceKm: 8.4,
        detailAvailable: true,
        steps: [
          { stepOrder: 1, type: 'walk', instruction: '步行 300 米到天府广场地铁站 B 口', distanceMeters: 300, durationMinutes: 4, pathPoints: [{ latitude: 30.65, longitude: 104.06 }] },
          { stepOrder: 2, type: 'metro', instruction: '乘 1 号线往文殊院方向 2 站', lineName: '1号线', stopCount: 2, pathPoints: [{ latitude: 30.66, longitude: 104.05 }] }
        ],
        pathPoints: [{ latitude: 30.65, longitude: 104.06 }, { latitude: 30.66, longitude: 104.05 }],
        source: 'route-provider'
      }
    }]
  })

  expect(snapshot.nodes[0].segmentRouteGuide.summary).toBe('步行 300 米 → 地铁 2 站 → 步行 450 米')
  expect(snapshot.nodes[0].segmentRouteGuide.steps[1].lineName).toBe('1号线')
  expect(snapshot.nodes[0].segmentRouteGuide.pathPoints).toHaveLength(2)
})
```

```js
it('builds guide titles and summary fallbacks without inventing structure', () => {
  expect(buildSegmentGuideTitle({
    stepOrder: 1,
    fromName: '当前位置',
    toName: '宽窄巷子'
  })).toBe('当前位置 → 宽窄巷子')

  expect(formatSegmentGuideSummary({
    summary: '',
    transportMode: '打车',
    durationMinutes: 14,
    distanceKm: 5.1
  })).toBe('打车约 14 分钟，约 5.1 公里')
})
```

- [ ] **Step 2: Run the frontend unit tests and confirm the new guide fields are missing**

Run: `npm run test -- src/store/__tests__/itinerary.test.js src/utils/__tests__/resultUi.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: FAIL because `segmentRouteGuide` is not normalized yet and helper exports do not exist.

- [ ] **Step 3: Normalize `segmentRouteGuide` in `itinerary.js` without fabricating any missing facts**

```js
const normalizeRoutePathPoint = point => {
  const latitude = Number(point?.latitude ?? point?.lat)
  const longitude = Number(point?.longitude ?? point?.lng)
  if (!Number.isFinite(latitude) || !Number.isFinite(longitude)) {
    return null
  }
  return { latitude, longitude }
}

const normalizeRoutePathPoints = points => {
  if (!Array.isArray(points)) {
    return []
  }
  return points.map(normalizeRoutePathPoint).filter(Boolean)
}

const normalizeSegmentGuideStep = step => {
  if (!step || typeof step !== 'object') {
    return null
  }
  return {
    ...step,
    stepOrder: Number.isFinite(Number(step.stepOrder)) ? Number(step.stepOrder) : null,
    distanceMeters: Number.isFinite(Number(step.distanceMeters)) ? Number(step.distanceMeters) : null,
    durationMinutes: Number.isFinite(Number(step.durationMinutes)) ? Number(step.durationMinutes) : null,
    stopCount: Number.isFinite(Number(step.stopCount)) ? Number(step.stopCount) : null,
    instruction: localizeItineraryText(step.instruction),
    lineName: localizeItineraryText(step.lineName),
    fromStation: localizeItineraryText(step.fromStation),
    toStation: localizeItineraryText(step.toStation),
    entranceName: localizeItineraryText(step.entranceName),
    exitName: localizeItineraryText(step.exitName),
    pathPoints: normalizeRoutePathPoints(step.pathPoints)
  }
}

const normalizeSegmentRouteGuide = guide => {
  if (!guide || typeof guide !== 'object') {
    return null
  }
  return {
    ...guide,
    summary: localizeItineraryText(guide.summary),
    transportMode: localizeItineraryText(guide.transportMode),
    durationMinutes: Number.isFinite(Number(guide.durationMinutes)) ? Number(guide.durationMinutes) : null,
    distanceKm: Number.isFinite(Number(guide.distanceKm)) ? Number(guide.distanceKm) : null,
    detailAvailable: Boolean(guide.detailAvailable),
    incompleteReason: localizeItineraryText(guide.incompleteReason),
    steps: Array.isArray(guide.steps) ? guide.steps.map(normalizeSegmentGuideStep).filter(Boolean) : [],
    pathPoints: normalizeRoutePathPoints(guide.pathPoints),
    source: guide.source || null
  }
}
```

```js
return {
  ...node,
  sysReason: localizeItineraryText(node?.sysReason),
  warmTipCandidates: localizedWarmTipCandidates,
  selectedWarmTip,
  statusNote: finalStatusNote,
  segmentRouteGuide: normalizeSegmentRouteGuide(node?.segmentRouteGuide)
}
```

- [ ] **Step 4: Add guide title and summary helpers in `resultUi.js`**

```js
export const buildSegmentGuideTitle = ({ stepOrder, fromName, toName }) => {
  const safeFrom = typeof fromName === 'string' && fromName.trim() ? fromName.trim() : (Number(stepOrder) === 1 ? '当前位置' : '上一站')
  const safeTo = typeof toName === 'string' && toName.trim() ? toName.trim() : (Number(stepOrder) === 1 ? '首站' : '当前站')
  return `${safeFrom} → ${safeTo}`
}

export const formatSegmentGuideSummary = guide => {
  if (guide?.summary && String(guide.summary).trim()) {
    return String(guide.summary).trim()
  }

  const mode = guide?.transportMode && String(guide.transportMode).trim()
    ? String(guide.transportMode).trim()
    : '出行'
  const durationMinutes = Number(guide?.durationMinutes)
  const distanceKm = formatDistanceNumber(guide?.distanceKm)

  if (Number.isFinite(durationMinutes) && durationMinutes > 0 && distanceKm) {
    return `${mode}约 ${durationMinutes} 分钟，约 ${distanceKm} 公里`
  }
  if (Number.isFinite(durationMinutes) && durationMinutes > 0) {
    return `${mode}约 ${durationMinutes} 分钟`
  }
  return mode
}
```

- [ ] **Step 5: Re-run the snapshot and helper tests**

Run: `npm run test -- src/store/__tests__/itinerary.test.js src/utils/__tests__/resultUi.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: PASS and snapshot reload now preserves `segmentRouteGuide` with no synthetic data.

- [ ] **Step 6: Commit the frontend normalization slice**

```bash
git add ^
  F:\dachuang\frontend\src\store\itinerary.js ^
  F:\dachuang\frontend\src\utils\resultUi.js ^
  F:\dachuang\frontend\src\store\__tests__\itinerary.test.js ^
  F:\dachuang\frontend\src\utils\__tests__\resultUi.test.js
git commit -m "feat: preserve segment route guides in frontend state"
```

---
### Task 4: Build the guide card and segment mini-map components

**Files:**
- Create: `F:\dachuang\frontend\src\components\itinerary\SegmentRouteGuideCard.vue`
- Create: `F:\dachuang\frontend\src\components\itinerary\SegmentMiniMap.vue`
- Create: `F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentRouteGuideCard.test.js`
- Create: `F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentMiniMap.test.js`

- [ ] **Step 1: Write the failing component tests**

```js
import { mount } from '@vue/test-utils'
import { describe, expect, it } from 'vitest'
import SegmentRouteGuideCard from '../SegmentRouteGuideCard.vue'

const buildWrapper = props => mount(SegmentRouteGuideCard, {
  props,
  global: {
    stubs: {
      SegmentMiniMap: { template: '<div class="mini-map-stub" />' },
      ElButton: { emits: ['click'], template: '<button @click="$emit(\'click\')"><slot /></button>' },
      ElTag: { template: '<span><slot /></span>' },
      ElAlert: { props: ['title'], template: '<div class="alert">{{ title }}</div>' }
    }
  }
})

describe('SegmentRouteGuideCard', () => {
  it('renders summary and detail steps from factual guide data', () => {
    const wrapper = buildWrapper({
      guide: {
        summary: '步行 300 米 → 地铁 2 站 → 步行 450 米',
        detailAvailable: true,
        steps: [
          { stepOrder: 1, instruction: '步行 300 米到天府广场地铁站 B 口' },
          { stepOrder: 2, instruction: '乘 1 号线往文殊院方向 2 站' }
        ]
      },
      fromName: '当前位置',
      toName: '宽窄巷子',
      expanded: true,
      segmentIndex: 0
    })

    expect(wrapper.text()).toContain('当前位置 → 宽窄巷子')
    expect(wrapper.text()).toContain('步行 300 米 → 地铁 2 站 → 步行 450 米')
    expect(wrapper.text()).toContain('乘 1 号线往文殊院方向 2 站')
  })

  it('shows incomplete notice and emits toggle/focus events', async () => {
    const wrapper = buildWrapper({
      guide: {
        summary: '打车约 14 分钟，约 5.1 公里',
        detailAvailable: false,
        incompleteReason: '该段暂未获取完整导航详情',
        steps: []
      },
      fromName: '博物馆',
      toName: '春熙路',
      expanded: false,
      segmentIndex: 1
    })

    const buttons = wrapper.findAll('button')
    await buttons[0].trigger('click')
    await buttons[1].trigger('click')

    expect(wrapper.emitted('focus-segment')).toEqual([[1]])
    expect(wrapper.emitted('toggle')).toEqual([[1]])
  })
})
```

```js
import { describe, expect, it } from 'vitest'
import source from '../SegmentMiniMap.vue?raw'

describe('SegmentMiniMap', () => {
  it('renders only the current segment geometry and keeps incomplete-state copy explicit', () => {
    expect(source).toContain('props.guide?.pathPoints')
    expect(source).toContain('props.guide?.steps')
    expect(source).toContain('当前仅展示概略路径')
    expect(source).toContain('该段暂未获取完整导航详情')
  })
})
```

- [ ] **Step 2: Run the new component tests and confirm the files do not exist yet**

Run: `npm run test -- src/components/itinerary/__tests__/SegmentRouteGuideCard.test.js src/components/itinerary/__tests__/SegmentMiniMap.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: FAIL because the new component files are missing.

- [ ] **Step 3: Implement `SegmentMiniMap.vue` with segment-only rendering**

```vue
<template>
  <section class="segment-mini-map">
    <div class="mini-map-head">
      <div>
        <p class="mini-map-kicker">本段地图</p>
        <strong>{{ title }}</strong>
      </div>
      <span v-if="modeLabel" class="mini-map-badge">{{ modeLabel }}</span>
    </div>
    <div v-if="hasRenderablePath" ref="mapRef" class="mini-map-canvas"></div>
    <el-empty v-else description="当前仅展示概略路径" />
    <p v-if="notice" class="mini-map-note">{{ notice }}</p>
  </section>
</template>

<script setup>
import { computed, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import L from 'leaflet'
import 'leaflet/dist/leaflet.css'

const props = defineProps({
  guide: { type: Object, default: null },
  fromName: { type: String, default: '' },
  toName: { type: String, default: '' }
})

const mapRef = ref(null)
let map = null
let layerGroup = null

const toLatLng = point => {
  const latitude = Number(point?.latitude ?? point?.lat)
  const longitude = Number(point?.longitude ?? point?.lng)
  return Number.isFinite(latitude) && Number.isFinite(longitude) ? [latitude, longitude] : null
}

const pathPoints = computed(() => Array.isArray(props.guide?.pathPoints) ? props.guide.pathPoints.map(toLatLng).filter(Boolean) : [])
const stepPolylines = computed(() => (props.guide?.steps || [])
  .map(step => Array.isArray(step?.pathPoints) ? step.pathPoints.map(toLatLng).filter(Boolean) : [])
  .filter(points => points.length > 1))
const hasRenderablePath = computed(() => pathPoints.value.length > 1)
const title = computed(() => `${props.fromName || '上一站'} → ${props.toName || '当前站'}`)
const modeLabel = computed(() => props.guide?.transportMode || '')
const notice = computed(() => {
  if (props.guide?.detailAvailable) {
    return ''
  }
  if (props.guide?.pathPoints?.length) {
    return props.guide?.incompleteReason || '该段暂未获取完整导航详情'
  }
  return '当前仅展示概略路径'
})

const renderMap = () => {
  if (!mapRef.value || !hasRenderablePath.value) {
    return
  }
  if (!map) {
    map = L.map(mapRef.value, {
      zoomControl: false,
      attributionControl: false
    })
    L.tileLayer('https://{s}.tile.openstreetmap.org/{z}/{x}/{y}.png', {
      maxZoom: 18
    }).addTo(map)
  }
  if (layerGroup) {
    layerGroup.remove()
  }

  const layers = []
  layers.push(L.polyline(pathPoints.value, {
    color: '#409eff',
    weight: 6,
    opacity: 0.92,
    lineCap: 'round',
    lineJoin: 'round'
  }))

  stepPolylines.value.forEach((points, index) => {
    layers.push(L.polyline(points, {
      color: index % 2 === 0 ? '#7cb8ff' : '#7c5cff',
      weight: 4,
      opacity: 0.72,
      dashArray: '10 10'
    }))
  })

  layers.push(L.circleMarker(pathPoints.value[0], { radius: 7, color: '#24c0a6', fillOpacity: 1 }))
  layers.push(L.circleMarker(pathPoints.value[pathPoints.value.length - 1], { radius: 7, color: '#7c5cff', fillOpacity: 1 }))
  layerGroup = L.layerGroup(layers).addTo(map)
  map.fitBounds(L.featureGroup(layers).getBounds(), {
    padding: [28, 28]
  })
}

onMounted(renderMap)
onBeforeUnmount(() => map?.remove())
watch([pathPoints, stepPolylines], renderMap, { deep: true })
</script>
```

- [ ] **Step 4: Implement `SegmentRouteGuideCard.vue` with summary + expandable left-text/right-map layout**

```vue
<template>
  <section class="segment-guide-card" :class="{ expanded }">
    <div class="guide-summary-row">
      <div class="guide-summary-main">
        <p class="guide-kicker">怎么走</p>
        <strong class="guide-title">{{ title }}</strong>
        <p class="guide-summary">{{ summary }}</p>
      </div>
      <div class="guide-summary-actions">
        <el-tag v-if="showIncompleteTag" size="small" type="warning" effect="plain">详情不完整</el-tag>
        <el-button text @click="$emit('focus-segment', segmentIndex)">高亮这一段</el-button>
        <el-button type="primary" link @click="$emit('toggle', segmentIndex)">
          {{ expanded ? '收起导航' : '查看怎么走' }}
        </el-button>
      </div>
    </div>

    <div v-if="expanded" class="guide-detail-grid">
      <div class="guide-detail-copy">
        <ol v-if="steps.length" class="guide-step-list">
          <li v-for="step in steps" :key="`${segmentIndex}-${step.stepOrder}`" class="guide-step-item">
            <span class="guide-step-order">{{ step.stepOrder }}</span>
            <div>
              <p class="guide-step-instruction">{{ step.instruction }}</p>
              <p v-if="buildStepMeta(step)" class="guide-step-meta">{{ buildStepMeta(step) }}</p>
            </div>
          </li>
        </ol>
        <el-alert
          v-else
          :title="detailNotice"
          type="warning"
          :closable="false"
          show-icon
        />
        <p v-if="showPathFallback" class="guide-fallback-note">当前仅展示概略路径</p>
      </div>

      <SegmentMiniMap
        :guide="guide"
        :from-name="fromName"
        :to-name="toName"
      />
    </div>
  </section>
</template>

<script setup>
import { computed } from 'vue'
import SegmentMiniMap from './SegmentMiniMap.vue'
import { buildSegmentGuideTitle, formatSegmentGuideSummary } from '@/utils/resultUi'

const props = defineProps({
  guide: { type: Object, default: null },
  fromName: { type: String, default: '' },
  toName: { type: String, default: '' },
  expanded: { type: Boolean, default: false },
  segmentIndex: { type: Number, default: 0 },
  stepOrder: { type: Number, default: 1 }
})

defineEmits(['toggle', 'focus-segment'])

const title = computed(() => buildSegmentGuideTitle({
  stepOrder: props.stepOrder,
  fromName: props.fromName,
  toName: props.toName
}))
const summary = computed(() => formatSegmentGuideSummary(props.guide))
const steps = computed(() => Array.isArray(props.guide?.steps) ? props.guide.steps : [])
const showIncompleteTag = computed(() => props.guide && props.guide.detailAvailable === false)
const detailNotice = computed(() => props.guide?.incompleteReason || '该段暂未获取完整导航详情')
const showPathFallback = computed(() => props.guide?.detailAvailable === false && Array.isArray(props.guide?.pathPoints) && props.guide.pathPoints.length > 1)

const buildStepMeta = step => {
  const parts = []
  if (step?.lineName) parts.push(step.lineName)
  if (step?.stopCount) parts.push(`${step.stopCount} 站`)
  if (step?.distanceMeters) parts.push(`${step.distanceMeters} 米`)
  if (step?.durationMinutes) parts.push(`${step.durationMinutes} 分钟`)
  if (step?.entranceName) parts.push(`入口 ${step.entranceName}`)
  if (step?.exitName) parts.push(`出口 ${step.exitName}`)
  return parts.join(' · ')
}
</script>
```

- [ ] **Step 5: Re-run the component tests**

Run: `npm run test -- src/components/itinerary/__tests__/SegmentRouteGuideCard.test.js src/components/itinerary/__tests__/SegmentMiniMap.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: PASS and the card now exposes factual step copy plus explicit incomplete-state messaging.

- [ ] **Step 6: Commit the new guide component slice**

```bash
git add ^
  F:\dachuang\frontend\src\components\itinerary\SegmentRouteGuideCard.vue ^
  F:\dachuang\frontend\src\components\itinerary\SegmentMiniMap.vue ^
  F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentRouteGuideCard.test.js ^
  F:\dachuang\frontend\src\components\itinerary\__tests__\SegmentMiniMap.test.js
git commit -m "feat: add segment navigation guide components"
```

---
### Task 5: Integrate the guide card into `Result.vue` and sync it with map segments

**Files:**
- Modify: `F:\dachuang\frontend\src\views\Result.vue`
- Modify: `F:\dachuang\frontend\src\views\__tests__\Result.test.js`

- [ ] **Step 1: Write the failing result-view tests for guide embedding and single-expand behavior**

```js
it('embeds segment route guide cards inside each stop card and keeps one expanded guide at a time', () => {
  expect(resultSource).toContain('SegmentRouteGuideCard')
  expect(resultSource).toContain('expandedRouteGuideIndex')
  expect(resultSource).toContain(':guide="node.segmentRouteGuide"')
  expect(resultSource).toContain('@toggle="handleToggleRouteGuide"')
  expect(resultSource).toContain('@focus-segment="handleFocusRouteSegment"')
})

it('opens the matching guide when a map segment is pinned and resets expanded state when switching days', () => {
  expect(resultSource).toContain('handleMapSegmentPin')
  expect(resultSource).toContain('expandedRouteGuideIndex.value = segmentIndex')
  expect(resultSource).toContain('watch(activeDayIndex')
  expect(resultSource).toContain('expandedRouteGuideIndex.value = null')
})
```

- [ ] **Step 2: Run the result-view tests and confirm the new guide integration is absent**

Run: `npm run test -- src/views/__tests__/Result.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: FAIL because `Result.vue` does not yet import or render `SegmentRouteGuideCard`.

- [ ] **Step 3: Add the page-level guide state and embed the new component into each stop card**

```vue
<script setup>
import SegmentRouteGuideCard from '@/components/itinerary/SegmentRouteGuideCard.vue'

const expandedRouteGuideIndex = ref(null)

const resolveGuideFromName = nodeIndex => {
  if (nodeIndex === 0) {
    return departurePoint.value?.label || originalReq.value?.departurePlaceName || '当前位置'
  }
  return displayNodes.value[nodeIndex - 1]?.poiName || '上一站'
}

const handleToggleRouteGuide = segmentIndex => {
  expandedRouteGuideIndex.value = expandedRouteGuideIndex.value === segmentIndex ? null : segmentIndex
  pinnedSegmentIndex.value = expandedRouteGuideIndex.value === null ? null : segmentIndex
}

const handleFocusRouteSegment = segmentIndex => {
  pinnedSegmentIndex.value = segmentIndex
  expandedRouteGuideIndex.value = segmentIndex
  scrollTimelineNodeIntoView(segmentIndex)
}
</script>
```

```vue
<SegmentRouteGuideCard
  v-if="node.segmentRouteGuide"
  :guide="node.segmentRouteGuide"
  :from-name="resolveGuideFromName(nodeIndex)"
  :to-name="node.poiName"
  :step-order="node.stepOrder || 1"
  :expanded="expandedRouteGuideIndex === nodeIndex"
  :segment-index="nodeIndex"
  @toggle="handleToggleRouteGuide"
  @focus-segment="handleFocusRouteSegment"
/>
```

- [ ] **Step 4: Sync map pinning and day switching with the guide expansion state**

```js
const handleMapSegmentPin = segmentIndex => {
  pinnedSegmentIndex.value = pinnedSegmentIndex.value === segmentIndex ? null : segmentIndex
  expandedRouteGuideIndex.value = pinnedSegmentIndex.value === null ? null : segmentIndex
  if (expandedRouteGuideIndex.value !== null) {
    scrollTimelineNodeIntoView(segmentIndex)
  }
}

watch(activeDayIndex, () => {
  expandedRouteGuideIndex.value = null
  hoveredSegmentIndex.value = null
  pinnedSegmentIndex.value = null
})
```

```js
const scrollTimelineNodeIntoView = nodeIndex => {
  const element = timelineNodeRefs.value[nodeIndex]
  if (!element?.scrollIntoView) {
    return
  }
  requestAnimationFrame(() => {
    element.scrollIntoView({
      behavior: 'smooth',
      block: 'nearest'
    })
  })
}
```

- [ ] **Step 5: Re-run the result-view tests**

Run: `npm run test -- src/views/__tests__/Result.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: PASS and `Result.vue` now declares the guide card, its state, and its map synchronization hooks.

- [ ] **Step 6: Commit the result-page integration slice**

```bash
git add ^
  F:\dachuang\frontend\src\views\Result.vue ^
  F:\dachuang\frontend\src\views\__tests__\Result.test.js
git commit -m "feat: integrate segment navigation guides into result page"
```

---
### Task 6: Final regression hardening, serialization, and verification

**Files:**
- Modify: `F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\service\domain\ai\ItineraryAiDecorationServiceTest.java`
- Modify: `F:\dachuang\backend\src\test\java\com\citytrip\controller\AuthAndItineraryControllerTest.java`

- [ ] **Step 1: Write the failing regression tests for AI decoration and controller serialization**

```java
@Test
void decorateWithLlmPreservesSegmentRouteGuideFacts() {
    LlmService llmService = mock(LlmService.class);
    ItineraryComparisonAssembler itineraryComparisonAssembler = mock(ItineraryComparisonAssembler.class);
    ItineraryAiDecorationService service = buildService(llmService, itineraryComparisonAssembler, 500L);

    ItineraryNodeVO node = new ItineraryNodeVO();
    node.setPoiId(1L);
    node.setPoiName("宽窄巷子");

    SegmentRouteStepVO step = new SegmentRouteStepVO();
    step.setStepOrder(1);
    step.setInstruction("步行 300 米到天府广场地铁站 B 口");

    SegmentRouteGuideVO guide = new SegmentRouteGuideVO();
    guide.setSummary("步行 300 米 → 地铁 2 站 → 步行 450 米");
    guide.setDetailAvailable(true);
    guide.setSteps(List.of(step));
    node.setSegmentRouteGuide(guide);

    ItineraryVO itinerary = new ItineraryVO();
    itinerary.setNodes(List.of(node));

    when(llmService.explainItinerary(any(), anyList())).thenReturn(null);
    when(llmService.explainPoiChoice(any(), any())).thenReturn("这站适合放在线路前段");
    when(llmService.generatePoiWarmTips(any(), any())).thenReturn("先走主街\n避开高峰\n拍照更从容");
    when(llmService.generateRouteWarmTip(any(), anyList())).thenReturn("今天按主线慢慢走");

    ItineraryVO result = service.decorateWithLlm(itinerary, new GenerateReqDTO());

    assertThat(result.getNodes().get(0).getSegmentRouteGuide()).isNotNull();
    assertThat(result.getNodes().get(0).getSegmentRouteGuide().getSummary()).isEqualTo("步行 300 米 → 地铁 2 站 → 步行 450 米");
    assertThat(result.getNodes().get(0).getSegmentRouteGuide().getSteps()).hasSize(1);
}
```

```java
@Test
void generateItineraryEndpointSerializesSegmentRouteGuide() throws Exception {
    GenerateReqDTO req = new GenerateReqDTO();
    req.setCityName("成都");
    req.setTripDate("2026-05-01");
    req.setStartTime("09:30");
    req.setEndTime("21:30");

    ItineraryVO itineraryVO = buildItinerary(601L, 420, "AI generated route");
    ItineraryNodeVO node = new ItineraryNodeVO();
    node.setPoiId(1L);
    node.setPoiName("宽窄巷子");

    SegmentRouteStepVO step = new SegmentRouteStepVO();
    step.setStepOrder(1);
    step.setInstruction("步行 300 米到地铁站");
    step.setLineName("1号线");

    SegmentRouteGuideVO guide = new SegmentRouteGuideVO();
    guide.setSummary("步行 300 米 → 地铁 2 站");
    guide.setDetailAvailable(true);
    guide.setSteps(List.of(step));
    node.setSegmentRouteGuide(guide);
    itineraryVO.setNodes(List.of(node));

    when(itineraryService.generateUserItinerary(eq(101L), any(GenerateReqDTO.class))).thenReturn(itineraryVO);

    mockMvc.perform(post("/api/itineraries/generate")
                    .header("Authorization", bearerToken)
                    .contentType(MediaType.APPLICATION_JSON)
                    .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.summary").value("步行 300 米 → 地铁 2 站"))
            .andExpect(jsonPath("$.nodes[0].segmentRouteGuide.steps[0].lineName").value("1号线"));
}
```

- [ ] **Step 2: Run the backend regression tests and confirm the current copy/serialization path is insufficient**

Run: `mvn -Dtest=ItineraryAiDecorationServiceTest,AuthAndItineraryControllerTest test`  
Working directory: `F:\dachuang\backend`  
Expected: FAIL if nested guide fields are lost or not asserted by the API tests yet.

- [ ] **Step 3: Harden `ItineraryAiDecorationService.copyItinerary()` so nested guide objects survive AI decoration**

```java
private ItineraryVO copyItinerary(ItineraryVO source) {
    if (source == null) {
        return null;
    }
    try {
        return objectMapper.readValue(objectMapper.writeValueAsBytes(source), ItineraryVO.class);
    } catch (Exception ex) {
        log.warn("Deep copy itinerary failed, fallback to original instance, reason={}", summarizeExecutionFailure(ex));
        return source;
    }
}
```

- [ ] **Step 4: Re-run targeted backend regressions, then run the cross-stack targeted suite**

Run: `mvn -Dtest=GeoSearchServiceImplTest,SegmentRouteGuideServiceTest,RouteAnalysisServiceTest,ItineraryAiDecorationServiceTest,AuthAndItineraryControllerTest test`  
Working directory: `F:\dachuang\backend`  
Expected: PASS for parser, guide assembly, AI-preservation, and controller serialization.

Run: `npm run test -- src/store/__tests__/itinerary.test.js src/utils/__tests__/resultUi.test.js src/components/itinerary/__tests__/SegmentRouteGuideCard.test.js src/components/itinerary/__tests__/SegmentMiniMap.test.js src/views/__tests__/Result.test.js`  
Working directory: `F:\dachuang\frontend`  
Expected: PASS for snapshot persistence, helpers, guide components, and result-page integration.

- [ ] **Step 5: Perform the manual smoke verification before claiming completion**

Run backend and frontend locally, then verify this exact checklist:

```text
1. Generate an itinerary with a real departure location.
2. Inspect the itinerary response payload and confirm every node contains segmentRouteGuide.
3. Confirm node 0 renders “当前位置 -> 首站” semantics, not “上一站 -> 当前站”.
4. Expand one guide card and verify the panel is left text / right mini map.
5. Use a leg without provider steps and confirm the UI shows “该段暂未获取完整导航详情”.
6. Click a segment on the left map and confirm the matching right-side guide opens automatically.
7. Refresh the page and confirm the guide data restores from snapshot without disappearing.
```

- [ ] **Step 6: Commit the hardening and verification slice**

```bash
git add ^
  F:\dachuang\backend\src\main\java\com\citytrip\service\domain\ai\ItineraryAiDecorationService.java ^
  F:\dachuang\backend\src\test\java\com\citytrip\service\domain\ai\ItineraryAiDecorationServiceTest.java ^
  F:\dachuang\backend\src\test\java\com\citytrip\controller\AuthAndItineraryControllerTest.java
git commit -m "test: harden segment guide serialization and preservation"
```

---

## Self-Review

### 1. Spec coverage

- **真实逐段导航说明** → Task 1 parses factual route steps; Task 2 assembles `segmentRouteGuide`.
- **第一个点显示“当前位置 -> 首站”** → Task 2 keeps `prev = null` on day starts; Task 5 computes `fromName` from departure semantics.
- **其余点显示“上一站 -> 当前站”** → Task 5 computes `fromName` from the previous displayed node.
- **展开区域左文右图** → Task 4 builds `SegmentRouteGuideCard.vue` + `SegmentMiniMap.vue`.
- **只用真实 provider facts，不允许模型编造** → Task 1 parses only provider fields; Task 2 assembles guide data only from `GeoRouteEstimate` and `TravelLegEstimate`.
- **拿不到完整结构化导航时明确降级** → Task 2 sets `detailAvailable` and `incompleteReason`; Task 4 renders explicit incomplete-state copy.
- **生成整条路线时一次性算完整段** → Task 2 attaches guides during `RouteAnalysisService.analyzeRoute`, not on-demand from the frontend.
- **多天行程每个 day 首站沿用 departure 语义** → Task 2 preserves day-boundary reset behavior and adds route-analysis tests for it.
- **快照刷新后依然保留 guide 数据** → Task 3 preserves `segmentRouteGuide` in `itinerary.js`; Task 6 smoke-tests refresh recovery.

### 2. Placeholder scan

- No placeholder markers remain, and there are no deferred implementation notes.
- Every task includes concrete files, test commands, implementation snippets, and commit commands.

### 3. Type consistency

- Backend contract flow is consistent: `GeoRouteStep` → `GeoRouteEstimate.steps` → `TravelLegEstimate.detailedRoute` → `SegmentRouteGuideService` → `SegmentRouteGuideVO` / `SegmentRouteStepVO` → `ItineraryNodeVO.segmentRouteGuide`.
- Frontend uses the same property name end-to-end: `segmentRouteGuide`.
- Path geometry remains consistent on the frontend through `RoutePathPointVO`-shaped `{ latitude, longitude }` objects.


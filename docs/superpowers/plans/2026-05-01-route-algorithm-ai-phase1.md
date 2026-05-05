# Route Algorithm AI Phase 1 Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Split the first layer of `ItineraryRouteOptimizer` responsibilities into persistent strategy/search/planning services while preserving existing behavior.

**Architecture:** Keep `ItineraryRouteOptimizer` as a compatibility facade in phase 1. Extract scoring into `PoiScoringStrategy` and `DefaultPoiScoringStrategy`, candidate filtering into `CandidatePreparationService`, and route ranking behind `RouteSearchEngine`. Existing public callers continue to compile against `ItineraryRouteOptimizer` while the active behavior is delegated to new services.

**Tech Stack:** Java 17, Spring Boot, JUnit 5, Mockito, Maven.

---

## Files

- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/ScoreBreakdown.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/PoiScoringStrategy.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/DefaultPoiScoringStrategy.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/planning/ItineraryRequestNormalizer.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/planning/CandidatePreparationService.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/routing/RouteSearchEngine.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/routing/LegacyRouteSearchEngine.java`
- Modify: `backend/src/main/java/com/citytrip/service/impl/ItineraryRouteOptimizer.java`
- Test: `backend/src/test/java/com/citytrip/service/domain/scoring/DefaultPoiScoringStrategyTest.java`
- Test: `backend/src/test/java/com/citytrip/service/domain/planning/CandidatePreparationServiceTest.java`
- Test: existing `backend/src/test/java/com/citytrip/service/impl/ItineraryRouteOptimizerDpTest.java`

## Task 0: Baseline

- [x] **Step 1: Create isolated worktree**

Run: `git worktree add .worktrees/route-algorithm-ai-refactor-phase1 codex/route-algorithm-ai-refactor-phase1`
Expected: worktree checked out at `F:\dachuang\.worktrees\route-algorithm-ai-refactor-phase1`.

- [x] **Step 2: Run full backend tests**

Run: `mvn test` from `backend`.
Expected: `Tests run: 189, Failures: 0, Errors: 0, Skipped: 0` and `BUILD SUCCESS`.

## Task 1: Extract request normalization and scoring strategy

- [x] **Step 1: Write focused scoring test**

Create `DefaultPoiScoringStrategyTest` with assertions that rainy group requests keep the same ordering behavior currently covered indirectly by `ItineraryRouteOptimizerDpTest`.

- [x] **Step 2: Run failing focused test**

Run: `mvn test -Dtest=DefaultPoiScoringStrategyTest`
Expected before implementation: compile failure because the new classes do not exist.

- [x] **Step 3: Implement scoring classes**

Create `ScoreBreakdown`, `PoiScoringStrategy`, and `DefaultPoiScoringStrategy`. Move current scoring-related helpers from `ItineraryRouteOptimizer` into this service: weather and walking constraints, companion matching, must-visit matching, base crowd penalty, visit crowd penalty, route value, POI cost, stay duration normalization.

- [x] **Step 4: Run focused scoring test**

Run: `mvn test -Dtest=DefaultPoiScoringStrategyTest`
Expected: PASS.

## Task 2: Extract candidate preparation

- [x] **Step 1: Write candidate preparation test**

Create `CandidatePreparationServiceTest` that verifies rainy + low-walking + group request filters and sorts candidates using `DefaultPoiScoringStrategy`.

- [x] **Step 2: Run failing candidate test**

Run: `mvn test -Dtest=CandidatePreparationServiceTest`
Expected before implementation: compile failure because `CandidatePreparationService` does not exist.

- [x] **Step 3: Implement candidate preparation service**

Create `CandidatePreparationService` with method `prepareCandidates(List<Poi> source, GenerateReqDTO req, boolean applyLimit)`. It calls `PoiService.enrichOperatingStatus`, applies scoring, filters unavailable candidates, sorts by score and priority, and applies the existing candidate limit.

- [x] **Step 4: Delegate optimizer candidate preparation**

Modify `ItineraryRouteOptimizer.prepareCandidates` to call `CandidatePreparationService` while keeping the public method signature unchanged.

- [x] **Step 5: Run candidate and optimizer tests**

Run: `mvn test -Dtest=CandidatePreparationServiceTest,ItineraryRouteOptimizerDpTest`
Expected: PASS.

## Task 3: Introduce route search engine seam

- [x] **Step 1: Create `RouteSearchEngine` interface**

Create interface with method `List<ItineraryRouteOptimizer.RouteOption> rankRoutes(List<Poi> candidates, GenerateReqDTO req, int maxStops)`.

- [x] **Step 2: Implement first persistent engine adapter**

Create `LegacyRouteSearchEngine` as a Spring component. It contains a dedicated `RouteRanker` callback so phase 1 can move the active DP/Beam call behind an interface without changing route results. This is a transitional seam; subsequent phase 1 iterations will move DP/Beam private methods from the facade into dedicated engines.

- [x] **Step 3: Delegate optimizer route ranking**

Modify `ItineraryRouteOptimizer.rankRoutes` to delegate through `RouteSearchEngine`, with an internal guard method to avoid recursion.

- [x] **Step 4: Run optimizer route tests**

Run: `mvn test -Dtest=ItineraryRouteOptimizerDpTest`
Expected: PASS with unchanged signatures and utility values.

## Task 4: Full verification and commit

- [x] **Step 1: Run full backend tests**

Run: `mvn test`
Expected: all backend tests pass.

- [x] **Step 2: Review git diff**

Run: `git diff --stat` and `git diff --check`.
Expected: no whitespace errors; changes only in planned files.

- [x] **Step 3: Commit phase 1A**

Run: `git add ... && git commit -m "refactor: extract route scoring and planning seams"`.
Expected: one commit on `codex/route-algorithm-ai-refactor-phase1`.

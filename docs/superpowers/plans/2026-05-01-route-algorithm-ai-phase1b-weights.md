# Route Algorithm AI Phase 1B Weights Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Start Stage 2 by moving route scoring magic numbers into a persistent algorithm weight provider while preserving current route behavior.

**Architecture:** Add `AlgorithmWeightsProperties`, immutable `AlgorithmWeightsSnapshot`, and `DynamicAlgorithmWeightProvider`. Scoring strategies read weights through the provider on every score calculation, enabling runtime update without service restart. Existing defaults match the current hard-coded numbers so current tests remain green.

**Tech Stack:** Java 17, Spring Boot `@ConfigurationProperties`, JUnit 5, Maven.

---

## Files

- Create: `backend/src/main/java/com/citytrip/config/AlgorithmWeightsProperties.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/AlgorithmWeightsSnapshot.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/AlgorithmWeightProvider.java`
- Create: `backend/src/main/java/com/citytrip/service/domain/scoring/DynamicAlgorithmWeightProvider.java`
- Modify: `backend/src/main/java/com/citytrip/CityTripApplication.java`
- Modify: `backend/src/main/java/com/citytrip/service/domain/scoring/DefaultPoiScoringStrategy.java`
- Modify: `backend/src/main/java/com/citytrip/service/domain/scoring/BudgetScoringStrategy.java`
- Modify: `backend/src/main/java/com/citytrip/service/domain/scoring/CompanionScoringStrategy.java`
- Modify: `backend/src/main/java/com/citytrip/service/domain/scoring/WeatherScoringStrategy.java`
- Modify: `backend/src/main/java/com/citytrip/service/domain/scoring/WalkingScoringStrategy.java`
- Test: `backend/src/test/java/com/citytrip/service/domain/scoring/DynamicAlgorithmWeightProviderTest.java`
- Test: `backend/src/test/java/com/citytrip/service/domain/scoring/DefaultPoiScoringStrategyTest.java`

## Task 1: RED - Provider update behavior

- [x] Write `DynamicAlgorithmWeightProviderTest` verifying defaults and runtime update.
- [x] Run `mvn test -Dtest=DynamicAlgorithmWeightProviderTest`; expect compile failure for missing provider classes.

## Task 2: GREEN - Provider implementation

- [x] Implement properties, snapshot, provider interface, and dynamic provider.
- [x] Register `AlgorithmWeightsProperties` in `CityTripApplication`.
- [x] Run `mvn test -Dtest=DynamicAlgorithmWeightProviderTest`; expect PASS.

## Task 3: RED - Scoring reads provider dynamically

- [x] Extend `DefaultPoiScoringStrategyTest` to mutate provider weights after constructing the strategy and verify the second score reflects the update.
- [x] Run `mvn test -Dtest=DefaultPoiScoringStrategyTest`; expect failure because scoring still uses constants.

## Task 4: GREEN - Wire scoring strategies to provider

- [x] Change scoring strategies to read `weightProvider.current()` instead of static constants.
- [x] Run `mvn test -Dtest=DefaultPoiScoringStrategyTest,CandidatePreparationServiceTest,ItineraryRouteOptimizerDpTest`; expect PASS.

## Task 5: Full verification and commit

- [x] Run `mvn test`; expect all backend tests pass.
- [x] Run `git diff --check`; expect no whitespace errors.
- [x] Commit with message `refactor: parameterize route scoring weights`.

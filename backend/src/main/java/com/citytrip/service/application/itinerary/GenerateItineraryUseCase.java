package com.citytrip.service.application.itinerary;

import com.citytrip.analytics.RoutePlanFactPublisher;
import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.SavedItinerary;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryOptionVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.impl.PlanningOrchestrator;
import com.citytrip.service.persistence.itinerary.SavedItineraryCodec;
import com.citytrip.service.persistence.itinerary.SavedItineraryRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

@Service
public class GenerateItineraryUseCase {

    private static final Logger log = LoggerFactory.getLogger(GenerateItineraryUseCase.class);

    private final PlanningOrchestrator planningOrchestrator;
    private final ItineraryComparisonAssembler itineraryComparisonAssembler;
    private final ItineraryAiDecorationService itineraryAiDecorationService;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final SavedItineraryRepository savedItineraryRepository;
    private final SavedItineraryCodec savedItineraryCodec;
    private final RoutePlanFactPublisher routePlanFactPublisher;
    private final Executor itineraryAiExecutor;

    public GenerateItineraryUseCase(PlanningOrchestrator planningOrchestrator,
                                    ItineraryComparisonAssembler itineraryComparisonAssembler,
                                    ItineraryAiDecorationService itineraryAiDecorationService,
                                    SavedItineraryCommandService savedItineraryCommandService,
                                    SavedItineraryRepository savedItineraryRepository,
                                    SavedItineraryCodec savedItineraryCodec,
                                    RoutePlanFactPublisher routePlanFactPublisher,
                                    @Qualifier("itineraryAiExecutor") Executor itineraryAiExecutor) {
        this.planningOrchestrator = planningOrchestrator;
        this.itineraryComparisonAssembler = itineraryComparisonAssembler;
        this.itineraryAiDecorationService = itineraryAiDecorationService;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.savedItineraryRepository = savedItineraryRepository;
        this.savedItineraryCodec = savedItineraryCodec;
        this.routePlanFactPublisher = routePlanFactPublisher;
        this.itineraryAiExecutor = itineraryAiExecutor;
    }

    public ItineraryVO generate(Long userId, GenerateReqDTO req) {
        PlanningOrchestrator.PlanningResult planningResult = planningOrchestrator.generate(
                userId,
                req,
                snapshot -> itineraryComparisonAssembler.buildComparedItinerary(
                        snapshot.rankedRoutes(),
                        snapshot.normalizedRequest(),
                        Collections.emptyMap(),
                        null,
                        Collections.emptySet()
                ),
                (normalizedRequest, baseItinerary) -> baseItinerary
        );
        if (!planningResult.success() && planningResult.itinerary() != null) {
            applyFailureReason(planningResult.itinerary(), planningResult.failReason());
        }

        ItineraryVO itinerary = savedItineraryCommandService.save(
                userId,
                null,
                planningResult.normalizedRequest(),
                planningResult.itinerary()
        );
        routePlanFactPublisher.publish(
                userId,
                itinerary.getId(),
                "generate",
                planningResult.normalizedRequest(),
                itinerary,
                planningResult.rawCandidateCount(),
                planningResult.filteredCandidateCount(),
                planningResult.finalCandidateCount(),
                planningResult.maxStops(),
                planningResult.generatedRouteCount(),
                planningResult.displayedOptionCount(),
                planningResult.success(),
                planningResult.failReason(),
                planningResult.algorithmVersion(),
                planningResult.recallStrategy(),
                planningResult.planningStartedAt()
        );
        scheduleAiDecoration(userId, planningResult.normalizedRequest(), itinerary);
        return itinerary;
    }

    private void applyFailureReason(ItineraryVO itinerary, String failReason) {
        if (itinerary == null || failReason == null || failReason.isBlank()) {
            return;
        }
        itinerary.setRecommendReason(failReason);
        List<String> alerts = itinerary.getAlerts() == null ? new java.util.ArrayList<>() : new java.util.ArrayList<>(itinerary.getAlerts());
        if (!alerts.contains(failReason)) {
            alerts.add(0, failReason);
        }
        itinerary.setAlerts(alerts);
    }

    private void scheduleAiDecoration(Long userId, GenerateReqDTO normalizedRequest, ItineraryVO savedItinerary) {
        if (savedItinerary == null || savedItinerary.getId() == null) {
            return;
        }
        Long itineraryId = savedItinerary.getId();
        CompletableFuture
                .supplyAsync(() -> itineraryAiDecorationService.decorateWithLlm(savedItinerary, normalizedRequest), itineraryAiExecutor)
                .thenAccept(decorated -> {
                    if (decorated == null) {
                        return;
                    }
                    ItineraryVO latest = loadLatestItinerary(userId, itineraryId);
                    preserveCalculatedTravelDetails(latest, decorated);
                    decorated.setId(itineraryId);
                    savedItineraryCommandService.save(userId, itineraryId, normalizedRequest, decorated);
                    log.info("background itinerary AI decoration saved, userId={}, itineraryId={}", userId, itineraryId);
                })
                .exceptionally(ex -> {
                    log.warn("background itinerary AI decoration failed, userId={}, itineraryId={}, reason={}",
                            userId, itineraryId, ex.getMessage(), ex);
                    return null;
                });
    }

    private ItineraryVO loadLatestItinerary(Long userId, Long itineraryId) {
        if (userId == null || itineraryId == null) {
            return null;
        }
        SavedItinerary entity = savedItineraryRepository.findOwned(userId, itineraryId);
        if (entity == null) {
            return null;
        }
        try {
            return savedItineraryCodec.readItinerary(entity);
        } catch (JsonProcessingException ex) {
            log.warn("failed to load latest itinerary before background AI save, userId={}, itineraryId={}, reason={}",
                    userId, itineraryId, ex.getMessage());
            return null;
        }
    }

    private void preserveCalculatedTravelDetails(ItineraryVO latest, ItineraryVO decorated) {
        if (latest == null || decorated == null) {
            return;
        }
        preserveCalculatedTravelDetails(latest.getNodes(), decorated.getNodes());
        if (latest.getOptions() == null || decorated.getOptions() == null) {
            return;
        }
        for (ItineraryOptionVO targetOption : decorated.getOptions()) {
            if (targetOption == null) {
                continue;
            }
            ItineraryOptionVO sourceOption = latest.getOptions().stream()
                    .filter(option -> option != null && Objects.equals(option.getOptionKey(), targetOption.getOptionKey()))
                    .findFirst()
                    .orElse(null);
            if (sourceOption != null) {
                preserveCalculatedTravelDetails(sourceOption.getNodes(), targetOption.getNodes());
            }
        }
    }

    private void preserveCalculatedTravelDetails(List<ItineraryNodeVO> sourceNodes, List<ItineraryNodeVO> targetNodes) {
        if (sourceNodes == null || targetNodes == null) {
            return;
        }
        for (ItineraryNodeVO target : targetNodes) {
            ItineraryNodeVO source = sourceNodes.stream()
                    .filter(candidate -> sameNode(candidate, target))
                    .findFirst()
                    .orElse(null);
            if (source == null || source.getSegmentRouteGuide() == null) {
                continue;
            }
            target.setTravelTime(source.getTravelTime());
            target.setTravelDistanceKm(source.getTravelDistanceKm());
            target.setTravelTransportMode(source.getTravelTransportMode());
            target.setDepartureTravelTime(source.getDepartureTravelTime());
            target.setDepartureDistanceKm(source.getDepartureDistanceKm());
            target.setDepartureTransportMode(source.getDepartureTransportMode());
            target.setSegmentRouteGuide(source.getSegmentRouteGuide());
            target.setRoutePathPoints(source.getRoutePathPoints() == null ? List.of() : source.getRoutePathPoints());
        }
    }

    private boolean sameNode(ItineraryNodeVO left, ItineraryNodeVO right) {
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getDayNo(), right.getDayNo())
                && Objects.equals(left.getStepOrder(), right.getStepOrder())
                && Objects.equals(left.getPoiId(), right.getPoiId());
    }
}

package com.citytrip.service.application.itinerary;

import com.citytrip.analytics.RoutePlanFactPublisher;
import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ReplanReqDTO;
import com.citytrip.model.dto.ReplanRespDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.domain.planning.ExternalPoiCandidateService;
import com.citytrip.service.domain.planning.PlanningPoiQueryService;
import com.citytrip.service.domain.policy.MaxStopsPolicy;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import com.citytrip.service.impl.PlanningOrchestrator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReplanItineraryUseCase {

    private final ItineraryRouteOptimizer routeOptimizer;
    private final PlanningPoiQueryService planningPoiQueryService;
    private final ItineraryComparisonAssembler itineraryComparisonAssembler;
    private final ItineraryAiDecorationService itineraryAiDecorationService;
    private final ExternalPoiCandidateService externalPoiCandidateService;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final ItineraryQueryService itineraryQueryService;
    private final RoutePlanFactPublisher routePlanFactPublisher;
    private final MaxStopsPolicy maxStopsPolicy;

    @Autowired
    public ReplanItineraryUseCase(ItineraryRouteOptimizer routeOptimizer,
                                  PlanningPoiQueryService planningPoiQueryService,
                                  ItineraryComparisonAssembler itineraryComparisonAssembler,
                                  ItineraryAiDecorationService itineraryAiDecorationService,
                                  ExternalPoiCandidateService externalPoiCandidateService,
                                  SavedItineraryCommandService savedItineraryCommandService,
                                  ItineraryQueryService itineraryQueryService,
                                  RoutePlanFactPublisher routePlanFactPublisher,
                                  MaxStopsPolicy maxStopsPolicy) {
        this.routeOptimizer = routeOptimizer;
        this.planningPoiQueryService = planningPoiQueryService;
        this.itineraryComparisonAssembler = itineraryComparisonAssembler;
        this.itineraryAiDecorationService = itineraryAiDecorationService;
        this.externalPoiCandidateService = externalPoiCandidateService;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.itineraryQueryService = itineraryQueryService;
        this.routePlanFactPublisher = routePlanFactPublisher;
        this.maxStopsPolicy = maxStopsPolicy;
    }

    public ReplanItineraryUseCase(ItineraryRouteOptimizer routeOptimizer,
                                  PlanningPoiQueryService planningPoiQueryService,
                                  ItineraryComparisonAssembler itineraryComparisonAssembler,
                                  ItineraryAiDecorationService itineraryAiDecorationService,
                                  SavedItineraryCommandService savedItineraryCommandService,
                                  ItineraryQueryService itineraryQueryService,
                                  RoutePlanFactPublisher routePlanFactPublisher,
                                  MaxStopsPolicy maxStopsPolicy) {
        this(
                routeOptimizer,
                planningPoiQueryService,
                itineraryComparisonAssembler,
                itineraryAiDecorationService,
                null,
                savedItineraryCommandService,
                itineraryQueryService,
                routePlanFactPublisher,
                maxStopsPolicy
        );
    }

    public ReplanRespDTO replan(Long userId, Long itineraryId, ReplanReqDTO req) {
        LocalDateTime planningStartedAt = LocalDateTime.now();
        ReplanRespDTO resp = new ReplanRespDTO();
        List<ItineraryNodeVO> currentNodes = req == null || req.getCurrentNodes() == null
                ? Collections.emptyList()
                : req.getCurrentNodes();
        if (currentNodes.isEmpty()) {
            resp.setSuccess(false);
            resp.setChanged(false);
            resp.setMessage("当前没有可重新规划的行程。");
            return resp;
        }

        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(req == null ? null : req.getOriginalReq());
        List<Poi> currentPois = planningPoiQueryService.loadOrderedPois(currentNodes);
        Set<Long> currentPoiIds = currentPois.stream().map(Poi::getId).collect(Collectors.toSet());

        List<Poi> localCandidates = safeList(planningPoiQueryService.loadPlanningPool(normalized)).stream()
                .peek(poi -> poi.setSourceType("local"))
                .toList();
        List<Poi> externalCandidates = externalPoiCandidateService == null
                ? Collections.emptyList()
                : safeList(externalPoiCandidateService.recallForReplan(currentPois, normalized, 10));
        List<Poi> candidates = mergeCandidates(localCandidates, externalCandidates);

        int maxStops = maxStopsPolicy.resolve(normalized, candidates.size());
        List<ItineraryRouteOptimizer.RouteOption> ranked = routeOptimizer.rankRoutes(candidates, normalized, maxStops);
        if (ranked.isEmpty()) {
            resp.setSuccess(false);
            resp.setChanged(false);
            resp.setMessage("当前时间窗内没有找到可执行的重排行程。");
            return resp;
        }

        String oldSignature = currentNodes.stream()
                .map(node -> String.valueOf(node.getPoiId()))
                .collect(Collectors.joining("-"));
        Set<String> excludedSignatures = new LinkedHashSet<>();
        excludedSignatures.add(oldSignature);
        if (req != null && req.getExcludedSignatures() != null) {
            req.getExcludedSignatures().stream()
                    .filter(StringUtils::hasText)
                    .map(String::trim)
                    .forEach(excludedSignatures::add);
        }

        ItineraryRouteOptimizer.RouteOption chosen = selectAlternativeRoute(
                ranked,
                currentPoiIds,
                currentNodes.size(),
                excludedSignatures
        );

        if (chosen == null) {
            resp.setSuccess(true);
            resp.setChanged(false);
            resp.setMessage("当前条件下暂无更优路线。");
            resp.setReason("在当前时间、偏好与营业状态约束下，暂时没有明显优于当前路线的新组合。");
            resp.setItinerary(itineraryQueryService.getLatest(userId));
            return resp;
        }

        ItineraryVO itinerary = itineraryComparisonAssembler.buildComparedItinerary(
                ranked,
                normalized,
                reasonMap(currentNodes),
                chosen.signature(),
                excludedSignatures
        );
        itinerary.setRecommendReason("已为你切换到一组新的候选路线，这次会优先避开刚刚看过的旧组合。");
        itineraryAiDecorationService.applyWarmTips(itinerary, normalized);
        itinerary = savedItineraryCommandService.save(userId, itineraryId, normalized, itinerary);

        resp.setSuccess(true);
        resp.setChanged(true);
        resp.setMessage("已为你换成一组新的路线方案。");
        resp.setReason("新路线会优先替换掉已经浏览过的组合，而不是简单打乱原有顺序。");
        resp.setItinerary(itinerary);

        routePlanFactPublisher.publish(
                userId,
                itinerary.getId(),
                "replan",
                normalized,
                itinerary,
                candidates.size(),
                candidates.size(),
                ranked.size(),
                maxStops,
                ranked.size(),
                itinerary.getOptions() == null ? 0 : itinerary.getOptions().size(),
                true,
                null,
                PlanningOrchestrator.ALGORITHM_VERSION,
                PlanningOrchestrator.RECALL_STRATEGY,
                planningStartedAt
        );
        return resp;
    }

    private Map<Long, String> reasonMap(List<ItineraryNodeVO> nodes) {
        Map<Long, String> map = new HashMap<>();
        for (ItineraryNodeVO node : nodes) {
            if (node.getPoiId() != null && StringUtils.hasText(node.getSysReason())) {
                map.put(node.getPoiId(), node.getSysReason());
            }
        }
        return map;
    }

    private ItineraryRouteOptimizer.RouteOption selectAlternativeRoute(List<ItineraryRouteOptimizer.RouteOption> ranked,
                                                                       Set<Long> currentPoiIds,
                                                                       int currentSize,
                                                                       Set<String> excludedSignatures) {
        List<ItineraryRouteOptimizer.RouteOption> alternatives = ranked.stream()
                .filter(option -> excludedSignatures == null || !excludedSignatures.contains(option.signature()))
                .filter(option -> containsNewPoi(option, currentPoiIds))
                .toList();
        if (alternatives.isEmpty()) {
            return null;
        }

        List<ItineraryRouteOptimizer.RouteOption> sameSize = alternatives.stream()
                .filter(option -> option.path().size() == currentSize)
                .toList();
        List<ItineraryRouteOptimizer.RouteOption> pool = sameSize.isEmpty() ? alternatives : sameSize;

        return pool.stream()
                .sorted((left, right) -> {
                    int byNewPoiCount = Integer.compare(
                            countNewPois(right, currentPoiIds),
                            countNewPois(left, currentPoiIds)
                    );
                    if (byNewPoiCount != 0) {
                        return byNewPoiCount;
                    }
                    return Double.compare(right.utility(), left.utility());
                })
                .findFirst()
                .orElse(null);
    }

    private boolean containsNewPoi(ItineraryRouteOptimizer.RouteOption option, Set<Long> currentPoiIds) {
        return option.path().stream()
                .map(Poi::getId)
                .anyMatch(id -> !currentPoiIds.contains(id));
    }

    private int countNewPois(ItineraryRouteOptimizer.RouteOption option, Set<Long> currentPoiIds) {
        return (int) option.path().stream()
                .map(Poi::getId)
                .filter(id -> !currentPoiIds.contains(id))
                .count();
    }

    private List<Poi> mergeCandidates(List<Poi> localCandidates, List<Poi> externalCandidates) {
        Map<String, Poi> deduped = new LinkedHashMap<>();
        if (localCandidates != null) {
            for (Poi poi : localCandidates) {
                if (poi == null || !StringUtils.hasText(poi.getName())) {
                    continue;
                }
                deduped.putIfAbsent(buildCandidateKey(poi), poi);
            }
        }
        if (externalCandidates != null) {
            for (Poi poi : externalCandidates) {
                if (poi == null || !StringUtils.hasText(poi.getName())) {
                    continue;
                }
                deduped.putIfAbsent(buildCandidateKey(poi), poi);
            }
        }
        return new ArrayList<>(deduped.values());
    }

    private String buildCandidateKey(Poi poi) {
        return (poi.getName() + "|" + poi.getLatitude() + "|" + poi.getLongitude()).toLowerCase();
    }

    private <T> List<T> safeList(List<T> source) {
        return source == null ? Collections.emptyList() : source;
    }
}

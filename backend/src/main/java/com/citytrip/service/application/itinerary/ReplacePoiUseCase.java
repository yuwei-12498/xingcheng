package com.citytrip.service.application.itinerary;

import com.citytrip.analytics.RoutePlanFactPublisher;
import com.citytrip.assembler.ItineraryComparisonAssembler;
import com.citytrip.common.BadRequestException;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.dto.ReplaceReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.model.vo.ItineraryVO;
import com.citytrip.service.domain.ai.ItineraryAiDecorationService;
import com.citytrip.service.domain.planning.ExternalPoiCandidateService;
import com.citytrip.service.domain.planning.PlanningPoiQueryService;
import com.citytrip.service.domain.policy.MaxStopsPolicy;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import com.citytrip.service.impl.PlanningOrchestrator;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ReplacePoiUseCase {

    private final ItineraryRouteOptimizer routeOptimizer;
    private final PlanningPoiQueryService planningPoiQueryService;
    private final ItineraryComparisonAssembler itineraryComparisonAssembler;
    private final ItineraryAiDecorationService itineraryAiDecorationService;
    private final ExternalPoiCandidateService externalPoiCandidateService;
    private final SavedItineraryCommandService savedItineraryCommandService;
    private final RoutePlanFactPublisher routePlanFactPublisher;
    private final MaxStopsPolicy maxStopsPolicy;

    public ReplacePoiUseCase(ItineraryRouteOptimizer routeOptimizer,
                             PlanningPoiQueryService planningPoiQueryService,
                             ItineraryComparisonAssembler itineraryComparisonAssembler,
                             ItineraryAiDecorationService itineraryAiDecorationService,
                             ExternalPoiCandidateService externalPoiCandidateService,
                             SavedItineraryCommandService savedItineraryCommandService,
                             RoutePlanFactPublisher routePlanFactPublisher,
                             MaxStopsPolicy maxStopsPolicy) {
        this.routeOptimizer = routeOptimizer;
        this.planningPoiQueryService = planningPoiQueryService;
        this.itineraryComparisonAssembler = itineraryComparisonAssembler;
        this.itineraryAiDecorationService = itineraryAiDecorationService;
        this.externalPoiCandidateService = externalPoiCandidateService;
        this.savedItineraryCommandService = savedItineraryCommandService;
        this.routePlanFactPublisher = routePlanFactPublisher;
        this.maxStopsPolicy = maxStopsPolicy;
    }

    public ItineraryVO replace(Long userId, Long itineraryId, Long targetPoiId, ReplaceReqDTO req) {
        LocalDateTime planningStartedAt = LocalDateTime.now();
        if (req == null || targetPoiId == null) {
            throw new BadRequestException("缺少需要替换的景点。");
        }

        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(req.getOriginalReq());
        List<ItineraryNodeVO> currentNodes = req.getCurrentNodes() == null ? Collections.emptyList() : req.getCurrentNodes();
        if (currentNodes.isEmpty()) {
            throw new BadRequestException("当前没有可替换的行程节点。");
        }

        List<Poi> currentPois = planningPoiQueryService.loadOrderedPois(currentNodes);
        Poi target = currentPois.stream()
                .filter(poi -> Objects.equals(poi.getId(), targetPoiId))
                .findFirst()
                .orElse(null);
        if (target == null) {
            throw new BadRequestException("未找到需要替换的目标景点。");
        }

        Set<Long> currentIds = currentPois.stream().map(Poi::getId).filter(Objects::nonNull).collect(Collectors.toSet());
        List<Poi> localPool = safeList(planningPoiQueryService.loadPlanningPool(normalized)).stream()
                .filter(poi -> !currentIds.contains(poi.getId()))
                .peek(poi -> poi.setSourceType("local"))
                .toList();
        List<Poi> externalPool = safeList(
                externalPoiCandidateService == null
                        ? null
                        : externalPoiCandidateService.recallForReplacement(target, normalized, 8)
        ).stream()
                .filter(poi -> !currentIds.contains(poi.getId()))
                .toList();

        List<Poi> pool = mergeCandidates(localPool, externalPool).stream()
                .sorted((left, right) -> Double.compare(
                        routeOptimizer.replacementScore(target, right),
                        routeOptimizer.replacementScore(target, left)
                ))
                .limit(8)
                .toList();

        ItineraryRouteOptimizer.RouteOption best = null;
        Poi chosen = null;
        for (Poi replacement : pool) {
            List<Poi> replaced = currentPois.stream()
                    .map(poi -> Objects.equals(poi.getId(), targetPoiId) ? replacement : poi)
                    .collect(Collectors.toList());
            List<Poi> prepared = routeOptimizer.prepareCandidates(replaced, normalized, false);
            ItineraryRouteOptimizer.RouteOption candidate = routeOptimizer.bestRoute(
                    prepared,
                    normalized,
                    maxStopsPolicy.resolve(normalized, prepared.size())
            );
            if (isBetter(candidate, best)) {
                best = candidate;
                chosen = replacement;
            }
        }

        if (best == null || best.path().isEmpty()) {
            throw new BadRequestException("未找到更合适的替换方案。");
        }

        Map<Long, String> reasons = reasonMap(currentNodes);
        reasons.remove(targetPoiId);

        ItineraryVO itinerary = itineraryComparisonAssembler.buildComparedItinerary(
                List.of(best),
                normalized,
                reasons,
                null,
                Collections.emptySet()
        );
        applyReplacementReason(itinerary, target, chosen);
        itinerary.setRecommendReason("已围绕新的点位重新计算路线，并尽量保留原方案的顺路性和偏好匹配。");
        itineraryAiDecorationService.applyWarmTips(itinerary, normalized);

        ItineraryVO saved = savedItineraryCommandService.save(userId, itineraryId, normalized, itinerary);
        routePlanFactPublisher.publish(
                userId,
                saved.getId(),
                "replace",
                normalized,
                saved,
                currentPois.size(),
                pool.size(),
                best.path().size(),
                best.path().size(),
                1,
                saved.getOptions() == null ? 0 : saved.getOptions().size(),
                true,
                null,
                PlanningOrchestrator.ALGORITHM_VERSION,
                PlanningOrchestrator.RECALL_STRATEGY,
                planningStartedAt
        );
        return saved;
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

    private boolean isBetter(ItineraryRouteOptimizer.RouteOption candidate,
                             ItineraryRouteOptimizer.RouteOption current) {
        if (candidate == null || candidate.path().isEmpty()) {
            return false;
        }
        if (current == null || current.path().isEmpty()) {
            return true;
        }
        int byScore = Double.compare(candidate.utility(), current.utility());
        return byScore > 0 || (byScore == 0 && candidate.path().size() > current.path().size());
    }

    private void applyReplacementReason(ItineraryVO itinerary, Poi oldPoi, Poi newPoi) {
        if (itinerary == null || itinerary.getNodes() == null || oldPoi == null || newPoi == null) {
            return;
        }
        for (ItineraryNodeVO node : itinerary.getNodes()) {
            if (Objects.equals(node.getPoiId(), newPoi.getId())) {
                node.setSysReason("已将 " + oldPoi.getName() + " 替换为更适合当前路线的相近点位。");
                node.setSourceType(newPoi.getSourceType());
            }
        }
        if (itinerary.getOptions() == null) {
            return;
        }
        itinerary.getOptions().forEach(option -> {
            if (option.getNodes() == null) {
                return;
            }
            option.getNodes().forEach(node -> {
                if (Objects.equals(node.getPoiId(), newPoi.getId())) {
                    node.setSysReason("已将 " + oldPoi.getName() + " 替换为更适合当前路线的相近点位。");
                    node.setSourceType(newPoi.getSourceType());
                }
            });
        });
    }

    private List<Poi> mergeCandidates(List<Poi> localPool, List<Poi> externalPool) {
        Map<String, Poi> deduped = new LinkedHashMap<>();
        if (localPool != null) {
            for (Poi poi : localPool) {
                if (poi == null || !StringUtils.hasText(poi.getName())) {
                    continue;
                }
                deduped.putIfAbsent(buildCandidateKey(poi), poi);
            }
        }
        if (externalPool != null) {
            for (Poi poi : externalPool) {
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

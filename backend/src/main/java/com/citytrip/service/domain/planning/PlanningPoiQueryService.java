package com.citytrip.service.domain.planning;

import com.citytrip.mapper.PoiMapper;
import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.service.impl.ItineraryRouteOptimizer;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

@Service
public class PlanningPoiQueryService {
    private static final int PLANNING_POOL_FETCH_LIMIT = 240;

    private final PoiMapper poiMapper;
    private final ItineraryRouteOptimizer routeOptimizer;

    public PlanningPoiQueryService(PoiMapper poiMapper, ItineraryRouteOptimizer routeOptimizer) {
        this.poiMapper = poiMapper;
        this.routeOptimizer = routeOptimizer;
    }

    public List<Poi> loadPlanningPool(GenerateReqDTO req) {
        GenerateReqDTO normalized = routeOptimizer.normalizeRequest(req);
        boolean rainy = normalized != null && Boolean.TRUE.equals(normalized.getIsRainy());
        String walkingLevel = normalized == null ? null : normalized.getWalkingLevel();
        boolean explicitCity = req != null
                && (StringUtils.hasText(req.getCityCode()) || StringUtils.hasText(req.getCityName()));
        List<Poi> raw = explicitCity
                ? poiMapper.selectPlanningCandidates(
                rainy,
                walkingLevel,
                normalized == null ? null : normalized.getCityCode(),
                normalized == null ? null : normalized.getCityName(),
                PLANNING_POOL_FETCH_LIMIT
        )
                : poiMapper.selectPlanningCandidates(
                rainy,
                walkingLevel,
                PLANNING_POOL_FETCH_LIMIT
        );
        if (raw != null) {
            raw.stream()
                    .filter(Objects::nonNull)
                    .forEach(poi -> poi.setSourceType("local"));
        }
        return routeOptimizer.prepareCandidates(raw, normalized, true);
    }

    public List<Poi> loadOrderedPois(List<ItineraryNodeVO> nodes) {
        if (nodes == null || nodes.isEmpty()) {
            return Collections.emptyList();
        }
        List<Long> ids = nodes.stream()
                .map(ItineraryNodeVO::getPoiId)
                .filter(Objects::nonNull)
                .toList();
        return loadOrderedPoisByIds(ids);
    }

    public List<Poi> loadOrderedPoisByIds(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return Collections.emptyList();
        }
        List<Poi> fetched = poiMapper.selectBatchIds(ids);
        if (fetched == null || fetched.isEmpty()) {
            return Collections.emptyList();
        }

        Map<Long, Poi> byId = fetched.stream()
                .filter(Objects::nonNull)
                .filter(poi -> poi.getId() != null)
                .peek(poi -> poi.setSourceType("local"))
                .collect(Collectors.toMap(Poi::getId, poi -> poi, (left, right) -> left));

        List<Poi> ordered = new ArrayList<>(ids.size());
        for (Long id : ids) {
            Poi poi = byId.get(id);
            if (poi != null) {
                ordered.add(poi);
            }
        }
        return ordered;
    }
}

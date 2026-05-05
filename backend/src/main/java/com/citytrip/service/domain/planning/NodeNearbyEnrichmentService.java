package com.citytrip.service.domain.planning;

import com.citytrip.config.GeoSearchProperties;
import com.citytrip.model.vo.ItineraryNodeVO;
import com.citytrip.service.geo.GeoPoint;
import com.citytrip.service.geo.GeoPoiCandidate;
import com.citytrip.service.geo.GeoSearchService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

@Service
public class NodeNearbyEnrichmentService {

    private final GeoSearchService geoSearchService;
    private final GeoSearchProperties geoSearchProperties;
    private final Executor itineraryPlanningExecutor;

    public NodeNearbyEnrichmentService(GeoSearchService geoSearchService,
                                       GeoSearchProperties geoSearchProperties,
                                       @Qualifier("itineraryPlanningExecutor") Executor itineraryPlanningExecutor) {
        this.geoSearchService = geoSearchService;
        this.geoSearchProperties = geoSearchProperties;
        this.itineraryPlanningExecutor = itineraryPlanningExecutor;
    }

    public void enrich(List<ItineraryNodeVO> nodes, String cityName) {
        if (nodes == null || nodes.isEmpty()) {
            return;
        }

        int timeoutMs = Math.max(120, geoSearchProperties.getNodeNearbyTimeoutMs());
        List<CompletableFuture<Void>> futures = new ArrayList<>();
        for (ItineraryNodeVO node : nodes) {
            if (node == null || !StringUtils.hasText(node.getPoiName())) {
                continue;
            }
            CompletableFuture<Void> future = CompletableFuture.runAsync(
                    () -> enrichSingleNode(node, cityName),
                    itineraryPlanningExecutor
            ).orTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                    .exceptionally(ex -> null);
            futures.add(future);
        }
        if (futures.isEmpty()) {
            return;
        }
        CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                .exceptionally(ex -> null)
                .join();
    }

    private void enrichSingleNode(ItineraryNodeVO node, String cityName) {
        GeoPoint center = resolveCenter(node, cityName);
        if (center == null || !center.valid()) {
            return;
        }
        int radiusMeters = Math.max(500, geoSearchProperties.getNearbyRadiusMeters());
        node.setNearbyHotels(extractTopNames(geoSearchService.searchNearby(center, cityName, "酒店", radiusMeters, 6), 3));
        node.setNearbyFoods(extractTopNames(geoSearchService.searchNearby(center, cityName, "餐饮", radiusMeters, 6), 3));
        node.setNearbyShops(extractTopNames(geoSearchService.searchNearby(center, cityName, "商铺", radiusMeters, 6), 3));
    }

    private GeoPoint resolveCenter(ItineraryNodeVO node, String cityName) {
        if (node.getLatitude() != null && node.getLongitude() != null) {
            GeoPoint point = new GeoPoint(node.getLatitude(), node.getLongitude());
            if (point.valid()) {
                return point;
            }
        }
        String keyword = node.getPoiName();
        if (StringUtils.hasText(node.getDistrict())) {
            keyword = node.getDistrict() + " " + keyword;
        }
        return geoSearchService.geocode(keyword, cityName).orElse(null);
    }

    private List<String> extractTopNames(List<GeoPoiCandidate> candidates, int limit) {
        if (candidates == null || candidates.isEmpty()) {
            return Collections.emptyList();
        }
        Set<String> names = new LinkedHashSet<>();
        for (GeoPoiCandidate candidate : candidates) {
            if (candidate == null || !StringUtils.hasText(candidate.getName())) {
                continue;
            }
            names.add(candidate.getName().trim());
            if (names.size() >= limit) {
                break;
            }
        }
        return new ArrayList<>(names);
    }
}


package com.citytrip.service.domain.routing;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.impl.ItineraryRouteOptimizer;

import java.util.List;
import java.util.Objects;

/**
 * Transitional route-search engine for phase 1.
 *
 * <p>The public optimizer facade delegates through this interface now, so later
 * DP and Beam implementations can replace this adapter without changing
 * application callers.</p>
 */
public class LegacyRouteSearchEngine implements RouteSearchEngine {

    private final RouteRanker routeRanker;

    public LegacyRouteSearchEngine(RouteRanker routeRanker) {
        this.routeRanker = Objects.requireNonNull(routeRanker, "routeRanker");
    }

    @Override
    public List<ItineraryRouteOptimizer.RouteOption> rankRoutes(List<Poi> candidates,
                                                                GenerateReqDTO request,
                                                                int maxStops) {
        return routeRanker.rankRoutes(candidates, request, maxStops);
    }

    @FunctionalInterface
    public interface RouteRanker {
        List<ItineraryRouteOptimizer.RouteOption> rankRoutes(List<Poi> candidates,
                                                             GenerateReqDTO request,
                                                             int maxStops);
    }
}
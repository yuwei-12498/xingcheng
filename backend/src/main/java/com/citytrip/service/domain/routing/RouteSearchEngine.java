package com.citytrip.service.domain.routing;

import com.citytrip.model.dto.GenerateReqDTO;
import com.citytrip.model.entity.Poi;
import com.citytrip.service.impl.ItineraryRouteOptimizer;

import java.util.List;

public interface RouteSearchEngine {
    List<ItineraryRouteOptimizer.RouteOption> rankRoutes(List<Poi> candidates, GenerateReqDTO request, int maxStops);
}
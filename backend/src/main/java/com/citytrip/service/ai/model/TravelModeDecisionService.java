package com.citytrip.service.ai.model;

import com.citytrip.service.TravelTimeService;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

@Service
public class TravelModeDecisionService {

    public TravelTimeService.TravelLegEstimate pickBest(List<TravelTimeService.TravelLegEstimate> estimates) {
        List<TravelTimeService.TravelLegEstimate> candidates = estimates.stream()
                .filter(Objects::nonNull)
                .filter(estimate -> estimate.estimatedMinutes() > 0)
                .toList();
        TravelTimeService.TravelLegEstimate walking = best(candidates, this::isWalking);
        if (isReasonableWalkingLeg(walking)) {
            return walking;
        }

        TravelTimeService.TravelLegEstimate cycling = best(candidates, this::isCycling);
        if (isReasonableCyclingLeg(cycling, walking)) {
            return cycling;
        }

        TravelTimeService.TravelLegEstimate publicTransit = best(candidates, this::isPublicTransit);
        TravelTimeService.TravelLegEstimate taxi = best(candidates, this::isTaxi);
        if (isReasonablePublicTransitLeg(publicTransit, taxi, cycling)) {
            return publicTransit;
        }

        return candidates.stream()
                .min(Comparator.comparingDouble(this::score))
                .orElseThrow();
    }

    private TravelTimeService.TravelLegEstimate best(List<TravelTimeService.TravelLegEstimate> estimates,
                                                     java.util.function.Predicate<TravelTimeService.TravelLegEstimate> predicate) {
        return estimates.stream()
                .filter(predicate)
                .min(Comparator.comparingInt(TravelTimeService.TravelLegEstimate::estimatedMinutes))
                .orElse(null);
    }

    private double score(TravelTimeService.TravelLegEstimate estimate) {
        double timeScore = estimate.estimatedMinutes();
        if (isWalking(estimate)) {
            return timeScore;
        }
        if (isCycling(estimate)) {
            return timeScore + 1D;
        }
        if (isPublicTransit(estimate)) {
            return timeScore + 5D;
        }
        if (isTaxi(estimate)) {
            return timeScore + 20D;
        }
        return timeScore + 12D;
    }

    private boolean isReasonableWalkingLeg(TravelTimeService.TravelLegEstimate estimate) {
        if (estimate == null) {
            return false;
        }
        double distanceKm = estimate.estimatedDistanceKm() == null
                ? 0D
                : estimate.estimatedDistanceKm().doubleValue();
        return estimate.estimatedMinutes() <= 20 && (distanceKm <= 0D || distanceKm <= 1.5D);
    }

    private boolean isReasonableCyclingLeg(TravelTimeService.TravelLegEstimate cycling,
                                           TravelTimeService.TravelLegEstimate walking) {
        if (cycling == null) {
            return false;
        }
        double distanceKm = cycling.estimatedDistanceKm() == null
                ? 0D
                : cycling.estimatedDistanceKm().doubleValue();
        boolean withinComfortBand = distanceKm <= 0D
                ? cycling.estimatedMinutes() <= 18
                : distanceKm >= 1.2D && distanceKm <= 4.0D && cycling.estimatedMinutes() <= 25;
        if (!withinComfortBand) {
            return false;
        }
        return walking == null || cycling.estimatedMinutes() + 6 <= walking.estimatedMinutes();
    }

    private boolean isReasonablePublicTransitLeg(TravelTimeService.TravelLegEstimate publicTransit,
                                                 TravelTimeService.TravelLegEstimate taxi,
                                                 TravelTimeService.TravelLegEstimate cycling) {
        if (publicTransit == null) {
            return false;
        }
        if (cycling != null && cycling.estimatedMinutes() + 4 < publicTransit.estimatedMinutes()) {
            return false;
        }
        if (taxi == null) {
            return true;
        }
        int publicMinutes = publicTransit.estimatedMinutes();
        int taxiMinutes = taxi.estimatedMinutes();
        return publicMinutes <= Math.max(taxiMinutes + 12, Math.ceil(taxiMinutes * 1.6D));
    }

    private boolean isWalking(TravelTimeService.TravelLegEstimate estimate) {
        String mode = normalizeMode(estimate);
        return mode.contains("walk") || mode.contains("\u6b65\u884c");
    }

    private boolean isPublicTransit(TravelTimeService.TravelLegEstimate estimate) {
        String mode = normalizeMode(estimate);
        return mode.contains("transit")
                || mode.contains("public")
                || mode.contains("bus")
                || mode.contains("metro")
                || mode.contains("subway")
                || mode.contains("\u516c\u4ea4")
                || mode.contains("\u5730\u94c1");
    }

    private boolean isCycling(TravelTimeService.TravelLegEstimate estimate) {
        String mode = normalizeMode(estimate);
        return mode.contains("bike")
                || mode.contains("bicycle")
                || mode.contains("cycling")
                || mode.contains("ride")
                || mode.contains("\u9a91\u884c");
    }

    private boolean isTaxi(TravelTimeService.TravelLegEstimate estimate) {
        String mode = normalizeMode(estimate);
        return mode.contains("taxi")
                || mode.contains("cab")
                || mode.contains("drive")
                || mode.contains("driving")
                || mode.contains("car")
                || mode.contains("\u6253\u8f66")
                || mode.contains("\u9a7e\u8f66")
                || mode.contains("\u51fa\u79df");
    }

    private String normalizeMode(TravelTimeService.TravelLegEstimate estimate) {
        return estimate == null || estimate.transportMode() == null
                ? ""
                : estimate.transportMode().trim().toLowerCase(Locale.ROOT);
    }
}

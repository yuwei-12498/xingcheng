package com.citytrip.service.domain.policy;

import com.citytrip.model.dto.GenerateReqDTO;
import org.springframework.stereotype.Component;

@Component
public class MaxStopsPolicy {

    private static final int DEFAULT_MAX_STOPS = 6;

    public int resolve(GenerateReqDTO req, int candidateSize) {
        if (candidateSize <= 0) {
            return 0;
        }

        int requested = DEFAULT_MAX_STOPS;
        double tripDays = req == null || req.getTripDays() == null ? 1.0D : req.getTripDays();
        if (tripDays >= 2.0D) {
            requested = DEFAULT_MAX_STOPS + 2;
        } else if (tripDays <= 0.5D) {
            requested = Math.max(4, DEFAULT_MAX_STOPS - 2);
        }
        return Math.min(requested, candidateSize);
    }
}

package com.optrip.server.dto;

import java.util.List;

public record RouteLegResponse(
        String travelMode,
        long durationSeconds,
        int distanceMeters,
        String encodedPolyline,
        List<TransitStep> transitSteps
) {
    public record TransitStep(
            String vehicleType,
            String lineName,
            int stopCount
    ) {
    }
}

package com.optrip.server.dto;

import java.util.List;

public record RouteLegResponse(
        String travelMode,
        long durationSeconds,
        int distanceMeters,
        String encodedPolyline,
        List<TransitStep> transitSteps,
        List<RouteStep> steps
) {
    public record TransitStep(
            String vehicleType,
            String lineName,
            int stopCount
    ) {
    }

    public record RouteStep(
            String travelMode,
            long durationSeconds,
            int distanceMeters,
            String vehicleType,
            String lineName,
            String departureStop,
            String arrivalStop,
            int stopCount
    ) {
    }
}

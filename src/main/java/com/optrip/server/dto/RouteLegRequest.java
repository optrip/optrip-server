package com.optrip.server.dto;

public record RouteLegRequest(
        double originLatitude,
        double originLongitude,
        double destinationLatitude,
        double destinationLongitude,
        String travelMode
) {
}

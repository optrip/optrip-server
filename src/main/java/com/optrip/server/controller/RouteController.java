package com.optrip.server.controller;

import com.optrip.server.client.google.GoogleRoutesClient;
import com.optrip.server.dto.RouteLegRequest;
import com.optrip.server.dto.RouteLegResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@Tag(name = "routes", description = "Google Routes를 이용한 장소 사이 이동 경로")
@RestController
@RequestMapping("/api/routes")
public class RouteController {

    private final GoogleRoutesClient googleRoutesClient;

    public RouteController(GoogleRoutesClient googleRoutesClient) {
        this.googleRoutesClient = googleRoutesClient;
    }

    @Operation(summary = "두 장소 사이의 대중교통 또는 자동차 경로")
    @PostMapping("/leg")
    public RouteLegResponse leg(@RequestBody RouteLegRequest request) {
        validateCoordinates(request);
        try {
            return googleRoutesClient.computeLeg(request);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage(), e);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "경로 정보를 가져오지 못했습니다.", e);
        }
    }

    private void validateCoordinates(RouteLegRequest request) {
        if (!validLatitude(request.originLatitude())
                || !validLatitude(request.destinationLatitude())
                || !validLongitude(request.originLongitude())
                || !validLongitude(request.destinationLongitude())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "위도 또는 경도 값이 올바르지 않습니다.");
        }
    }

    private boolean validLatitude(double value) {
        return Double.isFinite(value) && value >= -90 && value <= 90;
    }

    private boolean validLongitude(double value) {
        return Double.isFinite(value) && value >= -180 && value <= 180;
    }
}

package com.optrip.server.config;

import com.optrip.server.client.tour.TourApiProperties;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class AdminTokenInterceptor implements HandlerInterceptor {

    private final TourApiProperties properties;

    public AdminTokenInterceptor(TourApiProperties properties) {
        this.properties = properties;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        String expected = properties.adminToken();
        if (expected == null || expected.isBlank()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "ADMIN_TOKEN 이 설정되지 않았습니다");
        }
        if (!expected.equals(request.getHeader("X-Admin-Token"))) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "잘못된 관리자 토큰");
        }
        return true;
    }
}

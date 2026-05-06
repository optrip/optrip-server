package com.optrip.server.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

// @Configuration → "이 파일은 서버 설정 파일이에요" 라고 Spring에 알려주는 표시
// WebMvcConfigurer → Spring MVC 설정을 커스터마이징할 수 있게 해주는 인터페이스
@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")       // /api/ 로 시작하는 모든 경로에 적용
                .allowedOrigins("*")         // 개발 중엔 모든 주소 허용
                .allowedMethods("GET", "POST", "PUT", "DELETE")
                .allowedHeaders("*");
        // 배포 직전에 "*" → "https://optrip.github.io" 로 변경할 예정
    }
}
package com.optrip.server.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

// Fly.io 가 주입하는 DATABASE_URL(postgres://user:pass@host:port/db?...)을
// Spring 이 이해하는 spring.datasource.* 프로퍼티로 변환한다.
// DATABASE_URL 이 없으면 아무것도 하지 않는다 (로컬은 application-local.yml 로 직접 설정).
public class DatabaseUrlEnvironmentPostProcessor implements EnvironmentPostProcessor {

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        String databaseUrl = environment.getProperty("DATABASE_URL");
        if (databaseUrl == null || databaseUrl.isBlank()) {
            return;
        }
        // 이미 명시적으로 datasource url 이 설정돼 있으면 존중한다
        if (environment.getProperty("spring.datasource.url") != null) {
            return;
        }

        URI uri = URI.create(databaseUrl);
        String[] userInfo = uri.getUserInfo() != null ? uri.getUserInfo().split(":", 2) : new String[0];
        int port = uri.getPort() != -1 ? uri.getPort() : 5432;
        String query = uri.getRawQuery() != null ? "?" + uri.getRawQuery() : "";

        Map<String, Object> props = new HashMap<>();
        props.put("spring.datasource.url",
                "jdbc:postgresql://" + uri.getHost() + ":" + port + uri.getRawPath() + query);
        if (userInfo.length > 0) {
            props.put("spring.datasource.username", userInfo[0]);
        }
        if (userInfo.length > 1) {
            props.put("spring.datasource.password", userInfo[1]);
        }
        environment.getPropertySources().addFirst(new MapPropertySource("flyDatabaseUrl", props));
    }
}

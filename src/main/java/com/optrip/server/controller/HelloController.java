package com.optrip.server.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

// @RestController → JSON을 반환하는 컨트롤러. @Controller + @ResponseBody 합친 것
// @RequestMapping → 이 컨트롤러의 모든 경로 앞에 "/api" 붙음
@RestController
@RequestMapping("/api")
public class HelloController {

    // @GetMapping → GET 방식 HTTP 요청을 받음
    // 전체 경로: GET /api/hello
    @GetMapping("/hello")
    public ResponseEntity<Map<String, String>> hello() {
        // ResponseEntity → HTTP 상태코드(200, 404 등) + 데이터를 함께 반환
        // Map.of() → 간단한 JSON 만들기. {"message": "..."}
        return ResponseEntity.ok(
                Map.of("message", "optrip server is running!!!")
        );
    }
}
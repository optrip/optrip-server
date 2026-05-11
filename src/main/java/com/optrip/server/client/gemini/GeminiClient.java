package com.optrip.server.client.gemini;

import java.util.Map;

// Gemini 호출 추상화. 프롬프트 + JSON Schema 를 주면 지정한 타입으로 역직렬화해서 돌려준다.
// 서비스 레이어는 이 인터페이스만 알고, HTTP/Gemini 세부 사항은 모른다.
public interface GeminiClient {

    /**
     * @param prompt          모델에게 전달할 사용자 메시지
     * @param responseSchema  Gemini responseSchema 형식의 JSON Schema (OBJECT/STRING/... 대문자 type)
     * @param responseType    응답 JSON 을 매핑할 클래스
     */
    <T> T generateStructured(String prompt, Map<String, Object> responseSchema, Class<T> responseType);
}

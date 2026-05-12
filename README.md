# optrip-server

> AI 기반 여행 코스 추천 앱 **optrip**의 백엔드 서버

---

## 스택

| 항목 | 기술 |
|------|------|
| Language | Java 21 |
| Framework | Spring Boot 3.5.x |
| Build Tool | Gradle |
| Database | 추후 추가 예정 |

---

## 시작하기

### 사전 요구사항

- Java 21+
- IntelliJ IDEA (권장)
- Git

### 설치 및 실행

```bash
# 레포지토리 클론
git clone https://github.com/optrip/optrip-server.git
cd optrip-server

# Gemini API 키 설정 (아래 "Gemini API 키 설정" 섹션 참고)

# 실행 (local 프로파일 활성화)
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

서버가 정상 실행되면 `http://localhost:8080` 에서 접근 가능합니다.

### Gemini API 키 설정

API 키는 환경변수로 주입됩니다. 키 자체는 절대 커밋하지 마세요.

#### 로컬 개발

`src/main/resources/application-local.yml` 파일을 만들고 본인 키를 넣습니다. 이 파일은 `.gitignore`에 등록되어 있어 커밋되지 않습니다.

```yaml
gemini:
  api-key: YOUR_GEMINI_API_KEY
```

실행 시 `local` 프로파일을 활성화하면 위 값이 적용됩니다.

```bash
SPRING_PROFILES_ACTIVE=local ./gradlew bootRun
```

IntelliJ에서 실행할 경우, Run Configuration의 **Environment variables** 에 `SPRING_PROFILES_ACTIVE=local` 을 추가하세요.

키는 [Google AI Studio](https://aistudio.google.com/apikey) 에서 발급받을 수 있습니다.

### 동작 확인

```bash
curl http://localhost:8080/api/hello
# {"message":"optrip server is running!"}
```

---

## 디렉토리 구조

```
optrip-server/
├── src/
│   ├── main/
│   │   ├── java/com/optrip/server/
│   │   │   ├── ServerApplication.java     # 앱 진입점
│   │   │   ├── config/
│   │   │   │   └── CorsConfig.java        # CORS 설정 (프론트 연동 허용)
│   │   │   └── controller/
│   │   │       └── HelloController.java   # API 엔드포인트
│   │   └── resources/
│   │       └── application.yml            # 서버 환경 설정
└── build.gradle                           # 의존성(라이브러리) 관리
```

---

## API 명세

### 현재 엔드포인트

| 메서드 | 경로 | 설명 | 상태 |
|--------|------|------|------|
| GET | `/api/hello` | 서버 상태 확인 | ✅ 완료 |

### 개발 예정 엔드포인트

| 메서드 | 경로 | 설명 | 상태 |
|--------|------|------|------|
| POST | `/api/recommend` | 질문 답변 기반 여행지 추천 | 🔧 개발 예정 |
| POST | `/api/trips` | 확정 코스 저장 | 🔧 개발 예정 |
| GET | `/api/trips` | 내 여행 목록 조회 | 🔧 개발 예정 |
| PUT | `/api/users/{id}` | 사용자 정보 수정 | 🔧 개발 예정 |

### 응답 형식

```json
{
  "message": "응답 내용"
}
```

---

## 프론트엔드 연동

프론트엔드 레포: [optrip-app](https://github.com/optrip/optrip-app)

| 항목 | 값 |
|------|-----|
| 백엔드 Base URL (로컬) | `http://localhost:8080` |
| 모든 API prefix | `/api/` |
| 응답 형식 | JSON |
| CORS 허용 | 개발 중 전체 허용 (`*`) |

**React Query 호출 예시 (프론트 측)**

```typescript
import { useQuery } from '@tanstack/react-query';

function Example() {
  const { data, isLoading } = useQuery({
    queryKey: ['hello'],
    queryFn: async () =>
      fetch('http://localhost:8080/api/hello').then((r) => r.json()),
  });
}
```

---

## 트러블슈팅

| 증상 | 해결 |
|------|------|
| 포트 8080 이미 사용 중 | `application.yml`에서 `server.port` 변경 |
| CORS 에러 | `CorsConfig.java`의 허용 주소 확인 |
| Gradle 빌드 실패 | `./gradlew clean build` 후 재시도 |
| 변경사항이 반영 안 됨 | IntelliJ에서 서버 재시작 |

---

## 팀 구성

| 역할 | 담당 |
|------|------|
| 프론트엔드 | [@팀원계정](https://github.com/팀원계정) |
| 백엔드 (Spring Boot) | [@본인계정](https://github.com/본인계정) |
    
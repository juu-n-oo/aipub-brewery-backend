# dockerizer-backend API 아키텍처

> 작성일: 2026-06-01  
> 최종 수정: 2026-06-02  
> 범위: 백엔드 REST API 구조, 인증 필터

---

## 1. 모듈 구조

```
io.ten1010.dockerizerbackend/
  aipub/                          ← AIPub 연동 (인증)
    config/
      AipubProperties              AIPub base URL 설정
      AipubRestClientConfiguration  Token Introspection용 RestClient
      SecurityConfiguration         Spring Security 설정
    dto/
      SelfSubjectReviewResponse     selfsubjectreviews 응답 DTO
    filter/
      AipubAuthenticationFilter     Token Introspection 인증 필터
  dockerfile/                     ← Dockerfile CRUD
  imagebuild/                     ← ImageBuild CR 관리 + 빌드 로그
  volume/                         ← AIPubVolume 조회 + 파일 탐색
  registry/                       ← NGC, HuggingFace 레지스트리 프록시
  common/                         ← 공통 설정 (K8s, OpenAPI, Properties)
```

## 2. 인증 — Token Introspection

### AipubAuthenticationFilter

`OncePerRequestFilter`를 상속하며, 매 요청마다 AIPub에 토큰 유효성 검증을 위임한다.

```
요청 수신
  → AIPUB_ACCESS_COOKIE 추출
  → 쿠키 없음 → SecurityContext 미설정 → Spring Security가 401 반환
  → 쿠키 있음 → AIPub selfsubjectreviews 호출 (클러스터 내부 HTTP)
    → isAuthenticated: true → username, roles 추출 → SecurityContext 설정
    → isAuthenticated: false 또는 예외 → SecurityContext 미설정 → 401
```

- JWT secret key 공유 없음 — 인증 주체는 AIPub에 일원화
- 캐싱 없음 — 매 요청마다 AIPub 호출
- AIPub이 잠금/휴면/탈퇴 등 사용자 상태까지 체크

### SecurityFilterChain

```java
permitAll:  api-docs, swagger-ui, h2-console, actuator, mcp
authenticated: 나머지 모든 요청
```

- CSRF 비활성화, stateless 세션
- AipubAuthenticationFilter → UsernamePasswordAuthenticationFilter 앞에 등록
- 인증 엔드포인트(login/logout/selfsubjectreviews)는 AIPub Ingress에서 AIPub으로 직접 라우팅되므로 dockerizer backend에 도달하지 않음
- Dockerizer는 자체 Ingress를 생성하지 않으며, AIPub Ingress(`aipub-backend-adapter`)에 path가 추가되어 라우팅됨

## 3. REST API

모든 엔드포인트 접두사: `/api/v1alpha1`

### Dockerfile (`/dockerfiles`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/dockerfiles` | 생성 (forbidden instructions 검증, 리비전 v1 생성) |
| GET | `/dockerfiles?project=&username=` | 목록 |
| GET | `/dockerfiles/{id}` | 단건 |
| PUT | `/dockerfiles/{id}` | 수정 (저장 시 새 리비전 append) |
| DELETE | `/dockerfiles/{id}` | 삭제 |

### Dockerfile Revision (`/dockerfiles/{id}/revisions`)

매 저장마다 `dockerfile_revisions`에 append-only 로 버전이 쌓인다. 이력 조회 · diff · 롤백 지원.

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/dockerfiles/{id}/revisions` | 리비전(버전) 목록 |
| GET | `/dockerfiles/{id}/revisions/{version}` | 특정 리비전 단건 (content/baseImage 포함) |
| POST | `/dockerfiles/{id}/revisions/{version}/rollback` | 해당 리비전 내용으로 롤백 (새 리비전으로 append) |

### Build Context Files (`/dockerfiles/{dockerfileId}/files`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/dockerfiles/{dockerfileId}/files` | 파일 목록 |
| POST | `/dockerfiles/{dockerfileId}/files?targetPath=` | 파일 업로드 (multipart) |
| DELETE | `/dockerfiles/{dockerfileId}/files/{fileId}` | 파일 삭제 |

### ImageBuild (`/builds`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| POST | `/builds` | 빌드 트리거 → ImageBuild CR 생성 (HTTP 202) |
| GET | `/builds?project=` | 빌드 목록 |
| GET | `/builds/{namespace}/{name}` | 빌드 상태 |
| GET | `/builds/{namespace}/{name}/logs` | 빌드 로그 (text/plain) |
| GET | `/builds/{namespace}/{name}/logs/stream` | SSE 실시간 로그 스트림 |

### Volume (`/volumes`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/volumes/{namespace}` | AIPubVolume 목록 |
| GET | `/volumes/{namespace}/{volumeName}/browse?path=` | 볼륨 파일 탐색 (helper Pod `exec ls`) |
| POST | `/volumes/{namespace}/{volumeName}/upload?path=` | 볼륨 지정 경로에 파일 업로드 (multipart). PVC 를 RW 로 마운트한 helper Pod 에 `exec dd of=<경로>` 로 multipart 스트림을 그대로 기록(로컬 디스크 미경유). 응답으로 갱신된 디렉토리 목록 반환 |

### Registry (`/registries`)

| 메서드 | 경로 | 설명 |
|--------|------|------|
| GET | `/registries/ngc/images?query=&page=&pageSize=` | NGC 이미지 검색 |
| GET | `/registries/ngc/images/{org}/{repo}/tags` | NGC 태그 |
| GET | `/registries/huggingface/images?query=&page=&pageSize=` | HuggingFace 검색 |
| GET | `/registries/huggingface/images/{repo}/tags` | HuggingFace 태그 |

## 4. K8s 직접 통신 (ServiceAccount)

| 리소스 | API Group | 용도 |
|--------|-----------|------|
| ImageBuild CR | `dockerizer.aipub.ten1010.io/v1alpha1` | 빌드 CR 생성/조회 |
| AIPubVolume CR | `aipub.ten1010.io/v1alpha1` | 볼륨 목록 조회 |
| Pod / Pod/log | `core/v1` | 빌드 Pod 상태/로그 조회 |
| Pod/exec | `core/v1` | 볼륨 파일 탐색 (`ls`) 및 업로드 (`dd`, stdin 스트림) — helper Pod exec |

## 5. 설정

### application.yaml

```yaml
dockerizer:
  aipub:
    base-url: ${DOCKERIZER_AIPUB_BASE_URL:http://localhost:9090}
```

### Helm

- `DOCKERIZER_AIPUB_BASE_URL`: env-configmap.yaml에서 주입
- 기본값: `http://aipub-backend-gateway.aipub.svc.cluster.local:8080`

### Ingress 라우팅

Dockerizer는 자체 Ingress를 생성하지 않는다. `install.sh`가 기존 AIPub Ingress(`aipub-backend-adapter`)에 `kubectl patch`로 다음 path를 추가한다:

| 경로 | 대상 서비스 |
|------|-----------|
| `/api/v1alpha1/dockerfiles` | dockerizer-backend :8080 |
| `/api/v1alpha1/builds` | dockerizer-backend :8080 |
| `/api/v1alpha1/volumes` | dockerizer-backend :8080 |
| `/api/v1alpha1/registries` | dockerizer-backend :8080 |
| `/dockerizer` | dockerizer-web :80 |

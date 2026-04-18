# AIPub Brewery Backend - MVP 실행계획

> 작성일: 2026-04-17 | 최종 업데이트: 2026-04-18  
> 프로젝트: aipub-brewery-backend  
> 기술 스택: Spring Boot 4.0.5, Java 21, JPA, Kubernetes Java Client (official) 24.0.0, H2/PostgreSQL

---

## 1. 프로젝트 구조

```
aipub-brewery-backend/                      # Gradle 멀티모듈 루트
├── backend-server/                         # 모듈 1: REST API 서버
│   └── src/main/java/io/ten1010/aipubbrewerybackend/
│       ├── common/
│       │   ├── config/                     #   K8sProperties, KubernetesConfiguration, BreweryProperties
│       │   │                               #   OpenApiConfiguration, McpServerConfiguration
│       │   └── exception/                  #   글로벌 예외 처리 (ProblemDetail 기반)
│       ├── dockerfile/
│       │   ├── controller/                 #   DockerfileController, BuildContextFileController
│       │   ├── dto/                        #   Request/Response DTO, MapStruct Mapper
│       │   ├── entity/                     #   Dockerfile, BuildContextFile JPA Entity
│       │   ├── repository/                 #   DockerfileRepository, BuildContextFileRepository
│       │   └── service/                    #   DockerfileService, DockerfileValidator, BuildContextFileService
│       ├── imagebuild/
│       │   ├── controller/                 #   ImageBuild 트리거/목록/상태/로그 REST API
│       │   ├── cr/                         #   ImageBuild CR 모델 (Constants, Spec, Status, Cr)
│       │   ├── dto/                        #   Request/Response DTO
│       │   └── service/                    #   K8s CustomObjectsApi/CoreV1Api 연동
│       ├── volume/
│       │   ├── client/                     #   AipubVolumeClient (Interface + K8s/Proxy 구현체)
│       │   ├── controller/                 #   VolumeController (목록, 파일 브라우저)
│       │   ├── dto/                        #   VolumeInfo, BrowseResponse, FileEntry
│       │   └── service/                    #   VolumeBrowserService (Pod exec 기반)
│       └── registry/
│           ├── controller/                 #   RegistryController (NGC, HuggingFace 프록시)
│           ├── dto/                        #   RegistryImage, ImageSearchResponse, ImageTagsResponse
│           └── service/                    #   NgcRegistryService, HuggingfaceRegistryService
├── imagebuild-controller/                  # 모듈 2: K8s Operator
│   └── src/main/java/io/ten1010/aipubbrewerycontroller/
│       ├── config/                         #   K8sProperties, KubernetesConfiguration, ControllerProperties
│       ├── cr/                             #   ImageBuildConstants, Spec, Status, Resource, ResourceList
│       └── reconciler/                     #   ImageBuildWatcher, Reconciler, KanikoJobFactory
│                                           #   ImageBuildStatusUpdater, EventRecorder
├── k8s/
│   └── imagebuild-crd.yaml                # ImageBuild CRD (brewery.aipub.ten1010.io/v1alpha1)
└── docs/
    ├── mvp-execution-plan.md               # 본 문서
    ├── kubernetes-integration.md           # K8s 리소스 연동 정리
    ├── redesign-build-context.md           # 빌드 컨텍스트 확장 초기 설계
    └── enhancement-build-context.md        # 고도화: 빌드 컨텍스트 + 파일 브라우저
```

---

## 2. API 엔드포인트 설계

### 2.1 Dockerfile 관리 (`/api/v1/dockerfiles`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/dockerfiles` | Dockerfile 생성 (description 포함) |
| GET | `/api/v1/dockerfiles/{id}` | Dockerfile 단건 조회 (contextFiles 포함) |
| GET | `/api/v1/dockerfiles?project={p}&username={u}` | 목록 조회 (프로젝트/사용자별) |
| PUT | `/api/v1/dockerfiles/{id}` | Dockerfile 수정 (name, description, content) |
| DELETE | `/api/v1/dockerfiles/{id}` | Dockerfile 삭제 (contextFiles cascade 삭제) |

### 2.2 빌드 컨텍스트 파일 (`/api/v1/dockerfiles/{id}/files`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/dockerfiles/{id}/files` | 빌드 컨텍스트 파일 목록 조회 |
| POST | `/api/v1/dockerfiles/{id}/files` | 파일 업로드 (multipart, targetPath 지정) |
| DELETE | `/api/v1/dockerfiles/{id}/files/{fileId}` | 파일 삭제 |

### 2.3 이미지 빌드 (`/api/v1/builds`)

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/builds` | 빌드 트리거 (ImageBuild CR 생성) |
| GET | `/api/v1/builds?project={p}` | 프로젝트별 빌드 목록 조회 |
| GET | `/api/v1/builds/{namespace}/{name}` | 빌드 상태 조회 |
| GET | `/api/v1/builds/{namespace}/{name}/logs` | 빌드 로그 일회성 조회 (text/plain) |
| GET | `/api/v1/builds/{namespace}/{name}/logs/stream` | 빌드 로그 실시간 스트리밍 (SSE, text/event-stream) |

### 2.4 Volume 파일 브라우저 (`/api/v1/volumes`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/volumes/{namespace}` | AIPubVolume 목록 조회 |
| GET | `/api/v1/volumes/{namespace}/{volumeName}/browse?path=` | Volume 내 파일/디렉토리 조회 |

### 2.5 외부 레지스트리 프록시 (`/api/v1/registries`)

| Method | Path | 설명 |
|--------|------|------|
| GET | `/api/v1/registries/ngc/images?query=&page=&pageSize=` | NGC 이미지 검색 |
| GET | `/api/v1/registries/ngc/images/{org}/{repo}/tags` | NGC 이미지 태그 조회 |
| GET | `/api/v1/registries/huggingface/images?query=&page=&pageSize=` | HuggingFace 이미지 검색 |
| GET | `/api/v1/registries/huggingface/images/{repo}/tags` | HuggingFace 이미지 태그 조회 |

### 2.6 문서화 엔드포인트

| Path | 설명 |
|------|------|
| `/api-docs` | OpenAPI 3.1 JSON (springdoc 3.0.3) |
| `/swagger-ui` | Swagger UI |
| `/static/docs/index.html` | Spring REST Docs HTML |
| `/mcp/messages` | MCP SSE 엔드포인트 (AI 에이전트용) |

---

## 3. 단계별 실행계획

### Phase 1: 프로젝트 기반 세팅 ✅ 완료

- [x] Spring Boot 4.0.5 + Java 21 프로젝트 초기화
- [x] 의존성 추가 (Web, JPA, Validation, K8s Java Client 24.0.0, MapStruct, Lombok, Springdoc)
- [x] 패키지 구조 생성 (dockerfile, imagebuild, common)
- [x] application.yaml 프로파일 구성 (기본: H2, prod: PostgreSQL)
- [x] 글로벌 예외 처리 (ProblemDetail 기반)

### Phase 2: Dockerfile CRUD ✅ 완료

- [x] Dockerfile JPA Entity (project, username, name, description, content, contextFiles)
- [x] BuildContextFile Entity (fileName, targetPath, fileSize, storagePath)
- [x] DockerfileService CRUD + DockerfileValidator
- [x] DockerfileController REST API
- [x] 테스트: Validator 7개, Service 7개, Repository 6개, RestDocs 5개

### Phase 3-A: ImageBuild CR 연동 — backend-server ✅ 완료

- [x] ImageBuild CRD 정의, K8s 클라이언트 설정
- [x] CR 생성/조회/로그 + 멀티모듈 전환

### Phase 3-B: ImageBuild Controller — imagebuild-controller ✅ 완료

- [x] CR Watch, Reconciler (Pending→Preparing→Building→Succeeded/Failed)
- [x] KanikoJobFactory, EventRecorder, StatusUpdater

### Phase 3-C: API 문서화 ✅ 완료

- [x] OpenAPI (springdoc 3.0.3), Swagger UI, REST Docs, MCP Server

### Phase 3-D: 프론트엔드 연동 API 확장 ✅ 완료

프론트엔드 Gap 분석 결과를 반영하여 API 추가/수정:

- [x] **Dockerfile `description` 필드 추가** — Entity, CreateRequest, UpdateRequest, Response 모두 적용
- [x] **DockerfileUpdateRequest 확장** — `name` (optional), `description` (optional) 수정 가능
- [x] **빌드 목록 조회 API** — `GET /api/v1/builds?project={p}` (K8s CR listNamespaced)
- [x] **ImageBuildResponse 필드 추가** — `dockerfileId`, `username`, `createdAt` (CR labels로 저장/조회)
- [x] **COPY 허용** — forbidden-instructions에서 COPY 제거, ADD만 reject
  - Controller/DTO OpenAPI 설명도 "COPY 허용, ADD만 reject"로 수정
- [x] **빌드 컨텍스트 파일 업로드 API** — `POST/GET/DELETE /api/v1/dockerfiles/{id}/files`
  - BuildContextFileController + BuildContextFileService
  - 로컬 파일 저장 (configurable base-path)
  - 경로 순회 방지 검증
- [x] **빌드 로그 실시간 스트리밍 (SSE)** — `GET /api/v1/builds/{namespace}/{name}/logs/stream`
  - `Content-Type: text/event-stream`
  - `PodLogs.streamNamespacedPodLog()` (follow=true) → 라인 단위 SSE 전송
  - 빌드 완료 시 `event: done`, `data: [DONE]` 발송 후 스트림 종료
  - timeout: 5분
  - 기존 `GET /logs` (text/plain, 일회성)는 빌드 완료 후 전체 로그 조회용으로 유지

### Phase 3-E: Volume 파일 브라우저 ✅ 완료

- [x] AIPubVolume 목록 조회 (`GET /api/v1/volumes/{namespace}`)
- [x] Volume 파일 브라우저 (`GET /api/v1/volumes/{namespace}/{volumeName}/browse?path=`)
  - 기존 AIPubVolume 상주 Pod(busybox, sleep infinity)에 exec
  - `find -printf` 기반 파일/디렉토리 조회
- [x] AipubVolumeClient 인터페이스 + 2개 구현체
  - `K8sAipubVolumeClient` — K8s API 직접 사용 (기본)
  - `ProxyAipubVolumeClient` — AIPub k8s proxy 경유
  - `brewery.volume.client-mode` 설정으로 전환 (K8S / PROXY)
- [x] RestDocs 테스트 (3개 — 목록, 루트 조회, 하위 디렉토리 조회)

### Phase 3-F: 외부 레지스트리 프록시 ✅ 완료

- [x] NGC 이미지 검색 + 태그 조회 (api.ngc.nvidia.com, nvcr.io 프록시)
- [x] HuggingFace 이미지 검색 + 태그 조회 (Docker Hub huggingface/* 프록시)
- [x] 레지스트리별 enabled/disabled 설정
- [x] NGC API Key 설정 (환경변수 `NGC_API_KEY`)

### Phase 4: 추가 기능 및 안정화

- [ ] 입력 데이터 추가 검증 (targetImage/tag 형식, content 크기 제한)
- [ ] API 응답 페이지네이션 지원
- [ ] CORS 설정 (aipub-brewery-web 연동용)

### Phase 5: 인증/인가 연동

- [ ] AIPub OIDC 연동 (Spring Security OAuth2 Resource Server)
- [ ] Project 기반 접근 제어
- [ ] API 인증 테스트

### Phase 6: 운영 준비

- [ ] DB 마이그레이션 (Flyway/Liquibase)
- [ ] Kubernetes 배포 매니페스트, Docker 이미지 빌드
- [ ] 로깅/모니터링 설정

---

## 4. 핵심 설계 결정

### 4.1 Dockerfile 검증 전략

- `ADD` 지시자만 reject (COPY는 빌드 컨텍스트 파일 참조를 위해 허용)
- 정규식 기반 라인별 검사: `^\s*ADD\s`
- 설정 외부화: `brewery.dockerfile.forbidden-instructions`

### 4.2 데이터 모델

```
dockerfiles
├── id (PK, auto)
├── project (string, not null)
├── username (string, not null)
├── name (string, not null)
├── description (text, nullable)
├── content (text, not null)
├── created_at (timestamp)
└── updated_at (timestamp)
UNIQUE(project, username, name)

build_context_files
├── id (PK, auto)
├── dockerfile_id (FK → dockerfiles.id)
├── file_name (string, not null)
├── target_path (string, not null)
├── file_size (bigint, not null)
├── storage_path (string, not null)
└── uploaded_at (timestamp)
CASCADE DELETE: dockerfile 삭제 시 연관 파일도 삭제
```

### 4.3 ImageBuild CR

```
Phase 흐름: Pending → Preparing → Building → Succeeded
                                    ↘ Failed

Labels (메타데이터 저장):
  brewery.aipub.ten1010.io/dockerfile-id: "1"
  brewery.aipub.ten1010.io/username: "joonwoo"
```

각 phase 전이 시 K8s Event 발행 (Normal: PhaseTransition, Warning: BuildFailed)

### 4.4 Volume 파일 브라우저

AIPubVolume 생성 시 자동 생성되는 상주 Pod (busybox, sleep infinity)에 exec하여 파일 목록 조회.
별도 Pod 생성 불필요 → 즉시 응답.

### 4.5 AipubVolumeClient 전략 패턴

```yaml
brewery:
  volume:
    client-mode: K8S    # K8S: CustomObjectsApi 직접 | PROXY: AIPub k8s proxy 경유
```

Volume CR 조회는 K8S/PROXY 전환 가능, Pod exec는 항상 K8s API 직접 사용.

### 4.6 프로파일 전략

| 프로파일 | DB | 용도 |
|----------|-----|------|
| default | H2 (in-memory) | 로컬 개발 |
| prod | PostgreSQL | 운영/스테이징 |
| test | H2 (in-memory) | 테스트 |

### 4.7 외부 레지스트리

```yaml
brewery:
  registry:
    ngc:
      enabled: true
      api-key: ${NGC_API_KEY:}     # 태그 조회 시 필요
    huggingface:
      enabled: true                 # Docker Hub huggingface/* 프록시
```

### 4.8 빌드 로그 스트리밍

| 엔드포인트 | 방식 | 용도 |
|-----------|------|------|
| `GET /{ns}/{name}/logs` | `text/plain` (일회성) | 빌드 완료 후 전체 로그 조회 |
| `GET /{ns}/{name}/logs/stream` | `text/event-stream` (SSE) | 빌드 진행 중 실시간 스트리밍 |

SSE 스트리밍은 `PodLogs.streamNamespacedPodLog(follow=true)`로 K8s Pod 로그를 InputStream으로 수신하여, 라인 단위로 SSE event 전송. 빌드 완료 시 `event: done` 발송.

프론트엔드 사용:
```javascript
const es = new EventSource('/api/v1/builds/{ns}/{name}/logs/stream');
es.onmessage = (e) => appendLog(e.data);
es.addEventListener('done', () => es.close());
```

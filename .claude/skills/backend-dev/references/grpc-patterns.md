# gRPC 개발 패턴 레퍼런스

이 프로젝트의 gRPC 개발 시 참조하는 패턴 가이드. proto 정의 → gRPC 서비스 구현 → Gateway REST 프록시의 전체 흐름을 다룬다.

---

## 목차
1. [Proto 파일 작성 패턴](#1-proto-파일-작성-패턴)
2. [gRPC 서비스 구현 패턴](#2-grpc-서비스-구현-패턴)
3. [Gateway REST 프록시 패턴](#3-gateway-rest-프록시-패턴)
4. [Proto ↔ Domain 컨버터 패턴](#4-proto--domain-컨버터-패턴)
5. [빌드 및 코드 생성](#5-빌드-및-코드-생성)

---

## 1. Proto 파일 작성 패턴

### 위치
`backend-protocol/proto/api/` 디렉토리

### 네이밍
- 파일명: `snake_case.proto` (예: `image_hub_project.proto`)
- 서비스명: `PascalCase` + `Service` 접미사 (예: `ImageHubProjectService`)
- 메시지명: `PascalCase` (예: `CreateImageHubProjectRequest`)
- RPC 메서드: `PascalCase` 동사 + 명사 (예: `CreateProject`, `GetProjectById`)

### 메시지 패턴
- 요청: `{Action}{Resource}Request`
- 응답: `{Action}{Resource}Response`
- 공통 엔티티는 `entities.proto` 또는 `common/common.proto`에 정의

### 참고 사항
- `common.proto`에 공통 타입(PageRequest, PageResponse 등) 정의되어 있으므로 확인 후 재사용
- 기존 proto 파일의 스타일을 반드시 따른다 — import 순서, option 설정, 주석 스타일

## 2. gRPC 서비스 구현 패턴

### 위치
`backend-api/src/main/java/.../api/grpc/`

### 구현 흐름
1. Proto에서 생성된 `{Service}Grpc.{Service}ImplBase`를 상속
2. `@GrpcService` 어노테이션 사용
3. 각 RPC 메서드를 오버라이드
4. 요청 메시지 → 도메인 모델 변환 → 서비스 호출 → 응답 메시지 변환

### 인터셉터
- `GrpcTokenInterceptor`: JWT 토큰 검증
- `GrpcMdcInterceptor`: MDC 전파 (traceId 등)
- 요청/응답 로깅 인터셉터

## 3. Gateway REST 프록시 패턴

### 위치
`backend-gateway/src/main/java/.../gateway/controller/`

### 구현 흐름
1. REST 컨트롤러에서 HTTP 요청 수신
2. 요청 DTO → Proto 메시지 변환
3. gRPC 스텁으로 backend-api 호출
4. Proto 응답 → REST 응답 DTO 변환

### 컨트롤러 네이밍
- 버전 접두사: `V1alpha1{Resource}Controller`, `V1alpha2{Resource}Controller`
- 경로 구성: `/api/v1alpha1/{resource}/...`

### 인증
- Gateway가 JWT 인증 처리 후 gRPC 메타데이터로 토큰 전달
- `@PreAuthorize` 또는 커스텀 인증 어노테이션 사용

## 4. Proto ↔ Domain 컨버터 패턴

### backend-api 내부
- `grpc/converter/` 패키지에 proto 메시지 ↔ 도메인 모델 컨버터
- MapStruct를 사용하는 경우와 수동 변환이 혼재

### backend-gateway 내부
- 컨트롤러 레벨에서 REST DTO ↔ proto 메시지 변환
- 서비스 레이어에서 변환 로직 구현

## 5. 빌드 및 코드 생성

```bash
# proto 컴파일 (Java 코드 생성)
./gradlew :backend-protocol:build

# proto 변경 후 의존 모듈 빌드
./gradlew :backend-protocol:build && ./gradlew :backend-api:build -x test

# gateway까지 전체 빌드
./gradlew :backend-protocol:build && ./gradlew :backend-api:build -x test && ./gradlew :backend-gateway:build -x test
```

proto 파일 변경 시 반드시 `backend-protocol`을 먼저 빌드하여 Java 코드를 생성한 뒤, 의존 모듈을 빌드한다.

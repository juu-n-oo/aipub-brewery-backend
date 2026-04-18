# 모듈별 개발 가이드

aipub-backend 각 모듈의 특성과 개발 시 주의사항.

---

## 목차
1. [backend-protocol](#backend-protocol)
2. [backend-api](#backend-api)
3. [backend-gateway](#backend-gateway)
4. [backend-common](#backend-common)
5. [backend-batch](#backend-batch)
6. [backend-usage](#backend-usage)
7. [backend-adapter](#backend-adapter)
8. [backend-notice](#backend-notice)

---

## backend-protocol

**역할**: 공유 Protobuf 정의 (gRPC 계약의 단일 출처)

**주요 디렉토리**:
- `proto/api/` — 서비스별 proto 정의 (19개)
- `proto/common/` — 공통 타입
- `proto/lgd/` — LGD 전용
- `proto/usage/` — 사용량 서비스

**개발 시 주의**:
- 이 모듈의 변경은 backend-api, backend-gateway 모두에 영향
- proto 변경 후 반드시 이 모듈을 먼저 빌드하여 Java 코드 생성
- 기존 메시지 필드 번호를 변경하지 않는다 (하위 호환성)
- 새 필드 추가 시 기존 최대 번호 + 1 사용

## backend-api

**역할**: 메인 gRPC API 서버 (포트 8091)

**주요 패키지**:
- `grpc/` — gRPC 서비스 구현체, 인터셉터, 컨버터
- `notification/` — 알림 (이메일, 인앱)
- `imagehub/` — 이미지 레지스트리 관리
- `batch/` — 배치 작업 오케스트레이션
- `survey/` — 설문
- `configuration/` — 앱 설정
- `data/repository/` — QueryDSL 레포지토리

**DDD 레이어 패턴**:
```
feature/
├── domain/          # 도메인 모델, 값 객체, 레포지토리 인터페이스
├── application/     # @Service + @Transactional
├── infrastructure/  # @Entity, @Repository 구현
└── event/           # ApplicationEvent
```

**개발 시 주의**:
- gRPC 서비스 구현 시 `@GrpcService` 사용
- 예외는 `@GrpcAdvice`로 전역 처리
- Elasticsearch 연동 기능은 `configuration/` 하위 설정 확인
- QueryDSL Q클래스는 빌드 시 자동 생성 — 엔티티 변경 후 빌드 필요

## backend-gateway

**역할**: HTTP/REST 게이트웨이 (포트 8081) — REST를 gRPC로 변환

**주요 패키지**:
- `controller/` — REST 컨트롤러 (25개)
- `service/` — gRPC 클라이언트 호출 서비스
- `configuration/` — 보안, OAuth2, LDAP 설정

**개발 시 주의**:
- 컨트롤러 네이밍: `V1alpha1{Resource}Controller` 또는 `V1alpha2{Resource}Controller`
- JWT 인증은 Gateway가 처리, gRPC 메타데이터로 전달
- REST Docs 기반 API 문서화 — Acceptance Test로 스니펫 생성
- Spring Cloud Gateway의 ProxyExchange 사용

## backend-common

**역할**: 공유 유틸리티, 상수, gRPC 예외 타입

**개발 시 주의**:
- 이 모듈 변경은 모든 모듈에 영향
- 공통 예외, 유틸리티만 포함. 비즈니스 로직 금지.

## backend-batch

**역할**: Spring Batch + Quartz 스케줄러

**개발 시 주의**:
- Job/Step 정의는 Spring Batch 설정 클래스에서
- Quartz로 스케줄링
- Keycloak/K8s/Harbor 사용자 동기화 작업

## backend-usage

**역할**: 사용량 추적 gRPC 서비스

**개발 시 주의**:
- 자체 gRPC 서비스 (usage.proto)
- JPA + QueryDSL 사용

## backend-adapter

**역할**: 다수 고객사 SSO 설정을 위한 인증 어댑터

**개발 시 주의**:
- Vanilla, Customer1, Customer2 등 고객사별 어댑터 패턴
- 새 고객사 추가 시 기존 어댑터 패턴 참고

## backend-notice

**역할**: 공지사항/알림 서비스

**개발 시 주의**:
- notice.proto 기반 gRPC 서비스
- CRUD + 페이지네이션 패턴

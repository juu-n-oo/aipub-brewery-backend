# AIPub Brewery Backend

AIPub 플랫폼의 웹 기반 Dockerfile 편집 및 이미지 빌드/관리 서비스 백엔드.

## 모듈 구조

이 프로젝트는 Gradle 멀티모듈로 구성되며, 두 개의 독립 실행 가능한 Spring Boot 애플리케이션으로 이루어져 있다.

```
aipub-brewery-backend/
├── backend-server/            # REST API 서버
├── imagebuild-controller/     # K8s Operator (ImageBuild Controller)
└── k8s/                       # K8s 매니페스트 (CRD 등)
```

### backend-server

Dockerfile 관리 및 이미지 빌드 요청을 처리하는 REST API 서버.

- Dockerfile CRUD API (저장, 조회, 수정, 삭제)
- Dockerfile 검증 (MVP에서 COPY/ADD 금지)
- ImageBuild CR 생성 (빌드 트리거)
- ImageBuild CR 상태 조회 및 빌드 로그 조회
- Swagger UI 제공 (`/swagger-ui`)

**기술 스택**: Spring Boot, Spring Data JPA, Kubernetes Java Client, MapStruct, Springdoc OpenAPI

### imagebuild-controller

ImageBuild CR을 watch하여 Kaniko 기반 빌드 Job을 생성/관리하는 K8s Operator.

- ImageBuild CR watch (Informer 기반)
- Kaniko Pod/Job 생성 및 라이프사이클 관리
- 빌드 결과를 CR status에 반영 (phase, imageDigest 등)
- Harbor로 이미지 push

**기술 스택**: Spring Boot, Kubernetes Java Client (extended), Informer/Reconciler 패턴

## 빌드

```bash
# 전체 빌드
./gradlew build

# 모듈별 빌드
./gradlew :backend-server:build
./gradlew :imagebuild-controller:build
```

## 실행

```bash
# backend-server (로컬 개발 - H2 DB)
./gradlew :backend-server:bootRun

# imagebuild-controller (로컬 개발 - ~/.kube/config 사용)
./gradlew :imagebuild-controller:bootRun
```

## CRD 설치

ImageBuild Controller가 동작하려면 CRD가 클러스터에 설치되어 있어야 한다.

```bash
kubectl apply -f k8s/imagebuild-crd.yaml
```

## 데이터 흐름

```
[User Browser]
  └─ aipub-brewery-web (프론트엔드)
      │
      └─ backend-server (REST API)
          ├─ Dockerfile 저장/조회 (DB)
          ├─ ImageBuild CR 생성 ──────→ K8s API Server
          ├─ CR 상태 조회 ←────────── K8s API Server
          └─ Pod 로그 조회 ←────────── K8s API Server
                                          │
                                   imagebuild-controller (Operator)
                                          ├─ CR watch
                                          ├─ Kaniko Pod 생성
                                          └─ CR status 업데이트
```

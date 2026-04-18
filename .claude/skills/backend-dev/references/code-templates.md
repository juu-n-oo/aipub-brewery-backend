# 코드 템플릿 — 골든 패턴

새 기능 구현 시 이 템플릿의 구조를 그대로 따르라. survey 모듈의 실제 구조를 기반으로, DDD 정석에 맞게 보정한 이상적 패턴이다.

이 문서가 프로젝트 현행 코드와 다를 경우, **이 문서의 패턴을 우선**한다. 팀이 지향하는 DDD 구조를 반영한 것이기 때문이다.

---

## 목차
1. [Feature 패키지 구조](#1-feature-패키지-구조)
2. [Proto 정의](#2-proto-정의)
3. [Domain 레이어](#3-domain-레이어)
4. [Application 레이어](#4-application-레이어)
5. [Infrastructure 레이어](#5-infrastructure-레이어)
6. [gRPC 레이어](#6-grpc-레이어)
7. [Event 레이어](#7-event-레이어)
8. [Gateway 레이어](#8-gateway-레이어)
9. [테스트 패턴](#9-테스트-패턴)

---

## 1. Feature 패키지 구조

```
feature/
├── domain/
│   ├── {Feature}.java              # Aggregate Root (POJO)
│   ├── {SubEntity}.java            # 하위 도메인 모델 (POJO)
│   ├── {ValueObject}.java          # 값 객체 (선택)
│   └── repository/
│       └── {Feature}Repository.java  # 레포지토리 인터페이스 (포트)
├── application/
│   ├── service/
│   │   └── {Feature}Service.java   # Application 서비스
│   ├── dto/
│   │   └── {Feature}Dto.java       # Application DTO (record)
│   └── converter/
│       └── {Feature}Converter.java # DTO <-> 도메인 변환
├── infrastructure/
│   ├── entity/
│   │   └── {Feature}Entity.java    # JPA 엔티티
│   ├── repository/
│   │   ├── {Feature}RepositoryImpl.java  # 레포지토리 구현체 (어댑터)
│   │   └── {Feature}JpaRepository.java   # Spring Data JPA
│   └── converter/
│       └── {Feature}EntityConverter.java # 도메인 <-> 엔티티 변환
└── event/                          # 비동기 사이드이펙트가 있을 때만 생성
    ├── {Feature}CreatedEvent.java  # 이벤트 (record)
    └── {Feature}EventListener.java # 리스너
```

**현행 코드와의 차이**: 기존 모듈들은 레포지토리 인터페이스가 `application/repository/`에 있다. 새 기능에서는 `domain/repository/`에 배치한다. 도메인 레이어가 자기 자신의 영속성 계약을 소유하는 것이 DDD 원칙이다.

---

## 2. Proto 정의

**위치**: `backend-protocol/proto/api/{feature_name}.proto`

```protobuf
syntax = "proto3";

package io.ten1010.protobuf;

option java_package = "io.ten1010.protobuf.service";
option java_multiple_files = true;

import "entities.proto";
import "common/common.proto";

service {Feature}Service {
  rpc Create{Feature}(Create{Feature}Request) returns (Create{Feature}Response) {}
  rpc Get{Feature}ById(Get{Feature}ByIdRequest) returns (Get{Feature}ByIdResponse) {}
  rpc Get{Feature}List(Get{Feature}ListRequest) returns (Get{Feature}ListResponse) {}
  rpc Update{Feature}(Update{Feature}Request) returns (Update{Feature}Response) {}
  rpc Delete{Feature}(Delete{Feature}Request) returns (Delete{Feature}Response) {}
}

// 요청/응답 메시지 네이밍: {Action}{Resource}Request/Response
message Create{Feature}Request {
  string title = 1;
  string content = 2;
}

message Create{Feature}Response {
  {Feature}Data {feature} = 1;
}

// 목록 조회 시 공통 PageRequest/PageResponse 사용
message Get{Feature}ListRequest {
  PageRequest pageRequest = 1;
}

message Get{Feature}ListResponse {
  repeated {Feature}Data {feature}List = 1;
  PageResponse pageResponse = 2;
}

// 리소스 데이터 메시지는 별도 정의 (entities.proto에 추가하거나 로컬 정의)
message {Feature}Data {
  int64 id = 1;
  string title = 2;
  string content = 3;
  string createdDate = 4;
  string updatedDate = 5;
}
```

**규칙**:
- 파일명: `snake_case.proto`
- 기존 메시지 필드 번호는 절대 변경하지 않는다 (하위 호환성)
- `common.proto`의 `PageRequest`/`PageResponse`를 확인하고 재사용한다
- `optional` 키워드: 클라이언트가 명시적으로 보내지 않을 수 있는 필드에 사용

---

## 3. Domain 레이어

### 3-1. 도메인 모델 (Aggregate Root)

도메인 모델은 JPA 의존성이 없는 순수 POJO이다. 빈혈 모델이 아니라, 비즈니스 규칙을 메서드로 표현한다.

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.domain;

import java.time.Instant;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder(toBuilder = true)
@NoArgsConstructor(access = AccessLevel.PRIVATE)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class {Feature} {

  private Long id;
  private String title;
  private String content;
  private String authorId;
  private Boolean pinned;
  private Instant createdDate;
  private Instant updatedDate;

  // --- 팩토리 메서드: 생성 의도를 명확히 표현 ---
  public static {Feature} create(String title, String content, String authorId) {
    return {Feature}.builder()
        .title(title)
        .content(content)
        .authorId(authorId)
        .pinned(false)
        .build();
  }

  // --- 행위 메서드: 상태 변경에 비즈니스 규칙 포함 ---
  public void updateContent(String title, String content) {
    this.title = title;
    this.content = content;
  }

  public void pin() {
    this.pinned = true;
  }

  public void unpin() {
    this.pinned = false;
  }
}
```

**핵심 원칙**:
- `@Setter` 금지. 상태 변경은 의도를 표현하는 메서드로만 수행한다.
- `@Builder(toBuilder = true)`: 불변 재생성이 필요할 때 사용한다.
- 팩토리 메서드(`create`)로 생성 규칙을 캡슐화한다.
- 시간 필드는 `java.time.Instant`만 사용한다.

### 3-2. 레포지토리 인터페이스 (포트)

**위치**: `domain/repository/` — 도메인 레이어가 영속성 계약을 소유한다.

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.domain.repository;

import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

public interface {Feature}Repository {

  {Feature} save({Feature} {feature});

  Optional<{Feature}> findById(Long id);

  Page<{Feature}> findAll(Pageable pageable);

  void deleteById(Long id);
}
```

**규칙**:
- 인터페이스는 **도메인 타입만** 파라미터/반환값으로 사용한다. JPA 엔티티가 노출되면 안 된다.
- `Optional`을 반환하여 호출부에서 명시적으로 처리하게 한다.
- 페이지네이션이 필요하면 `org.springframework.data.domain.Page`/`Pageable`을 사용한다 (Spring Data는 인프라가 아닌 추상화 레이어로 허용).

---

## 4. Application 레이어

### 4-1. Application 서비스

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.application.service;

import io.ten1010.aipub.aipubbackend.api.{feature}.application.dto.Create{Feature}Dto;
import io.ten1010.aipub.aipubbackend.api.{feature}.application.dto.Update{Feature}Dto;
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.repository.{Feature}Repository;
import io.ten1010.aipub.aipubbackend.common.grpc.exception.AipubGrpcException;
import io.ten1010.aipub.aipubbackend.common.grpc.exception.ErrorCodeTypeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class {Feature}Service {

  private final {Feature}Repository {feature}Repository;
  private final ApplicationEventPublisher eventPublisher;

  @Transactional
  public {Feature} create(Create{Feature}Dto dto) {
    {Feature} {feature} = {Feature}.create(dto.title(), dto.content(), dto.authorId());
    {Feature} saved = {feature}Repository.save({feature});
    // 비동기 사이드이펙트가 필요하면 이벤트 발행
    // eventPublisher.publishEvent(new {Feature}CreatedEvent(saved));
    return saved;
  }

  @Transactional(readOnly = true)
  public {Feature} getById(Long id) {
    return {feature}Repository.findById(id)
        .orElseThrow(() -> new AipubGrpcException(
            ErrorCodeTypeEnum.{FEATURE}_NOT_FOUND, id));
  }

  @Transactional(readOnly = true)
  public Page<{Feature}> getList(Pageable pageable) {
    return {feature}Repository.findAll(pageable);
  }

  @Transactional
  public {Feature} update(Long id, Update{Feature}Dto dto) {
    {Feature} {feature} = getById(id);
    {feature}.updateContent(dto.title(), dto.content());
    return {feature}Repository.save({feature});
  }

  @Transactional
  public void delete(Long id) {
    {feature}Repository.deleteById(id);
  }
}
```

**규칙**:
- `@Transactional`은 서비스 메서드에 배치한다. 읽기 전용은 `@Transactional(readOnly = true)`.
- 리소스 미발견 시 `AipubGrpcException` + `ErrorCodeTypeEnum.{FEATURE}_NOT_FOUND`를 던진다. `.orElseThrow()`에 예외를 명시한다.
- 비동기 사이드이펙트(알림, 외부 연동)는 `ApplicationEventPublisher`로 이벤트를 발행한다. 서비스에서 직접 처리하지 않는다.
- 도메인 로직은 도메인 모델에 위임한다 (`{feature}.updateContent()`). 서비스에서 setter를 호출하지 않는다.

### 4-2. Application DTO

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.application.dto;

public record Create{Feature}Dto(
    String title,
    String content,
    String authorId) {}
```

**규칙**: Java `record`를 사용한다. 불변이고 간결하다.

### 4-3. Application 컨버터

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.application.converter;

import io.ten1010.aipub.aipubbackend.api.{feature}.application.dto.Create{Feature}Dto;
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};

public class {Feature}Converter {

  public static Create{Feature}Dto toDto(String title, String content, String authorId) {
    return new Create{Feature}Dto(title, content, authorId);
  }
}
```

---

## 5. Infrastructure 레이어

### 5-1. JPA 엔티티

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.entity;

import io.ten1010.aipub.aipubbackend.api.data.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

@Getter
@Entity
@SuperBuilder
@Table(name = "{feature_table}")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class {Feature}Entity extends BaseTimeEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String title;

  @Column(columnDefinition = "TEXT")
  private String content;

  @Column(nullable = false)
  private String authorId;

  @Column(nullable = false)
  private Boolean pinned;
}
```

**규칙**:
- `BaseTimeEntity`를 상속하여 `createdDate`/`updatedDate` 자동 관리.
- `@SuperBuilder`를 사용한다 (상속 체인에서 Builder 패턴 유지).
- `@NoArgsConstructor(access = PROTECTED)` — JPA 요구사항.
- `@Data` 금지 (hashCode/toString 순환 참조 문제).
- `@Setter` 금지 — 엔티티 변경은 도메인 모델 → 새 엔티티 변환으로 처리.

### 5-2. 레포지토리 구현체 (어댑터)

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.repository;

import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.repository.{Feature}Repository;
import io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.converter.{Feature}EntityConverter;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class {Feature}RepositoryImpl implements {Feature}Repository {

  private final {Feature}JpaRepository jpaRepository;

  @Override
  public {Feature} save({Feature} {feature}) {
    return {Feature}EntityConverter.toDomain(
        jpaRepository.save({Feature}EntityConverter.toEntity({feature})));
  }

  @Override
  public Optional<{Feature}> findById(Long id) {
    return jpaRepository.findById(id)
        .map({Feature}EntityConverter::toDomain);
  }

  @Override
  public Page<{Feature}> findAll(Pageable pageable) {
    return jpaRepository.findAll(pageable)
        .map({Feature}EntityConverter::toDomain);
  }

  @Override
  public void deleteById(Long id) {
    jpaRepository.deleteById(id);
  }
}
```

**핵심**: 이 클래스가 도메인 ↔ 인프라 경계의 유일한 통로이다. JPA 엔티티는 이 밖으로 나가지 않는다.

### 5-3. Spring Data JPA 레포지토리

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.repository;

import io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.entity.{Feature}Entity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface {Feature}JpaRepository extends JpaRepository<{Feature}Entity, Long> {}
```

### 5-4. 엔티티 컨버터

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.converter;

import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.aipub.aipubbackend.api.{feature}.infrastructure.entity.{Feature}Entity;

public class {Feature}EntityConverter {

  public static {Feature}Entity toEntity({Feature} domain) {
    return {Feature}Entity.builder()
        .id(domain.getId())
        .title(domain.getTitle())
        .content(domain.getContent())
        .authorId(domain.getAuthorId())
        .pinned(domain.getPinned())
        .build();
  }

  public static {Feature} toDomain({Feature}Entity entity) {
    return {Feature}.builder()
        .id(entity.getId())
        .title(entity.getTitle())
        .content(entity.getContent())
        .authorId(entity.getAuthorId())
        .pinned(entity.getPinned())
        .createdDate(entity.getCreatedDate())
        .updatedDate(entity.getUpdatedDate())
        .build();
  }
}
```

**규칙**: static 메서드로 양방향 변환. `toEntity`/`toDomain` 네이밍.

---

## 6. gRPC 레이어

### 6-1. gRPC 서비스 구현체

**위치**: `api/grpc/{Feature}GrpcService.java`

```java
package io.ten1010.aipub.aipubbackend.api.grpc;

import io.grpc.stub.StreamObserver;
import io.ten1010.aipub.aipubbackend.api.grpc.converter.{Feature}ProtoConverter;
import io.ten1010.aipub.aipubbackend.api.{feature}.application.service.{Feature}Service;
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.protobuf.service.*;
import lombok.RequiredArgsConstructor;
import org.springframework.grpc.server.service.GrpcService;

@GrpcService
@RequiredArgsConstructor
public class {Feature}GrpcService extends {Feature}ServiceGrpc.{Feature}ServiceImplBase {

  private final {Feature}Service {feature}Service;

  @Override
  public void create{Feature}(
      Create{Feature}Request request,
      StreamObserver<Create{Feature}Response> responseObserver) {

    {Feature} result = {feature}Service.create(
        {Feature}ProtoConverter.toCreateDto(request));

    responseObserver.onNext(
        Create{Feature}Response.newBuilder()
            .set{Feature}({Feature}ProtoConverter.toProto(result))
            .build());
    responseObserver.onCompleted();
  }

  @Override
  public void get{Feature}ById(
      Get{Feature}ByIdRequest request,
      StreamObserver<Get{Feature}ByIdResponse> responseObserver) {

    {Feature} result = {feature}Service.getById(request.getId());

    responseObserver.onNext(
        Get{Feature}ByIdResponse.newBuilder()
            .set{Feature}({Feature}ProtoConverter.toProto(result))
            .build());
    responseObserver.onCompleted();
  }
}
```

**규칙**:
- gRPC 서비스는 **얇은 어댑터**이다. 비즈니스 로직을 넣지 않는다.
- proto → DTO 변환은 `ProtoConverter`에 위임한다.
- 이벤트 발행(`ApplicationEventPublisher`)은 여기서 하지 않는다. Application 서비스에서 한다.

### 6-2. Proto 컨버터 (backend-api)

**위치**: `api/grpc/converter/{Feature}ProtoConverter.java`

```java
package io.ten1010.aipub.aipubbackend.api.grpc.converter;

import io.ten1010.aipub.aipubbackend.api.{feature}.application.dto.Create{Feature}Dto;
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.protobuf.service.Create{Feature}Request;
import io.ten1010.protobuf.service.{Feature}Data;

public class {Feature}ProtoConverter {

  public static Create{Feature}Dto toCreateDto(Create{Feature}Request request) {
    return new Create{Feature}Dto(
        request.getTitle(),
        request.getContent(),
        request.hasAuthorId() ? request.getAuthorId() : null);
  }

  public static {Feature}Data toProto({Feature} domain) {
    {Feature}Data.Builder builder = {Feature}Data.newBuilder()
        .setId(domain.getId())
        .setTitle(domain.getTitle())
        .setContent(domain.getContent());

    if (domain.getCreatedDate() != null) {
      builder.setCreatedDate(domain.getCreatedDate().toString());
    }
    if (domain.getUpdatedDate() != null) {
      builder.setUpdatedDate(domain.getUpdatedDate().toString());
    }

    return builder.build();
  }
}
```

**규칙**: `optional` 필드는 반드시 `has{Field}()` 체크 후 변환한다.

---

## 7. Event 레이어

비동기 사이드이펙트(알림, 외부 시스템 연동)가 필요한 경우에만 생성한다.

### 7-1. 이벤트 정의

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.event;

import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};

public record {Feature}CreatedEvent({Feature} {feature}) {}
```

**규칙**: Java `record`로 정의. Spring `ApplicationEvent`를 상속하지 않는다.

### 7-2. 이벤트 리스너

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.event;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class {Feature}EventListener {

  @Async
  @EventListener
  public void handle{Feature}Created({Feature}CreatedEvent event) {
    log.debug("Handling {feature} created event: {}", event.{feature}().getId());
    // 알림 발송, 외부 시스템 연동 등
  }
}
```

---

## 8. Gateway 레이어

### 8-1. REST 컨트롤러

```java
package io.ten1010.aipub.aipubbackend.gateway.web.v1alpha1;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.ten1010.aipub.aipubbackend.gateway.client.grpc.Grpc{Feature}ApiClient;
import io.ten1010.aipub.aipubbackend.gateway.web.ResourcePathConstants;
import io.ten1010.aipub.aipubbackend.gateway.web.v1alpha1.dto.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@Tag(name = "{Feature}", description = "{feature} 관리")
@RestController
@RequestMapping(
    value = ResourcePathConstants.API
        + ResourcePathConstants.V1ALPHA1
        + ResourcePathConstants.{FEATURES})
@RequiredArgsConstructor
public class V1alpha1{Feature}Controller {

  private final Grpc{Feature}ApiClient grpcClient;

  @Operation(summary = "{feature} 생성")
  @PostMapping
  public ResponseEntity<V1alpha1{Feature}Response> create(
      @RequestBody V1alpha1Create{Feature}Request request) {
    return ResponseEntity.ok(grpcClient.create{Feature}(request));
  }

  @Operation(summary = "{feature} 단건 조회")
  @GetMapping("/{id}")
  public ResponseEntity<V1alpha1{Feature}Response> getById(@PathVariable Long id) {
    return ResponseEntity.ok(grpcClient.get{Feature}ById(id));
  }

  @Operation(summary = "{feature} 목록 조회")
  @GetMapping
  public ResponseEntity<V1alpha1{Feature}ListResponse> getList(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size) {
    return ResponseEntity.ok(grpcClient.get{Feature}List(page, size));
  }
}
```

**규칙**:
- `@Tag` + `@Operation` SpringDoc 어노테이션 필수. 한글 description.
- 경로 상수는 `ResourcePathConstants`에 추가한다.
- 메서드명은 HTTP 동사 + 리소스를 정확히 반영한다 (복붙 잔재 주의).

### 8-2. Gateway DTO

```java
package io.ten1010.aipub.aipubbackend.gateway.web.v1alpha1.dto;

import io.swagger.v3.oas.annotations.media.Schema;

public record V1alpha1Create{Feature}Request(
    @Schema(description = "제목", example = "점검 공지") String title,
    @Schema(description = "내용", example = "서버 점검이 예정되어 있습니다.") String content) {}
```

**규칙**: `@Schema`에 description + example 필수.

### 8-3. Gateway gRPC 클라이언트

```java
package io.ten1010.aipub.aipubbackend.gateway.client.grpc;

import io.grpc.StatusRuntimeException;
import io.ten1010.aipub.aipubbackend.gateway.client.converter.{Feature}ProtoConverter;
import io.ten1010.aipub.aipubbackend.gateway.web.v1alpha1.dto.*;
import io.ten1010.protobuf.service.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class Grpc{Feature}ApiClient {

  private final {Feature}ServiceGrpc.{Feature}ServiceBlockingStub stub;

  public V1alpha1{Feature}Response create{Feature}(V1alpha1Create{Feature}Request request) {
    try {
      Create{Feature}Response response = stub.create{Feature}(
          {Feature}ProtoConverter.toCreateRequest(request));
      return {Feature}ProtoConverter.toResponse(response.get{Feature}());
    } catch (StatusRuntimeException e) {
      log.error("[gRPC call failed] status: {}, description: {}",
          e.getStatus(), e.getStatus().getDescription());
      throw e;
    }
  }
}
```

### 8-4. Gateway Proto 컨버터

```java
package io.ten1010.aipub.aipubbackend.gateway.client.converter;

import io.ten1010.aipub.aipubbackend.gateway.web.v1alpha1.dto.*;
import io.ten1010.protobuf.service.*;

public class {Feature}ProtoConverter {

  public static Create{Feature}Request toCreateRequest(V1alpha1Create{Feature}Request dto) {
    Create{Feature}Request.Builder builder = Create{Feature}Request.newBuilder()
        .setTitle(dto.title());

    if (dto.content() != null) {
      builder.setContent(dto.content());
    }

    return builder.build();
  }

  public static V1alpha1{Feature}Response toResponse({Feature}Data proto) {
    return new V1alpha1{Feature}Response(
        proto.getId(),
        proto.getTitle(),
        proto.getContent(),
        proto.getCreatedDate(),
        proto.getUpdatedDate());
  }
}
```

---

## 9. 테스트 패턴

### 9-1. Application 서비스 단위 테스트

```java
package io.ten1010.aipub.aipubbackend.api.{feature}.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import io.ten1010.aipub.aipubbackend.api.{feature}.application.dto.Create{Feature}Dto;
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.{Feature};
import io.ten1010.aipub.aipubbackend.api.{feature}.domain.repository.{Feature}Repository;
import io.ten1010.aipub.aipubbackend.common.grpc.exception.AipubGrpcException;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

@ExtendWith(MockitoExtension.class)
class {Feature}ServiceTest {

  @InjectMocks
  private {Feature}Service {feature}Service;

  @Mock
  private {Feature}Repository {feature}Repository;

  @Mock
  private ApplicationEventPublisher eventPublisher;

  @Nested
  @DisplayName("create")
  class Create {

    @Test
    @DisplayName("유효한 요청이 주어지면 {feature}를 생성한다")
    void givenValidDto_whenCreate_thenReturn{Feature}() {
      // given
      Create{Feature}Dto dto = new Create{Feature}Dto("제목", "내용", "author1");
      {Feature} expected = {Feature}.builder()
          .id(1L)
          .title("제목")
          .content("내용")
          .authorId("author1")
          .build();
      given({feature}Repository.save(any({Feature}.class))).willReturn(expected);

      // when
      {Feature} result = {feature}Service.create(dto);

      // then
      assertThat(result.getId()).isEqualTo(1L);
      assertThat(result.getTitle()).isEqualTo("제목");
      verify({feature}Repository).save(any({Feature}.class));
    }
  }

  @Nested
  @DisplayName("getById")
  class GetById {

    @Test
    @DisplayName("존재하는 ID가 주어지면 {feature}를 반환한다")
    void givenExistingId_whenGetById_thenReturn{Feature}() {
      // given
      {Feature} expected = {Feature}.builder().id(1L).title("제목").build();
      given({feature}Repository.findById(1L)).willReturn(Optional.of(expected));

      // when
      {Feature} result = {feature}Service.getById(1L);

      // then
      assertThat(result.getTitle()).isEqualTo("제목");
    }

    @Test
    @DisplayName("존재하지 않는 ID가 주어지면 AipubGrpcException을 던진다")
    void givenNonExistingId_whenGetById_thenThrowException() {
      // given
      given({feature}Repository.findById(999L)).willReturn(Optional.empty());

      // when & then
      assertThatThrownBy(() -> {feature}Service.getById(999L))
          .isInstanceOf(AipubGrpcException.class);
    }
  }
}
```

**규칙**:
- `@DisplayName` 필수 — 한글로 행위를 설명.
- 메서드명: `given{Condition}_when{Action}_then{Result}`.
- `@Nested`로 메서드별 테스트 그룹화.
- BDD 스타일: `given` → `when` → `then` 주석으로 구분.
- Mockito BDD: `given().willReturn()` 사용.

### 9-2. gRPC 서비스 단위 테스트

```java
@ExtendWith(MockitoExtension.class)
class {Feature}GrpcServiceTest {

  @InjectMocks
  private {Feature}GrpcService grpcService;

  @Mock
  private {Feature}Service {feature}Service;

  @Mock
  private StreamObserver<Create{Feature}Response> responseObserver;

  @Test
  @DisplayName("gRPC create 요청이 주어지면 응답을 반환하고 완료한다")
  void givenValidRequest_whenCreate{Feature}_thenRespondAndComplete() {
    // given
    Create{Feature}Request request = Create{Feature}Request.newBuilder()
        .setTitle("제목")
        .setContent("내용")
        .build();
    {Feature} expected = {Feature}.builder().id(1L).title("제목").build();
    given({feature}Service.create(any())).willReturn(expected);

    // when
    grpcService.create{Feature}(request, responseObserver);

    // then
    verify(responseObserver).onNext(any(Create{Feature}Response.class));
    verify(responseObserver).onCompleted();
  }
}
```

---

## 예외 처리 패턴

새 기능을 추가할 때 `ErrorCodeTypeEnum`에 해당 리소스의 NOT_FOUND 코드를 추가한다.

**파일**: `backend-common/.../grpc/exception/ErrorCodeTypeEnum.java`

```java
// 404...
{FEATURE}_NOT_FOUND(Code.NOT_FOUND, "{Feature} '%s' not found"),
```

사용:
```java
throw new AipubGrpcException(ErrorCodeTypeEnum.{FEATURE}_NOT_FOUND, id);
```

---

## 경로 상수 등록

새 기능의 경로 상수를 `ResourcePathConstants`에 추가한다.

```java
public static final String {FEATURES} = "/{features}";
```

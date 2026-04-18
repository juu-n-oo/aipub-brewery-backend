# 재설계: 빌드 컨텍스트 확장 (AIPubVolume + 파일 업로드)

> 작성일: 2026-04-18  
> 상태: 설계 검토 중

---

## 1. 배경

현재 MVP에서는 Dockerfile에 `COPY`/`ADD`를 금지하고 있다. 이를 확장하여:

1. **AIPubVolume 데이터 참조**: 프로젝트의 AIPubVolume에 저장된 파일을 `COPY`로 이미지에 포함
2. **파일 직접 업로드**: `requirements.txt`, config 파일 등을 업로드하여 빌드 컨텍스트로 사용

---

## 2. 설계 개요

### 빌드 시 Kaniko Pod 구조

```
Kaniko Pod
├── initContainer: context-preparer
│   ├── /mnt/volumes/{volumeName}/    ← AIPubVolume PVC (ReadOnly)
│   ├── /mnt/uploaded/                ← 업로드 파일 PVC
│   └── /workspace/                   ← 빌드 컨텍스트 (EmptyDir, 여기에 파일을 복사)
│
└── container: kaniko
    └── /workspace/                   ← 빌드 컨텍스트
        ├── Dockerfile                ← ConfigMap
        ├── requirements.txt          ← 업로드 파일에서 복사됨
        └── data/models/weights.pt    ← AIPubVolume에서 복사됨
```

### 핵심 아이디어

1. **initContainer**가 AIPubVolume과 업로드 PVC에서 필요한 파일을 `/workspace/`(EmptyDir)로 복사
2. **Kaniko**는 `/workspace/`를 빌드 컨텍스트로 사용 → `COPY`가 정상 작동
3. AIPubVolume은 **ReadOnly**로 마운트하여 빌드가 기존 데이터를 변경하지 않도록 보장

---

## 3. 엔티티/CR 재설계

### 3.1 Dockerfile Entity (DB)

```java
@Entity
@Table(name = "dockerfiles",
       uniqueConstraints = @UniqueConstraint(columns = {"project", "username", "name"}))
public class Dockerfile {
    @Id @GeneratedValue
    private Long id;
    private String project;
    private String username;
    private String name;

    @Column(columnDefinition = "TEXT")
    private String content;              // Dockerfile 내용

    @OneToMany(cascade = ALL, orphanRemoval = true)
    private List<BuildContextFile> contextFiles;  // 업로드된 파일 메타데이터

    private Instant createdAt;
    private Instant updatedAt;
}
```

### 3.2 BuildContextFile Entity (DB — 신규)

업로드된 파일의 메타데이터를 관리한다. 실제 파일은 PVC 또는 오브젝트 스토리지에 저장.

```java
@Entity
@Table(name = "build_context_files")
public class BuildContextFile {
    @Id @GeneratedValue
    private Long id;

    @ManyToOne
    private Dockerfile dockerfile;

    private String fileName;          // e.g. "requirements.txt"
    private String targetPath;        // 빌드 컨텍스트 내 경로 e.g. "requirements.txt" or "configs/app.yaml"
    private Long fileSize;            // bytes
    private String storagePath;       // PVC 내 실제 저장 경로
    private Instant uploadedAt;
}
```

### 3.3 ImageBuild CR Spec 확장

```yaml
spec:
  dockerfileContent: |
    FROM pytorch/pytorch:2.1.0
    COPY requirements.txt /app/
    RUN pip install -r /app/requirements.txt
    COPY --from=volume data/models/weights.pt /app/models/
  targetImage: "harbor.aipub.io/pjw/my-pytorch:v1.0"
  pushSecretRef: "image-registry-secret-project-aipub-ten1010-io-pjw"

  # 신규 필드
  volumeMounts:                        # AIPubVolume 마운트 목록
    - volumeName: "data-storage"       # AIPubVolume CR 이름
      pvcName: "data-storage-43d77785" # resolve된 PVC 이름
      mountPath: "/mnt/volumes/data-storage"
      readOnly: true
      includes:                        # 빌드 컨텍스트로 복사할 파일/디렉토리
        - sourcePath: "models/weights.pt"
          targetPath: "data/models/weights.pt"

  buildContextPvcRef: "imagebuild-a1b2c3d4-context"  # 업로드 파일이 저장된 PVC
```

### 3.4 ImageBuild Request DTO 확장

```java
public class ImageBuildRequest {
    @NotNull
    private Long dockerfileId;

    @NotBlank
    private String targetImage;

    @NotBlank
    private String tag;

    private String pushSecretRef;

    // 신규 필드
    private List<VolumeMountRequest> volumeMounts;  // AIPubVolume 참조 목록
}

public class VolumeMountRequest {
    @NotBlank
    private String volumeName;      // AIPubVolume CR 이름

    private List<FileMapping> includes;  // 복사할 파일 매핑 (null이면 전체)
}

public class FileMapping {
    private String sourcePath;      // Volume 내 경로
    private String targetPath;      // 빌드 컨텍스트 내 경로
}
```

---

## 4. 파일 업로드 플로우

```
[User]
  │
  ├─ POST /api/v1/dockerfiles/{id}/files  (multipart upload)
  │   → backend-server가 파일을 PVC에 저장
  │   → BuildContextFile 레코드 생성
  │
  ├─ DELETE /api/v1/dockerfiles/{id}/files/{fileId}
  │   → 파일 삭제
  │
  └─ POST /api/v1/builds  (빌드 트리거)
      → ImageBuild CR 생성 (buildContextPvcRef 포함)
      → Controller가 initContainer에서 PVC 마운트 → /workspace/로 복사
```

### 파일 저장 방식 옵션

| 방식 | 장점 | 단점 |
|------|------|------|
| **Dockerfile별 PVC** | 격리 우수, 라이프사이클 명확 | PVC 수 증가 |
| **Project별 공유 PVC** | 리소스 효율적 | 경로 충돌 관리 필요 |
| **ConfigMap/Secret** | 별도 PVC 불필요 | 1MB 크기 제한 |
| **MinIO/S3** | 확장성 좋음 | 추가 인프라 필요 |

**권장**: Project별 공유 PVC (`brewery-context-{namespace}`)에 `dockerfiles/{dockerfileId}/` 하위에 파일 저장.
- 작은 파일(requirements.txt 등)은 이 방식으로 충분
- 대용량 데이터는 AIPubVolume 마운트로 해결

---

## 5. Dockerfile 검증 규칙 변경

### 현재 (MVP)
- `COPY`, `ADD` → 무조건 reject

### 변경 후
- `COPY`, `ADD` → **허용** (단, 참조하는 파일이 빌드 컨텍스트에 존재해야 함)
- 검증 로직:
  1. `COPY` 대상 파일이 업로드된 `contextFiles`에 존재하는지 확인
  2. `COPY --from=volume` 패턴으로 AIPubVolume 파일 참조 시, 해당 volumeMount가 요청에 포함되어 있는지 확인
  3. 외부 URL을 사용하는 `ADD http://...` 는 별도 정책으로 관리

---

## 6. KanikoJobFactory 변경사항

### initContainer 추가

```java
private V1Container contextPreparerContainer(ImageBuildResource cr) {
    // busybox 기반 initContainer
    // 1. AIPubVolume에서 includes에 해당하는 파일을 /workspace/로 복사
    // 2. 업로드 파일 PVC에서 /workspace/로 복사
    // cp 명령으로 처리
}
```

### Volume 추가

```java
// 기존
- dockerfile (ConfigMap)
- docker-config (push Secret)

// 신규
- workspace (EmptyDir) — 빌드 컨텍스트 조립용
- volume-{name} (PVC, readOnly) — AIPubVolume 마운트 (0~N개)
- build-context (PVC) — 업로드 파일 (선택적)
```

---

## 7. API 변경 요약

### 신규 엔드포인트

| Method | Path | 설명 |
|--------|------|------|
| POST | `/api/v1/dockerfiles/{id}/files` | 빌드 컨텍스트 파일 업로드 (multipart) |
| GET | `/api/v1/dockerfiles/{id}/files` | 업로드된 파일 목록 조회 |
| DELETE | `/api/v1/dockerfiles/{id}/files/{fileId}` | 업로드된 파일 삭제 |
| GET | `/api/v1/volumes?project={p}` | 프로젝트의 AIPubVolume 목록 조회 |

### 변경 엔드포인트

| Method | Path | 변경 내용 |
|--------|------|-----------|
| POST | `/api/v1/builds` | `volumeMounts` 필드 추가 |

---

## 8. 구현 우선순위

### Phase A: AIPubVolume 마운트 지원
1. ImageBuild CR spec에 `volumeMounts` 필드 추가
2. KanikoJobFactory에 initContainer + Volume 마운트 로직 추가
3. AIPubVolume PVC 이름 resolve 로직 (K8s API 호출)
4. Dockerfile 검증 규칙 완화 (COPY 허용)

### Phase B: 파일 업로드 지원
1. BuildContextFile Entity + Repository
2. 파일 업로드/삭제 API
3. Project별 빌드 컨텍스트 PVC 관리
4. KanikoJobFactory에 빌드 컨텍스트 PVC 마운트 추가

### Phase C: 통합
1. AIPubVolume + 업로드 파일이 혼합된 Dockerfile 지원
2. E2E 테스트

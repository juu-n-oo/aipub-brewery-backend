# 고도화: 빌드 컨텍스트 파일 참조 + Dockerfile 명령어 순서 모델링

> 작성일: 2026-04-18  
> 상태: 설계 단계  
> 선행 조건: Phase 2, 3-A, 3-B 완료

---

## 1. 목표

1. **Dockerfile 생성 시 COPY 대상 파일을 함께 지정**할 수 있도록 한다
   - 사용자가 직접 업로드한 파일
   - AIPubVolume(PVC)에 존재하는 파일
2. **ImageBuild Controller의 initContainer**에서 지정된 파일들을 빌드 컨텍스트에 포함시킨다
3. Dockerfile 명령어 간 **순서를 표현**할 수 있는 모델링을 제공한다

---

## 2. Dockerfile 명령어 순서 모델링

### 2.1 문제

일반적인 Dockerfile은 명령어의 순서가 중요하다:

```dockerfile
FROM pytorch/pytorch:2.1.0
COPY requirements.txt /app/           # ← 먼저 의존성 파일만 복사
RUN pip install -r /app/requirements.txt  # ← 의존성 설치 (캐시 레이어)
COPY . /app/                          # ← 나머지 소스 복사
CMD ["python", "main.py"]
```

`COPY requirements.txt`가 `COPY . /app/` 보다 앞에 와야 Docker 레이어 캐시가 효과적으로 동작한다. 현재 모델은 `content`(단일 텍스트)와 `contextFiles`(순서 없는 파일 목록)로 분리되어 있어, 어떤 파일이 Dockerfile의 어떤 위치에서 참조되는지를 표현하지 못한다.

### 2.2 설계 옵션

#### 옵션 A: content(텍스트) 유지 + contextFiles에 순서 부여

Dockerfile content는 그대로 텍스트로 유지하되, 각 contextFile에 `orderIndex`를 추가하여 Dockerfile 내 참조 순서를 기록한다.

```java
@Entity
public class BuildContextFile {
    private Long id;
    private Dockerfile dockerfile;
    private String fileName;
    private String targetPath;         // Dockerfile의 COPY에서 참조하는 경로
    private Long fileSize;
    private String storagePath;
    private String sourceType;         // "UPLOAD" | "AIPUB_VOLUME"
    private String volumeName;         // sourceType이 AIPUB_VOLUME일 때 AIPubVolume 이름
    private String volumeSourcePath;   // sourceType이 AIPUB_VOLUME일 때 Volume 내 경로
    private Integer orderIndex;        // Dockerfile 내 참조 순서
    private Instant uploadedAt;
}
```

- **장점**: 기존 구조와 호환, Dockerfile 텍스트 작성 자유도 높음
- **단점**: content와 contextFiles 간 정합성을 별도로 검증해야 함

#### 옵션 B: Dockerfile을 명령어 단위로 분해하여 저장 (구조화 모델)

Dockerfile을 텍스트가 아닌 명령어(Instruction) 리스트로 저장한다.

```java
@Entity
public class Dockerfile {
    private Long id;
    private String project;
    private String username;
    private String name;

    @OneToMany(cascade = ALL, orphanRemoval = true)
    @OrderColumn(name = "instruction_order")  // JPA가 순서를 자동 관리
    private List<DockerfileInstruction> instructions;
}

@Entity
public class DockerfileInstruction {
    private Long id;
    private Dockerfile dockerfile;
    private String type;              // "FROM", "RUN", "COPY", "ENV", "WORKDIR", "CMD", ...
    private String arguments;         // 명령어 인자 (e.g. "requirements.txt /app/")
    private Integer instructionOrder; // 순서 (0-based)

    // COPY/ADD일 때만 사용
    @ManyToOne
    private BuildContextFile contextFile;  // 참조하는 파일 (nullable)
}
```

- **장점**: 명령어 순서가 DB 레벨에서 보장, COPY와 파일의 관계가 명시적
- **단점**: 모델 복잡도 증가, 기존 텍스트 기반 편집 UX와 괴리, 임의의 Dockerfile 문법을 완벽히 표현하기 어려움

#### 옵션 C: content(텍스트) 유지 + 파일 선언은 별도 (권장)

Dockerfile content는 사용자가 자유롭게 작성하고, contextFiles는 빌드 컨텍스트에 포함할 파일 목록으로만 관리한다. Dockerfile 내 `COPY`/`ADD` 명령의 순서는 content 텍스트 자체에 의해 결정된다.

```java
@Entity
public class Dockerfile {
    private Long id;
    private String project, username, name;

    @Column(columnDefinition = "TEXT")
    private String content;           // 사용자가 작성한 전체 Dockerfile 텍스트

    @OneToMany(cascade = ALL, orphanRemoval = true)
    private List<BuildContextFile> contextFiles;  // 빌드 컨텍스트에 포함할 파일 선언
}

@Entity
public class BuildContextFile {
    private Long id;
    private Dockerfile dockerfile;

    private String sourceType;         // "UPLOAD" | "AIPUB_VOLUME"

    // 공통
    private String targetPath;         // 빌드 컨텍스트 내 경로 (= COPY의 src에 해당)

    // UPLOAD일 때
    private String fileName;           // 원본 파일 이름
    private Long fileSize;
    private String storagePath;        // 서버 저장 경로

    // AIPUB_VOLUME일 때
    private String volumeName;         // AIPubVolume CR 이름
    private String volumeSourcePath;   // Volume 내 소스 경로

    private Instant uploadedAt;
}
```

- **장점**: Dockerfile 텍스트 작성 자유도 100% 유지, 모델 단순, 기존 코드 변경 최소화
- **단점**: Dockerfile content 내 COPY가 참조하는 파일과 contextFiles의 정합성을 서버에서 검증해야 함
- **순서**: Dockerfile 텍스트 내 명령어 순서가 곧 빌드 순서이므로 별도 orderIndex 불필요

### 2.3 권장 방안: 옵션 C

Dockerfile은 텍스트 기반 편집이 핵심 UX이므로, 구조를 강제하기보다 **content 자유 작성 + 파일 선언 분리** 방식이 적합하다.

명령어 순서는 Dockerfile 텍스트 자체가 결정하며, `contextFiles`는 "이 Dockerfile을 빌드할 때 빌드 컨텍스트에 어떤 파일이 필요한지"를 선언하는 역할만 한다.

---

## 3. Dockerfile 생성 API 변경

### 3.1 요청 DTO

```java
public class DockerfileCreateRequest {
    @NotBlank private String project;
    @NotBlank private String username;
    @NotBlank private String name;
    @NotBlank private String content;

    private List<ContextFileDeclaration> contextFiles;  // 신규
}

public class ContextFileDeclaration {
    @NotBlank private String sourceType;      // "UPLOAD" | "AIPUB_VOLUME"
    @NotBlank private String targetPath;      // 빌드 컨텍스트 내 경로

    // AIPUB_VOLUME일 때
    private String volumeName;                // AIPubVolume CR 이름
    private String volumeSourcePath;          // Volume 내 소스 경로
}
```

파일 업로드는 별도 multipart 엔드포인트로 처리:

```
POST /api/v1/dockerfiles/{id}/files
Content-Type: multipart/form-data
- file: (binary)
- targetPath: "requirements.txt"
```

### 3.2 검증 로직

Dockerfile 생성/수정 시 서버에서 다음을 검증:

1. `content` 내 `COPY src dest` 구문을 파싱하여 참조하는 `src` 파일 목록 추출
2. 각 `src`가 `contextFiles`의 `targetPath`에 존재하는지 확인
3. 미존재 시 → 에러 응답 (어떤 파일이 누락되었는지 명시)
4. `contextFiles`에 선언되었지만 `content`에서 참조하지 않는 파일 → 경고 (에러는 아님)

### 3.3 예시 요청

```json
{
  "project": "pjw",
  "username": "joonwoo",
  "name": "pytorch-with-data",
  "content": "FROM pytorch/pytorch:2.1.0\nCOPY requirements.txt /app/\nRUN pip install -r /app/requirements.txt\nCOPY models/weights.pt /app/models/",
  "contextFiles": [
    {
      "sourceType": "UPLOAD",
      "targetPath": "requirements.txt"
    },
    {
      "sourceType": "AIPUB_VOLUME",
      "targetPath": "models/weights.pt",
      "volumeName": "data-storage",
      "volumeSourcePath": "trained/weights.pt"
    }
  ]
}
```

---

## 4. ImageBuild CR Spec 확장

### 4.1 CRD 변경

```yaml
spec:
  dockerfileContent: |
    FROM pytorch/pytorch:2.1.0
    COPY requirements.txt /app/
    RUN pip install -r /app/requirements.txt
    COPY models/weights.pt /app/models/
  targetImage: "harbor.aipub.io/pjw/my-pytorch:v1.0"
  pushSecretRef: "..."

  contextSources:                              # 신규
    - type: "UPLOAD"
      targetPath: "requirements.txt"
      uploadPvcRef: "brewery-context-pjw"      # 업로드 파일 PVC
      uploadStoragePath: "dockerfiles/1/requirements.txt"

    - type: "AIPUB_VOLUME"
      targetPath: "models/weights.pt"
      pvcName: "data-storage-43d77785"         # AIPubVolume의 PVC 이름
      volumeSourcePath: "trained/weights.pt"
```

### 4.2 Controller initContainer 동작

```
initContainer: context-preparer (busybox)
  Volumes:
    /workspace           ← EmptyDir (빌드 컨텍스트 조립)
    /mnt/dockerfile      ← ConfigMap (Dockerfile)
    /mnt/upload-pvc      ← brewery-context-pjw PVC (업로드 파일)
    /mnt/vol-data-storage ← data-storage-43d77785 PVC (AIPubVolume, ReadOnly)

  Commands:
    cp /mnt/dockerfile/Dockerfile /workspace/Dockerfile
    cp /mnt/upload-pvc/dockerfiles/1/requirements.txt /workspace/requirements.txt
    cp /mnt/vol-data-storage/trained/weights.pt /workspace/models/weights.pt

container: kaniko
  --context=dir:///workspace
  --dockerfile=/workspace/Dockerfile
```

---

## 5. 추가 고려사항

### 5.1 파일 크기/용량 제한

| 항목 | 제한 | 이유 |
|------|------|------|
| 단일 업로드 파일 크기 | 설정 가능 (기본 100MB) | 서버 메모리/대역폭 보호 |
| Dockerfile당 총 업로드 크기 | 설정 가능 (기본 500MB) | PVC 용량 관리 |
| AIPubVolume에서 복사할 파일 크기 | 제한 없음 (Volume 자체 용량) | 이미 할당된 리소스 |
| contextFiles 개수 | 설정 가능 (기본 50개) | CR spec 크기 제한 |

### 5.2 AIPubVolume 접근 권한 검증

- 사용자가 빌드 요청에 AIPubVolume을 지정할 때, 해당 Volume이 사용자의 Project namespace에 존재하는지 검증 필요
- AIPubVolume의 `status.readyCondition.status`가 `true`인지 확인 (PVC가 Bound 상태인지)
- AIPubVolume의 `status.pvcName`으로 실제 마운트할 PVC 이름 resolve

### 5.3 PVC ReadWriteMany 동시 접근

- AIPubVolume PVC는 `ReadWriteMany` (ontap-nas NFS) → Kaniko Pod에서 ReadOnly 마운트 가능
- 빌드 중 원본 Volume의 파일이 변경될 수 있음 → initContainer에서 **복사 후 사용**하므로 빌드 시점 스냅샷 보장
- 대용량 파일 복사 시 initContainer 실행 시간 증가 → timeout 설정 필요

### 5.4 업로드 파일 저장소 라이프사이클

- **저장 위치**: 프로젝트에 이미 존재하는 AIPubVolume의 PVC를 활용
- **저장 경로**: AIPubVolume PVC 내 `brewery/dockerfiles/{dockerfileId}/{targetPath}`
- **삭제 시점**: Dockerfile 삭제 시 cascade로 DB 레코드 삭제 + PVC 내 파일도 삭제
- **AIPubVolume 미존재 시**: 프론트엔드에서 애초에 COPY 관련 요청을 보내지 않음 (서버 측 검증도 수행)

### 5.5 Dockerfile content ↔ contextFiles 정합성

- **빌드 트리거 시 최종 검증**: content 내 COPY/ADD src 목록과 contextFiles targetPath를 대조
- **에디터 UX**: 프론트엔드에서 COPY 추가 시 파일 선택 UI를 연동하면 실수 방지
- **누락 파일 에러 메시지**: `COPY requirements.txt /app/ — 'requirements.txt' is not declared in contextFiles`

### 5.6 빌드 컨텍스트 크기와 빌드 시간

- initContainer에서 대용량 파일 복사 시 EmptyDir 용량 확인 필요 (노드 디스크 의존)
- EmptyDir에 `sizeLimit` 설정 권장 (e.g. `10Gi`)
- 대용량 모델 파일(수 GB)을 COPY하면 이미지 크기가 급증 → 사용자에게 경고 UI 제공
- 대용량 데이터는 빌드 시 COPY보다 Workspace에서 AIPubVolume을 런타임 마운트하는 것이 더 적절
  - Kaniko 빌드 컨텍스트에 포함: 이미지 용량 증가, 배포마다 데이터 포함
  - AIPubVolume 런타임 마운트: 이미지 경량 유지, 데이터 업데이트 시 재빌드 불필요

### 5.7 보안

- 업로드 파일 내용 검증: 실행 파일, 심볼릭 링크 등 위험 요소 차단
- AIPubVolume 경로 순회 공격 방지: `volumeSourcePath`에 `..` 포함 시 reject
- 업로드 `targetPath`에 절대 경로(`/`)나 경로 순회(`..`) 포함 시 reject

### 5.8 멱등성과 동시성

- 같은 Dockerfile에 같은 `targetPath`로 재업로드 시: 덮어쓰기 or 에러? → 덮어쓰기 권장
- 빌드 진행 중 contextFiles 변경 시: 이미 생성된 CR에는 영향 없음 (CR에 스냅샷 저장)
- 동일 AIPubVolume을 여러 빌드가 동시 참조: ReadOnly 마운트이므로 충돌 없음

---

## 6. AIPubVolume 파일 브라우저 API

프론트엔드에서 AIPubVolume PVC 내 파일/디렉토리를 탐색할 수 있도록 하는 API.
사용자가 COPY 대상 파일을 선택하기 위한 파일 브라우저 UI를 지원한다.

### 6.1 API 설계

#### Volume 목록 조회

```
GET /api/v1/volumes/{namespace}
```

프로젝트(namespace)의 AIPubVolume 목록을 반환한다. K8s API proxy로 `AIPubVolume` CR 목록을 조회.

```json
{
  "items": [
    {
      "name": "data-storage",
      "pvcName": "data-storage-43d77785",
      "capacity": "150Gi",
      "used": "0.00Gi",
      "ready": true
    }
  ]
}
```

#### 디렉토리 조회 (파일 브라우저)

```
GET /api/v1/volumes/{namespace}/{volumeName}/browse?path=/
GET /api/v1/volumes/{namespace}/{volumeName}/browse?path=/models/checkpoints
```

지정 경로의 파일/디렉토리 목록을 반환한다. `ls -la`와 유사.

```json
{
  "volumeName": "data-storage",
  "namespace": "pjw",
  "path": "/models",
  "entries": [
    {
      "name": "weights.pt",
      "type": "FILE",
      "size": 1073741824,
      "modifiedAt": "2026-04-17T12:00:00Z",
      "permissions": "-rw-r--r--"
    },
    {
      "name": "checkpoints",
      "type": "DIRECTORY",
      "modifiedAt": "2026-04-15T09:30:00Z",
      "permissions": "drwxr-xr-x"
    }
  ]
}
```

### 6.2 구현 방식: 기존 AIPubVolume Pod에 exec

AIPubVolume 생성 시 해당 PVC를 마운트한 busybox Pod이 **자동으로 생성**되어 `sleep infinity`로 상주한다.
별도 Pod을 만들 필요 없이 이 기존 Pod에 `exec`하여 `find` 명령을 실행하면 된다.

#### AIPubVolume Pod 특성

- **Pod 이름** = PVC 이름 = `{volumeName}-{uid prefix 8자리}` (e.g. `data-storage-43d77785`)
- **이미지**: `busybox:1.36.1`, `command: ["/bin/sh", "-c", "sleep infinity"]`
- **PVC 마운트 경로**: `/data` (ReadWrite)
- **Controlled By**: `AIPubVolume/{volumeName}`
- **상태**: AIPubVolume이 ready이면 Pod도 Running

따라서 `AIPubVolume.status.pvcName`이 곧 exec 대상 Pod 이름이다.

```
[backend-server]                           [Kubernetes]
      │                                        │
      ├─ 1. AIPubVolume CR 조회 ──────────────→│  status.pvcName resolve
      │     → pvcName = Pod 이름               │
      │                                        │
      ├─ 2. exec: find /data/{path} ... ─────→│  기존 상주 Pod에 직접 exec
      │                                        │
      ←─ 3. stdout 파싱 → JSON 변환 ←─────────│
      │                                        │
```

#### exec 명령어

```bash
find /data/{path} -maxdepth 1 -not -path /data/{path} \
  -printf '%f\t%y\t%s\t%T+\n'
```

`find -printf` 출력 예시:
```
requirements.txt	f	256	2026-04-17 12:00:00.0000000000+00:00
checkpoints	d	4096	2026-04-15 09:30:00.0000000000+00:00
```

### 6.3 고려사항

#### 성능

- Pod이 이미 Running이므로 exec만 수행 → **즉시 응답** (수백 ms 수준)
- 별도 Pod 생성/삭제 오버헤드 없음

#### 보안

- `path` 파라미터에 `..` 포함 시 reject (경로 순회 방지)
- Pod은 ReadOnly 마운트 → Volume 데이터 변경 불가
- exec 명령어에 사용자 입력을 직접 넣지 않고, 서버에서 안전하게 조합
- Pod은 `brewery.aipub.ten1010.io/purpose: volume-browser` 라벨로 식별 → 정리 용이

#### 에러 처리

| 상황 | 응답 |
|------|------|
| AIPubVolume 미존재 | 404 — `AIPubVolume not found: {volumeName}` |
| PVC 미준비 (readyCondition false) | 409 — `Volume is not ready` |
| 경로 미존재 | 404 — `Path not found: {path}` |
| 권한 없는 namespace | 403 |
| Pod 생성/exec 실패 | 500 — 상세 에러 포함 |
| timeout (30초 초과) | 504 — `Volume browse timed out` |

---

## 7. 구현 순서

### Step 1: AIPubVolume 파일 브라우저 API
- `GET /api/v1/volumes/{namespace}` — Volume 목록 조회
- `GET /api/v1/volumes/{namespace}/{volumeName}/browse?path=` — 디렉토리 조회
- 임시 Pod exec 기반 구현 (VolumeBrowserService)
- 경로 순회 방지 검증

### Step 2: BuildContextFile 엔티티 확장
- `sourceType` (UPLOAD / AIPUB_VOLUME), `volumeName`, `volumeSourcePath` 필드 추가
- DockerfileCreateRequest에 `contextFiles` 선언 필드 추가
- Dockerfile content ↔ contextFiles targetPath 정합성 검증

### Step 3: 파일 업로드 API
- `POST /api/v1/dockerfiles/{id}/files` (multipart) — AIPubVolume PVC에 저장
- `GET /api/v1/dockerfiles/{id}/files` (목록)
- `DELETE /api/v1/dockerfiles/{id}/files/{fileId}`

### Step 4: ImageBuild CR spec 확장
- `contextSources` 필드 추가 (CRD 변경)
- backend-server: 빌드 트리거 시 contextFiles → contextSources 변환
- AIPubVolume `status.pvcName` resolve

### Step 5: imagebuild-controller initContainer 구현
- KanikoJobFactory에 initContainer (busybox) + Volume 마운트 추가
- contextSources별 파일 복사 cp 명령 생성
- EmptyDir workspace 조립 → Kaniko context

### Step 6: Dockerfile 검증 규칙 변경
- COPY/ADD 금지 해제
- content ↔ contextFiles 정합성 검증으로 대체

### Step 7: 테스트
- Volume 파일 브라우저 API 테스트
- 업로드 파일 + COPY Dockerfile 빌드 E2E
- AIPubVolume 참조 + COPY Dockerfile 빌드 E2E
- 혼합 시나리오 (업로드 + Volume) E2E

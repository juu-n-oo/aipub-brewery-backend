# Kubernetes Integration — AIPub Brewery

> 작성일: 2026-04-18  
> AIPub Brewery가 연동하는 Kubernetes 리소스에 대한 정리

---

## 1. ImageBuild CR (Brewery 자체 CRD)

Brewery가 정의하고 관리하는 Custom Resource.

- **Group**: `brewery.aipub.ten1010.io`
- **Version**: `v1alpha1`
- **Scope**: Namespaced
- **CRD 파일**: `k8s/imagebuild-crd.yaml`

### Spec

| 필드 | 타입 | 필수 | 설명 |
|------|------|------|------|
| `dockerfileContent` | string | O | 인라인 Dockerfile 내용 |
| `targetImage` | string | O | 빌드 대상 이미지 (태그 포함, e.g. `harbor.aipub.io/pjw/my-image:v1.0`) |
| `pushSecretRef` | string | | push 인증용 Secret 이름 (미지정 시 기본 패턴 사용) |
| `volumeMounts` | array | | 빌드 시 마운트할 AIPubVolume 목록 (v2 확장) |
| `buildContextPvcRef` | string | | 빌드 컨텍스트 파일이 저장된 PVC 이름 (v2 확장) |

### Status

| 필드 | 타입 | 설명 |
|------|------|------|
| `phase` | enum | `Pending` → `Preparing` → `Building` → `Succeeded` / `Failed` |
| `startTime` | date-time | 빌드 시작 시각 |
| `completionTime` | date-time | 빌드 완료 시각 |
| `message` | string | 상태 메시지 |
| `imageDigest` | string | 빌드 성공 시 이미지 digest (e.g. `sha256:abc...`) |

---

## 2. AIPubVolume (기존 AIPub CRD)

AIPub 플랫폼이 관리하는 볼륨 CR. PV/PVC를 자동 생성한다.

- **Group**: `aipub.ten1010.io`
- **Version**: `v1alpha1`
- **Kind**: `AIPubVolume`
- **Scope**: Namespaced (Project namespace에 귀속)

### Spec

| 필드 | 타입 | 설명 |
|------|------|------|
| `capacity` | string | 볼륨 크기 (e.g. `150Gi`) |
| `storageClassName` | string | 스토리지 클래스 (e.g. `ontap-nas`) |

### Status (주요 필드)

| 필드 | 타입 | 설명 |
|------|------|------|
| `pvName` | string | 생성된 PV 이름 |
| `pvcName` | string | 생성된 PVC 이름 (e.g. `data-storage-43d77785`) |
| `readyCondition.status` | boolean | 볼륨 사용 준비 여부 |
| `used` | string | 현재 사용량 (e.g. `0.00Gi`) |
| `mountWorkloads` | array | 현재 마운트 중인 워크로드 목록 |

### PVC 특성

AIPubVolume이 생성하는 PVC는 다음과 같은 특성을 갖는다:

- **accessModes**: `ReadWriteMany` (여러 Pod에서 동시 마운트 가능)
- **storageClassName**: `ontap-nas` (NetApp ONTAP NAS — NFS 기반)
- **volumeMode**: `Filesystem`
- **Labels**:
  - `aipub.ten1010.io/userid`: 소유자 사용자 ID
  - `aipub.ten1010.io/username`: 소유자 사용자명
  - `aipub.ten1010.io/workload-kind`: `AIPubVolume`
  - `aipub.ten1010.io/workload-name`: AIPubVolume 이름

PVC 이름 패턴: `{aipubVolumeName}-{uid prefix 8자리}` (e.g. `data-storage-43d77785`)

### 상주 Pod (Volume Helper Pod)

AIPubVolume 생성 시 PVC를 마운트한 **busybox Pod이 자동 생성**되어 상주한다.

- **Pod 이름** = PVC 이름 = `{volumeName}-{uid prefix 8자리}` (e.g. `data-storage-43d77785`)
- **이미지**: `busybox:1.36.1`
- **명령**: `sleep infinity`
- **PVC 마운트 경로**: `/data`
- **Controlled By**: `AIPubVolume/{volumeName}`
- **라벨**: `aipub.ten1010.io/workload-kind: AIPubVolume`, `aipubvolumes.aipub.ten1010.io/owner: {volumeName}`

이 Pod은 항상 Running 상태이므로, Brewery에서 **`kubectl exec`로 직접 파일 목록을 조회**할 수 있다.
`AIPubVolume.status.pvcName`이 곧 exec 대상 Pod 이름이다.

### Brewery에서의 활용

**파일 브라우저**: 상주 Pod에 `find` 명령을 exec하여 Volume 내 파일/디렉토리 목록을 조회. 프론트엔드에서 COPY 대상 파일을 선택하는 UI에 사용.

**빌드 컨텍스트**: Kaniko 빌드 Pod에서 AIPubVolume의 PVC를 ReadOnly 마운트하면, 해당 볼륨에 저장된 데이터를 Dockerfile의 `COPY` 명령으로 이미지에 포함시킬 수 있다.

- PVC가 `ReadWriteMany`이므로 기존 워크로드(Workspace 등)와 Kaniko Pod이 동시 마운트 가능
- AIPubVolume의 `status.pvcName`으로 마운트할 PVC 이름 및 exec 대상 Pod 이름을 resolve

### Brewery 조회 API

```
GET /api/v1/volumes/{namespace}                              — Volume 목록
GET /api/v1/volumes/{namespace}/{volumeName}/browse?path=/   — 파일 브라우저
```

### AIPub 기존 조회 API

```
GET /api/v1alpha1/k8sproxy/apis/aipub.ten1010.io/v1alpha1/namespaces/{namespace}/aipubvolumes
```

---

## 3. Push Secret (기존 AIPub 리소스)

ImageBuild가 Harbor에 이미지를 push할 때 사용하는 인증 Secret.

- **이름 패턴**: `image-registry-secret-project-aipub-ten1010-io-{namespace}`
- **타입**: `kubernetes.io/dockerconfigjson`
- **키**: `.dockerconfigjson` (Harbor 레지스트리 인증 정보)
- **Scope**: 해당 Project namespace에 존재

Kaniko Pod에서는 이 Secret의 `.dockerconfigjson`을 `/kaniko/.docker/config.json`으로 마운트하여 push 인증에 사용한다.

---

## 4. Project (기존 AIPub CRD)

- **Group**: `project.aipub.ten1010.io`
- **Version**: `v1alpha1`
- **Kind**: `Project`
- **Scope**: Cluster

Project CR은 Kubernetes namespace와 1:1 대응된다. Brewery의 모든 리소스(Dockerfile, ImageBuild, Volume 등)는 Project namespace에 귀속된다.

### 관련 바인딩

| 바인딩 대상 | 필드 | 설명 |
|-------------|------|------|
| 사용자 | `spec.members[].aipubUser` | 프로젝트 멤버 목록 |
| 노드 | `spec.binding.nodes` | 할당된 노드 목록 |
| ImageHub | `spec.binding.imageHubs` | 바인딩된 ImageHub 목록 |

---

## 5. ImageHub (기존 AIPub CRD)

- **Group**: `project.aipub.ten1010.io`
- **Version**: `v1alpha1`
- **Kind**: `ImageHub`
- **Scope**: Cluster

Harbor 레지스트리 프로젝트와 연동된 CR. Brewery에서 Base 이미지 소스 및 빌드 결과물 push 대상으로 사용된다.

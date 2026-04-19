# AIPub Brewery 트러블슈팅 & 작업 이력

> 작성일: 2026-04-20
> 대상 기간: 2026-04-19 ~ 2026-04-20

---

## 1. ImageBuild Controller — Status 업데이트 415 에러

**증상**: ImageBuild CR 생성 후 Controller가 status를 업데이트하지 못하고 `415 Unsupported Media Type` 에러 발생.

**원인**: `CustomObjectsApi.patchNamespacedCustomObjectStatus()`에 `Map` 객체를 직접 전달하면 Content-Type이 `application/json`으로 전송됨. K8s API의 patch 엔드포인트는 `application/merge-patch+json` 등 patch 전용 Content-Type을 요구.

**해결**: `PatchUtils.patch()`를 사용하여 Content-Type `application/merge-patch+json`이 올바르게 설정되도록 변경.

**파일**: `imagebuild-controller/.../reconciler/ImageBuildStatusUpdater.java`

---

## 2. Kaniko Job — TLS 인증서 에러

**증상**: Kaniko 빌드 Job이 Harbor 레지스트리에 push 시 `x509: certificate signed by unknown authority` 에러.

**원인**: Harbor의 TLS 인증서가 자체 서명(self-signed)이라 Kaniko가 신뢰하지 못함.

**해결**: MVP 단계에서 `--insecure`, `--skip-tls-verify` 플래그를 Kaniko 컨테이너 args에 추가. 추후 CA 인증서를 `/kaniko/ssl/certs/`에 마운트하는 방식으로 전환 필요.

**파일**: `imagebuild-controller/.../reconciler/KanikoJobFactory.java`

---

## 3. 빌드 로그 조회 404

**증상**: `GET /api/v1/builds/{ns}/{name}/logs`가 404 반환.

**원인**: `podName = name + "-job"`으로 설정했는데, 이것은 Job 이름이지 Pod 이름이 아님. K8s Job이 생성하는 Pod 이름은 `{job-name}-{random-suffix}` 형태.

**해결**: `job-name` label selector로 Pod를 먼저 조회한 뒤, 실제 Pod 이름으로 로그를 가져오도록 변경.

**파일**: `backend-server/.../imagebuild/service/ImageBuildService.java`

---

## 4. Backend Pod list 권한 부족 (403)

**증상**: Pod 목록 조회 시 `pods is forbidden: User "system:serviceaccount:aipub:aipub-brewery-backend" cannot list resource "pods"` 에러.

**원인**: ClusterRole에 pods `get`만 있고 `list`가 없었음.

**해결**: ClusterRole에 pods `list` 권한 추가.

**파일**: `helm/backend-server/templates/clusterrole.yaml`

---

## 5. ImageBuild 상태가 Building에서 멈춤

**증상**: Kaniko Job이 완료되어도 ImageBuild CR의 phase가 `Building`에서 변하지 않음.

**원인**: `ImageBuildWatcher`가 ImageBuild CR의 이벤트만 감시. Job이 완료되어도 ImageBuild CR에 변경이 없으면 reconcile이 트리거되지 않음.

**해결**: `JobWatcher` 클래스를 새로 추가. `app.kubernetes.io/managed-by=aipub-brewery-controller` 라벨이 붙은 Job의 MODIFIED 이벤트를 감시하여, Job 상태 변경 시 해당 ImageBuild CR의 reconcile을 트리거.

**파일**:
- (신규) `imagebuild-controller/.../reconciler/JobWatcher.java`
- `helm/imagebuild-controller/templates/clusterrole.yaml` — jobs에 `list` 권한 추가

---

## 6. Volume 파일 탐색 — Exec WebSocket 403

**증상**: `GET /api/v1/volumes/{ns}/{name}/browse`가 500 에러. 로그에 `Expected HTTP 101 response but was '403 Forbidden'`.

**원인 (복합적)**:

### 6-1. RBAC: pods/exec에 get 권한 누락
`kubernetes-client-java`의 `Exec` 클래스는 내부적으로 `connectGetNamespacedPodExec` (GET 요청)을 사용. K8s에서 connect 서브리소스의 GET은 RBAC verb `get`으로 매핑되는데, ClusterRole에는 `create`만 있었음.

**해결**: `pods/exec`에 `get` 권한 추가.

### 6-2. WebSocket에 Bearer 토큰 미전달
`ClientBuilder.cluster().build()`는 인증을 OkHttp interceptor로만 설정. `WebSockets.stream()`은 `ApiClient.buildRequest()`로 request를 생성하는데, 이 메서드는 `authentications` 맵에서 `BearerToken`을 읽음. Interceptor 방식으로만 토큰이 설정되어 있으면 `buildRequest()` 단계에서 Authorization 헤더가 누락됨.

**해결**: `KubernetesConfiguration`에서 IN_CLUSTER 모드일 때 서비스 어카운트 토큰을 `client.setApiKey()` / `client.setApiKeyPrefix("Bearer")`로 명시적 설정.

**파일**: `backend-server/.../common/config/KubernetesConfiguration.java`

### 6-3. Exec 클래스의 레이스 컨디션
`Exec.exec()`는 WebSocket을 비동기로 시작 후 즉시 `ExecProcess`를 반환. `getInputStream()` 호출 시 WebSocket이 아직 열리지 않았거나 (403으로) 이미 닫혀서 `IllegalStateException` 발생.

**해결**: `Exec` 클래스 사용을 중단. `WebSockets.stream()` + `WebSocketStreamHandler`를 직접 사용하되, WebSocket 연결 전에 `handler.getInputStream(1)`, `handler.getInputStream(2)`를 미리 호출하여 PipedInputStream/PipedOutputStream 쌍을 사전 생성. 데이터 유실 방지.

**파일**: `backend-server/.../volume/service/VolumeBrowserService.java`

---

## 7. Volume 파일 탐색 — busybox find -printf 미지원

**증상**: Exec가 성공해도 `find -printf` 명령이 실패.

**원인**: AIPubVolume Pod는 busybox 이미지를 사용하는데, busybox의 `find`는 GNU `-printf` 옵션을 지원하지 않음.

**해결**: `find -printf` → `ls -lan`으로 변경하고, 파싱 로직도 `ls -lan` 출력 형식에 맞게 변경.

**파일**: `backend-server/.../volume/service/VolumeBrowserService.java`

---

## 8. Volume 파일 탐색 — 루트 경로 탐색 지원

**변경 전**: 볼륨 마운트 경로(`/data`)만 탐색 가능. `path=/`가 `/data`로 매핑됨.

**변경 후**: Pod의 루트(`/`)부터 자유롭게 탐색 가능. `path=/`이면 루트, `path=/data`이면 볼륨 마운트 경로.

**파일**: `backend-server/.../volume/service/VolumeBrowserService.java`

---

## 9. PVC 기반 빌드 컨텍스트 지원 (신규 기능)

**배경**: MVP 초기에는 `COPY`/`ADD` 없는 Dockerfile만 지원. 사용자가 PVC에 있는 파일을 이미지에 포함시키고 싶은 요구사항 발생.

**구현**:
- `ImageBuildRequest`에 `buildContextPvc`, `buildContextSubPath` 필드 추가
- `ImageBuildSpec` (backend-server, imagebuild-controller 양쪽)에 동일 필드 추가
- `KanikoJobFactory`에서 PVC 지정 시:
  - Dockerfile ConfigMap → `/kaniko-config/Dockerfile`에 마운트
  - PVC → `/workspace`에 `readOnly: true`로 마운트 (빌드 컨텍스트)
  - Kaniko args: `--dockerfile=/kaniko-config/Dockerfile --context=dir:///workspace`
- PVC 미지정 시: 기존 동작 유지 (ConfigMap이 `/workspace`에 마운트)
- `forbidden-instructions`에서 `COPY`는 허용, `ADD`만 거부

**파일**:
- `backend-server/.../imagebuild/dto/ImageBuildRequest.java`
- `backend-server/.../imagebuild/cr/ImageBuildSpec.java`
- `backend-server/.../imagebuild/service/ImageBuildService.java`
- `imagebuild-controller/.../cr/ImageBuildSpec.java`
- `imagebuild-controller/.../reconciler/KanikoJobFactory.java`
- `backend-server/src/main/resources/application.yaml`

---

## 10. BuildContextFileController 파일 저장 위치 문제 (미해결)

**현재 상태**: 업로드된 빌드 컨텍스트 파일이 백엔드 Pod의 로컬 파일시스템(`./build-context-storage/`)에 저장됨.

**문제점**:
1. Pod 재시작 시 파일 소실 (ephemeral storage)
2. Kaniko Job Pod에서 해당 파일에 접근 불가 (다른 Pod의 로컬 파일시스템)

**현재 우회**: PVC 기반 빌드 컨텍스트 방식 사용 (항목 9). 사용자가 AIPubVolume(PVC)에 미리 파일을 넣어두고, 빌드 시 해당 PVC를 직접 마운트.

**향후 개선 방안**: 업로드 파일을 PVC에 저장하거나, 빌드 시 ConfigMap으로 Kaniko Pod에 전달하는 방식 검토 필요.

---

## RBAC 최종 상태

### backend-server ClusterRole

| Resource | Verbs |
|----------|-------|
| `imagebuilds.brewery.aipub.ten1010.io` | get, list, create |
| `pods` | get, list |
| `pods/log` | get |
| `pods/exec` | get, create |
| `aipubvolumes.aipub.ten1010.io` | get, list |

### imagebuild-controller ClusterRole

| Resource | Verbs |
|----------|-------|
| `imagebuilds.brewery.aipub.ten1010.io` | get, list, watch, patch |
| `imagebuilds.brewery.aipub.ten1010.io/status` | get, patch |
| `configmaps` | get, create |
| `jobs` | get, list, create, watch |
| `events` | create |

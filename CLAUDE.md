# AIPub Brewery (가칭) 서비스 기획서

> 작성일: 2026-04-17  
> 버전: v0.3 (MVP 빌드 컨텍스트 제약 확정)  
> 프로젝트 코드네임: **AIPub Brewery**

---

## 1. 개요

### 1.1 배경

AIPub은 쿠버네티스 기반의 ML 개발 플랫폼으로, Workspace, Operation, Job 등 다양한 CR 기반 워크로드를 제공하며, 이미지 레지스트리는 Harbor 기반의 ImageHub를 통해 관리되고 있다. 그러나 이미지의 **생성(빌드)** 영역은 여전히 사용자의 로컬 환경에 의존하고 있어 다음과 같은 페인포인트가 존재한다.

- **로컬 환경 의존성**: Dockerfile 수정 → 빌드 → 푸시 사이클을 모두 로컬에서 수행해야 하므로, 이미지 버전이 바뀔 때마다 동일한 반복 작업이 발생한다.
- **팀 단위 자산화 불가**: 개인 로컬에 흩어진 Dockerfile은 팀 내 공유·재사용이 어렵다.
- **환경 재현성 저하**: 빌드 환경이 사용자마다 달라 "내 로컬에선 되는데" 이슈가 발생한다.
- **리소스 비효율**: ML 이미지는 종종 수 GB~수십 GB에 이르며, 로컬 디스크/네트워크 대역폭에 부담을 준다.
- **Workspace Commit 기능의 대체 필요**: 현재 Workspace는 commit 기능을 제공하지만, 비권장 경로이므로 이를 대체할 표준화된 이미지 생성 경로가 필요하다.

### 1.2 서비스 목적

AIPub 플랫폼 내에 **웹 기반 Dockerfile 편집 및 이미지 빌드/관리 서비스(AIPub Brewery)**를 구축하여, 사용자가 로컬 환경 없이도 이미지를 설계·빌드·배포할 수 있도록 한다.

### 1.3 기대 효과

- **생산성**: 로컬 빌드/푸시 작업 제거, 사이클 단축.
- **협업성**: Project(namespace) 단위로 Dockerfile 및 이미지 자산을 공유.
- **표준화**: Workspace Commit을 대체할 표준 이미지 생성 경로 확보.
- **인프라 활용도**: 빌드 작업을 k8s Job으로 수행하여 로컬 자원 부담 제거.

---

## 2. 주요 사용자 및 페르소나

### 2.1 Primary: ML 개발자 / 연구자

- 주요 작업: 모델 학습 환경 구성, 실험용 이미지 생성, Workspace/Job용 이미지 준비.
- 기대 경험: 로컬 환경 세팅 없이 브라우저만으로 이미지를 만들고, 빌드된 이미지를 바로 Workspace/Job에서 사용.

### 2.2 Secondary: 인프라 관리자

- 주요 작업: 팀별 리소스 할당, 이미지 정책 관리, 보안 준수 확인.
- 기대 경험: Project별 빌드 리소스 관리 및 이미지 저장소 현황 파악.

---

## 3. 핵심 기능 (Core)

### 3.1 웹 기반 Dockerfile Editor

- 브라우저에서 동작하는 코드 에디터(Monaco Editor 등) 기반 Dockerfile 작성 환경.
- Dockerfile 구문 하이라이팅 지원.
- 작성된 Dockerfile은 사용자별(+Project별)로 저장/관리된다.

### 3.2 Dockerfile 저장 및 관리

- 저장 단위: `Project / User / DockerfileName`.
- 저장된 Dockerfile은 언제든 불러와 재편집 및 재빌드 가능.
- Project 단위 귀속으로 팀 내 공유 기반 확보.

### 3.3 이미지 빌드 (Kaniko 기반 k8s Job)

- 사용자가 "빌드" 버튼을 누르면, 백엔드가 **k8s CR**을 생성하고 컨트롤러가 **Kaniko Pod/Job**을 기동하여 이미지를 빌드한다.
- **빌드 엔진: Kaniko 고정**
    - Docker daemon이 필요 없어 k8s 친화적.
    - Rootless 실행 가능으로 보안 이점.
    - 컨테이너 레이어 기반 캐시 지원.
- **MVP 빌드 컨텍스트 제약**: 로컬 파일을 참조하는 `COPY`, `ADD` 등의 명령을 포함하지 않은 Dockerfile만 허용한다. 즉 **`FROM` + `RUN` + `ENV` / `WORKDIR` / `CMD` / `ENTRYPOINT` 등 컨텍스트가 불필요한 지시자 중심**의 Dockerfile만 MVP에서 지원한다.
    - 이 제약으로 MVP에서는 별도의 빌드 컨텍스트 파일 업로드/전달이 불필요하며, Dockerfile 자체를 ConfigMap 등의 형태로 Kaniko Pod에 전달한다.
    - Python 의존성은 `RUN pip install <packages>` 형태로 직접 명시하거나, `RUN pip install -r <URL>` 처럼 원격에서 fetch하는 방식으로 우회 가능.
    - `COPY`/`ADD`를 포함한 Dockerfile은 서버에서 **사전 검증(reject)** 처리하여 명확한 에러 메시지로 안내한다.
- 빌드 진행 상황은 웹 UI에서 실시간 로그로 확인 가능.
- 빌드 Job은 해당 Project의 namespace에서 스케줄링된다.

### 3.4 이미지 푸시 (Harbor 연동)

- 빌드가 성공하면 해당 Project의 ImageHub(Harbor)로 자동 push.
- 태그 전략 (MVP): `project/image-name:태그` 형태로 사용자가 지정.
- 푸시 시 기존 AIPub의 imagePullSecret과 동일한 credential 체계를 활용.

### 3.5 Base 이미지 소스 (MVP 범위)

- **MVP**: 내부 **Harbor ImageHub**에 저장된 이미지만 Base 이미지로 사용 가능.
- 사용자는 Project가 접근 권한을 가진 레지스트리의 이미지 목록에서 Base 이미지를 선택할 수 있다.
- *외부 NGC 등 외부 레지스트리 연동은 차후 고도화 작업에서 추가.*

---

## 4. 시스템 아키텍처

### 4.1 구성 요소

| 구성요소 | 설명 |
|---|---|
| **aipub-brewery-web** | 신규 프론트엔드. Dockerfile 에디터, 빌드 트리거, 빌드 결과/로그 조회 UI. |
| **aipub-web-server** | 기존 AIPub 백엔드. Brewery API가 추가되어 Dockerfile 저장 및 k8s CR 생성을 담당. |
| **ImageBuild CR + Controller** | 신규 CR. 컨트롤러가 CR을 watch하여 Kaniko 기반 k8s Job을 생성/감시. |
| **Kaniko Job (Pod)** | 실제 이미지 빌드 및 Harbor push 수행. |
| **Harbor (기존 ImageHub)** | Base 이미지 제공 및 빌드 산출물 저장. |

### 4.2 데이터 흐름

```
[User Browser]
  └─ aipub-brewery-web
      ├─ Dockerfile 작성/저장 요청
      │   └─ aipub-web-server: Dockerfile 저장
      │
      ├─ 빌드 Run 요청
      │   └─ aipub-web-server
      │       └─ k8s ImageBuild CR 생성
      │           └─ Controller → Kaniko Job 기동
      │               ├─ Base Image Pull (Harbor)
      │               ├─ Build
      │               └─ Push → Harbor ImageHub
      │
      └─ 빌드 결과/로그 조회
          └─ aipub-web-server → k8s (CR status, Pod logs) → UI
```

### 4.3 신규 CR: `ImageBuild`

- **Spec (MVP)**: `dockerfileContent` (inline string), `targetImage` (repo + tag), `pushSecretRef`.
- **Status (MVP)**: `phase` (Pending / Running / Succeeded / Failed), `startTime`, `completionTime`, `message`, `imageDigest` (성공 시).
- 컨트롤러는 `dockerfileContent`를 ConfigMap으로 렌더링 후 Kaniko Pod에 마운트하여 빌드 수행. 별도 빌드 컨텍스트 볼륨은 불필요.
- AIPub의 기존 CR 스타일(Volume, Workspace, Operation, Job, ChainJob)과 일관성 있게 설계.

---

## 5. 기존 AIPub 시스템과의 연계 (MVP)

| 연계 대상 | 연계 방식 |
|---|---|
| **AIPub User / OIDC** | 기존 유저 시스템으로 인증. 빌드 Job은 해당 Project namespace에서 실행. |
| **AIPub Project (namespace)** | Dockerfile, 빌드 CR, 푸시된 이미지 모두 Project 범위에 귀속. |
| **ImageHub (Harbor)** | Base 이미지 소스 및 push 대상. 기존 imagePullSecret 체계 재사용. |

> Workspace / Job / Volume 등 다른 워크로드와의 직접 연계(빌드 후 Workspace 바로 생성 등)는 **고도화 범위**로 둔다.

---

## 6. MVP 범위 (Phase 1)

> MVP는 **가장 기본적인 플로우의 End-to-End 동작 검증**을 목표로 한다. 이후 검증이 완료되면 고도화를 진행한다.

### 6.1 MVP 핵심 요구사항

**(1) aipub-brewery-web**
- Dockerfile을 UI에서 작성할 수 있다.
- 작성 중 `COPY` / `ADD` 등 MVP에서 미지원되는 지시자가 포함되면 UI 레벨에서 경고를 표시한다.
- 작성한 Dockerfile을 저장할 수 있다.
- 저장된 Dockerfile을 불러와 재편집할 수 있다.
- 이미지 빌드 Run을 트리거할 수 있다.
- 빌드의 진행 상태 및 결과(성공/실패, 로그, 푸시된 이미지 정보)를 확인할 수 있다.

**(2) aipub-web-server**
- Dockerfile을 저장/조회/수정/삭제할 수 있다.
- **Dockerfile 저장 및 빌드 요청 시, `COPY` / `ADD` 등 로컬 컨텍스트를 참조하는 지시자가 포함되어 있으면 reject 한다.**
- 빌드 Run 요청 시 k8s에 `ImageBuild` CR을 생성한다 (Dockerfile 내용은 inline으로 전달).
- 생성된 CR의 status와 빌드 Pod의 로그를 조회하여 프론트엔드에 제공한다.
- 빌드 성공 시 이미지가 해당 Project의 Harbor ImageHub에 push된 상태여야 한다.

**(3) ImageBuild Controller + Kaniko Job**
- `ImageBuild` CR을 watch하여 Kaniko Pod/Job을 생성한다.
- Kaniko가 Base 이미지를 Harbor에서 pull → 빌드 → Harbor로 push 하는 과정을 수행한다.
- 빌드 결과를 CR status에 반영한다.

### 6.2 MVP 범위 제외 항목 (명시적 Out-of-Scope)

- **빌드 컨텍스트 파일 전달 (COPY / ADD)**: 로컬 파일을 이미지에 포함하는 플로우는 MVP에서 지원하지 않음. (requirements.txt, 소스코드 COPY 등 포함 → 고도화 범위)
- Dockerfile 버전 관리(히스토리, diff, 롤백)
- 템플릿 라이브러리, Quick Build Wizard, Lint
- 외부 레지스트리(NGC 등) 연동
- 빌드 캐시 최적화, 빌드 큐/쿼터
- 취약점 스캔 결과 연동, 이미지 계보/사용처 추적
- Workspace/Job과의 연계 생성 플로우
- Project 단위 빌드 정책, 감사 로그, 사용량 대시보드
- LLM Assistant, 자동 재빌드, 스케줄 빌드 등

### 6.3 MVP 성공 기준 (Definition of Done)

- 사용자가 웹 UI에서 Dockerfile 작성 → 저장 → 빌드 Run → 빌드 결과 확인까지의 플로우가 End-to-End로 동작한다.
- 빌드 성공 시 Harbor ImageHub에 이미지가 정상 push되고, 해당 이미지를 기존 AIPub 워크로드(Workspace 등)에서 정상 사용할 수 있다.
- 빌드 실패 시 UI에서 로그 기반 원인 파악이 가능하다.
- Project 권한 체계 내에서만 Dockerfile 및 빌드 접근이 가능하다.

---

## 7. 향후 고도화 작업 리스트 (Post-MVP Backlog)

> MVP 검증 완료 이후 단계적으로 도입을 검토할 기능들. 이미지를 **자산(asset)**으로 관리한다는 관점과 **ML 개발자 생산성** 관점에서 확장 가능한 기능들을 백로그로 정리한다.

### 7.1 Dockerfile 관리 고도화

- **Dockerfile 버전 관리 (Git-like)**: 수정 이력 커밋, diff, 롤백.
- **Dockerfile 템플릿 라이브러리**: `PyTorch + CUDA`, `TensorFlow`, `JupyterLab + GPU`, `HuggingFace`, `vLLM` 등 ML 특화 템플릿. 사내 템플릿 등록 지원.
- **Fork / Clone**: 팀원의 Dockerfile 또는 사내 템플릿을 복제하여 시작점으로 활용.
- **Lint & Best Practice 검증**: Hadolint 연동, ML 특화 룰(CUDA-torch 호환성 등).
- **Import from Git / 파일 업로드**: 기존 Dockerfile 임포트.
- **LLM 기반 Dockerfile Assistant**: 자연어로 Dockerfile 초안 생성, 최적화 제안.

### 7.2 이미지 메타 / 계보(Lineage) 관리

- **이미지 ↔ Dockerfile ↔ 빌드 로그 연결**: 재현성 확보의 핵심.
- **이미지 사용처 표시 (Reverse Dependency)**: 어떤 Workspace/Operation/Job이 이 이미지를 쓰는지 가시화.
- **이미지 비교 (Diff)**: 레이어 diff, 설치 패키지 diff (`pip list`, `dpkg -l`).
- **이미지 크기 / 레이어 분석**: Dive 스타일 시각화.
- **취약점 스캔 결과 연동**: Harbor Trivy 결과 노출 및 정책 위반 이미지 표시.
- **태깅 정책 & Retention**: 자동 GC, 태그 컨벤션 강제.

### 7.3 ML 개발자 편의 기능

- **Quick Build Wizard**: UI 선택(CUDA / 프레임워크 / 패키지)만으로 Dockerfile 자동 생성.
- **requirements.txt / environment.yml 통합**: 의존성 파일 업로드 시 Dockerfile에 자동 삽입.
- **빌드 직후 연계 워크로드 생성**: "이 이미지로 Workspace 바로 만들기 / Job 실행하기".
- **Workspace Commit 대체 플로우**: Workspace의 설치 이력을 Dockerfile로 추출하는 마법사.
- **실험 ID 기반 태깅**: MLflow / W&B 등과 연동한 자동 태깅.
- **GPU 아키텍처별 검증**: `TORCH_CUDA_ARCH_LIST` 권장값 안내, A100/H100/L40S 호환성 체크.
- **팀 추천 이미지 / 즐겨찾기**: 온보딩 시간 단축.
- **Dockerfile 공유 링크**: Project 멤버 간 URL 공유.

### 7.4 빌드 파이프라인 고도화

- **빌드 컨텍스트 파일 전달 지원 (COPY / ADD)**: 사용자가 `requirements.txt`, 소스 코드 등을 업로드하면 빌드 컨텍스트로 전달. 전달 방식 후보: 파일 업로드 → ConfigMap/PVC, AIPub Volume 참조, SFTPServer/Git 연계 등.
- **외부 레지스트리 연동(NGC 등)**: NGC Catalog 이미지를 Base로 선택 가능.
    - NGC API Key의 Project 단위 중앙 관리.
    - Harbor proxy-cache 활용 검토.
- **빌드 캐시**: Kaniko 원격 캐시(Harbor OCI artifact 또는 PVC) 활용.
- **빌드 큐 및 리소스 쿼터**: Project별 동시 빌드 수 제한, CPU/메모리 쿼터.
- **빌드 로그 영구 보관 및 검색**: 장기 보관, 검색 기능.
- **주기적 자동 재빌드 (Base Image Watch)**: 베이스 이미지 업데이트 시 파생 이미지 자동 재빌드 + 알림.
- **스케줄 빌드**: AIPub ChainJob과 연계하여 주기적 재빌드.
- **Multi-stage 빌드 시각화**: 각 stage를 UI에서 표현.
- **멀티 아키텍처 빌드**: ARM 지원 필요 시.

### 7.5 운영 / 거버넌스 고도화

- **Project 단위 빌드 정책**: 허용 Base 이미지 화이트리스트, root 금지, `ADD from URL` 금지, 이미지 최대 크기 제한 등.
- **빌드 사용량 대시보드**: Project별 빌드 시간, 저장소 사용량 추이.
- **감사 로그(Audit Log)**: Dockerfile 수정 / 빌드 실행 / 푸시 이력 기록.

### 7.6 AIPub 생태계 연계 고도화

- **AIPub Volume / brewery 볼륨 연계**: 빌드 컨텍스트(소스 코드, 리소스)를 AIPub Volume에서 로드.
- **SFTPServer 연계**: SFTP로 업로드한 파일을 빌드 컨텍스트로 활용.
- **ChainJob 파이프라인 편입**: 빌드 Job을 ChainJob의 한 단계로 사용(빌드 → 학습 → 평가).

---

## 8. 오픈 이슈 / 검토 필요 사항

- **빌드 Pod 리소스 격리**: GPU 노드풀과 분리된 CPU 빌드 전용 노드풀 사용 여부.
- **태그 정책**: MVP에서 사용자가 자유 지정할지, 최소 규칙(예: `latest` 금지)을 강제할지.
- **동시 빌드 제한**: MVP 초기에는 Project 단위 최대 N개 정도의 단순 제한 검토.
- **Dockerfile 지시자 화이트리스트 범위**: MVP에서 `COPY`/`ADD`는 금지 확정. 그 외 `ARG`, `LABEL`, `USER`, `HEALTHCHECK` 등 지시자는 허용할지 사전 정의 필요.
- **Workspace commit 기능의 최종 deprecate 시점**: 고도화 단계에서 Workspace Commit 대체 플로우가 완성된 이후에 결정.

---

## 9. 성공 지표 (MVP 이후 측정)

- 로컬 빌드 대비 Brewery 기반 빌드 채택률 (Project 단위)
- 빌드 평균 소요 시간, 성공률
- Workspace commit 사용량 감소 추이
- Brewery로 생성된 이미지가 실제 AIPub 워크로드(Workspace/Job/Operation)에서 사용된 비율

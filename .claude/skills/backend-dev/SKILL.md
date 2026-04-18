---
name: backend-dev
description: "aipub-backend 멀티모듈 프로젝트의 기능 개발, API 추가, 버그 수정을 에이전트 팀으로 조율하는 오케스트레이터. proto 정의 → gRPC 구현 → Gateway REST 엔드포인트 → 테스트 → 코드 품질 검증의 전체 개발 파이프라인을 자동화. '새 API 추가', '기능 개발', 'gRPC 서비스 구현', 'REST 엔드포인트 추가', '멀티모듈 변경', '백엔드 기능', 'proto 추가' 등의 요청 시 반드시 이 스킬을 사용할 것. 단순한 단일 파일 수정이나 코드 리뷰가 아닌, 여러 모듈에 걸친 구현 작업에 특히 유용."
---

# Backend Dev Orchestrator

aipub-backend 멀티모듈 프로젝트의 개발 파이프라인을 에이전트 팀으로 조율하는 오케스트레이터.

## 실행 모드: 에이전트 팀

파이프라인 + 생성-검증 복합 패턴. 분석 → 구현 ↔ 검증의 피드백 루프를 팀원 간 직접 통신으로 실현한다.

## 에이전트 구성

| 팀원 | 에이전트 타입 | 역할 | 출력 |
|------|-------------|------|------|
| analyst | codebase-analyst | 코드베이스 분석, 패턴 질의 응답 | `_workspace/01_analyst_report.md` |
| implementer | backend-implementer | 멀티모듈 코드 구현 | 소스 파일 + `_workspace/02_implementer_changes.md` |
| qa | qa-validator | 빌드/테스트/포맷/정합성 검증 | `_workspace/03_qa_report.md` |

## 팀 통신 구조

```
analyst ──분석 결과──→ implementer
analyst ←──패턴 질문── implementer
analyst ←──컨벤션 질문── qa

implementer ──모듈 완성 알림──→ qa (점진적 QA)
implementer ←──빌드/테스트 에러── qa
implementer ──수정 완료──→ qa (재검증 요청)

리더 ←── 각 팀원의 진행 보고
```

핵심: implementer ↔ qa 간 직접 피드백 루프로, 리더를 거치지 않고 빠르게 문제를 해결한다.

## 워크플로우

### Phase 1: 준비
1. 사용자 입력 분석 — 어떤 기능/변경이 필요한지 파악
2. 작업 디렉토리에 `_workspace/` 생성
3. 변경 범위 판단 — 멀티모듈인지 단일 모듈인지

**단일 모듈 변경** (proto 변경 없이 한 모듈만 수정): 분석 작업을 경량화하고 implementer + qa 2인 팀으로 구성 가능.

### Phase 2: 팀 구성

```
TeamCreate(
  team_name: "backend-dev-team",
  members: [
    {
      name: "analyst",
      agent_type: "codebase-analyst",
      model: "opus",
      prompt: "aipub-backend 코드베이스를 분석하라.

요청: {사용자 요청}

.claude/agents/codebase-analyst.md를 읽고 역할과 프로토콜을 따르라.
_workspace/01_analyst_report.md에 분석 보고서를 작성하라.

분석 완료 후 implementer에게 SendMessage로 핵심 발견을 요약 전달하라.
이후 implementer와 qa의 패턴 질문에 응답하라."
    },
    {
      name: "implementer",
      agent_type: "backend-implementer",
      model: "opus",
      prompt: "aipub-backend 코드를 구현하라.

요청: {사용자 요청}

.claude/agents/backend-implementer.md를 읽고 역할과 프로토콜을 따르라.
analyst로부터 분석 결과를 수신한 뒤 구현을 시작하라.
구현을 시작하기 전에 반드시 다음 두 파일을 읽어라:
1. .claude/skills/backend-dev/references/code-templates.md — DDD 골든 패턴. 모든 레이어(도메인 모델, 레포지토리, 서비스, 엔티티, gRPC, 게이트웨이)의 클래스 구조, 패키지 배치, 네이밍을 이 파일과 동일하게 구현하라. analyst가 찾아온 기존 코드 패턴과 골든 패턴이 다르면 골든 패턴을 우선한다.
2. .claude/skills/backend-dev/references/grpc-patterns.md — gRPC 개발 패턴.

구현 순서: backend-protocol → backend-api → backend-gateway
모듈 단위로 구현이 완료될 때마다 qa에게 SendMessage로 검증 요청하라.
qa의 피드백을 수신하면 즉시 수정하고 재검증 요청하라.
변경 목록을 _workspace/02_implementer_changes.md에 기록하라."
    },
    {
      name: "qa",
      agent_type: "qa-validator",
      model: "opus",
      prompt: "aipub-backend 코드 품질을 검증하라.

요청에 대한 구현이 진행 중이다.

.claude/agents/qa-validator.md를 읽고 역할과 2-Tier QA 전략을 따르라.
implementer로부터 모듈별 구현 완료 알림을 수신하면 즉시 Fast Tier 검증을 시작하라.

Fast Tier (모듈 완성마다): 빌드 → spotlessApply → 단위 테스트 → 컨벤션
Slow Tier (전체 Fast PASS 후): K8s/ES 통합 테스트 → 전체 테스트 → 모듈 간 정합성

문제 발견 시 implementer에게 SendMessage로 구체적 수정 요청을 보내라.
단순 포맷/import 에러는 직접 수정하라.
최종 결과를 _workspace/03_qa_report.md에 기록하라."
    }
  ]
)
```

### Phase 3: 작업 등록

```
TaskCreate(tasks: [
  {
    title: "코드베이스 분석",
    description: "기존 패턴 탐색, 영향 범위 식별, 참고 코드 수집",
    assignee: "analyst"
  },
  {
    title: "Proto 정의",
    description: "backend-protocol에 서비스/메시지 정의",
    assignee: "implementer",
    depends_on: ["코드베이스 분석"]
  },
  {
    title: "gRPC 서비스 구현",
    description: "backend-api에 DDD 레이어 + gRPC 서비스 구현",
    assignee: "implementer",
    depends_on: ["Proto 정의"]
  },
  {
    title: "Fast QA: Proto + API",
    description: "Fast Tier — backend-protocol, backend-api 빌드 + spotless + 단위 테스트 + 컨벤션",
    assignee: "qa",
    depends_on: ["gRPC 서비스 구현"]
  },
  {
    title: "Gateway REST 구현",
    description: "backend-gateway에 REST 컨트롤러 + gRPC 클라이언트 프록시",
    assignee: "implementer",
    depends_on: ["gRPC 서비스 구현"]
  },
  {
    title: "Fast QA: Gateway",
    description: "Fast Tier — backend-gateway 빌드 + spotless + 단위 테스트 + 컨벤션",
    assignee: "qa",
    depends_on: ["Gateway REST 구현"]
  },
  {
    title: "테스트 작성",
    description: "단위 테스트 + 필요 시 통합 테스트 작성",
    assignee: "implementer",
    depends_on: ["gRPC 서비스 구현"]
  },
  {
    title: "Slow QA: 통합 테스트 + 정합성",
    description: "Slow Tier — K8s/ES 통합 테스트(변경이 관련된 경우만) + 전체 테스트 + 모듈 간 경계면 교차 검증",
    assignee: "qa",
    depends_on: ["Fast QA: Proto + API", "Fast QA: Gateway", "테스트 작성"]
  }
])
```

> 작업 구성은 사용자 요청에 따라 조정한다. proto 변경이 불필요하면 "Proto 정의" 작업을 제외하고, gateway 변경이 불필요하면 관련 작업을 제외한다. Slow Tier의 K8s/ES 통합 테스트는 변경이 해당 영역에 영향을 주는 경우에만 실행한다.

### Phase 4: 팀 실행 및 모니터링

**실행 방식:** 팀원들이 자체 조율

팀원들은 작업 의존성에 따라 순차/병렬로 작업을 수행한다:
1. analyst가 분석 → implementer에게 SendMessage로 결과 전달
2. implementer가 모듈별로 구현 → qa에게 모듈 완성 알림
3. qa가 점진적으로 검증 → 문제 발견 시 implementer에게 직접 피드백
4. implementer가 수정 → qa에게 재검증 요청

**리더 모니터링:**
- 팀원이 유휴 상태가 되면 자동 알림 수신
- TaskGet으로 전체 진행률 확인
- 특정 팀원이 막히면 SendMessage로 개입

**2-Tier 점진적 QA 패턴:**
```
implementer: "backend-protocol 빌드 완료"     → qa: Fast Tier (빌드만)
implementer: "backend-api gRPC 구현 완료"     → qa: Fast Tier (빌드 + 단위 테스트 + 컨벤션)
qa: "빌드 에러 — UserService.java:42 미구현"  → implementer 수정
implementer: "수정 완료, 재검증 부탁"          → qa: Fast Tier 재검증
implementer: "backend-gateway 구현 완료"      → qa: Fast Tier (빌드 + 단위 테스트 + 컨벤션)
implementer: "테스트 작성 완료"               → qa: Slow Tier (K8s/ES 통합 테스트 + 정합성)
```

> Fast Tier에서는 K8s(K3s TestContainers ~3분)와 ES TestContainers를 사용하는 `*IntegrationTest`, `*AcceptanceTest`를 제외한다. Slow Tier에서 전체 테스트를 1회 실행한다.

### Phase 5: 결과 수집 및 보고
1. 모든 작업 완료 대기 (TaskGet으로 확인)
2. `_workspace/03_qa_report.md` Read
3. QA 종합 판정 확인:
   - **PASS**: 사용자에게 변경 목록 + 검증 결과 요약 보고
   - **FAIL**: 미해결 문제를 분석하고 implementer에게 SendMessage로 추가 수정 요청. 최대 1회 재시도 후에도 FAIL이면 사용자에게 문제 상세 보고.
4. 팀원들에게 종료 요청

### Phase 6: 문서화

QA PASS 후 `${PROJECT_HOME}/docs/{작업명}/` 디렉토리를 생성하고 다음 4개 문서를 작성한다.

**작업명 규칙**: 사용자 요청에서 핵심 키워드를 kebab-case로 변환 (예: "헬스체크 API" → `health-check-api`, "즐겨찾기 gRPC API" → `favorite-grpc-api`)

**문서 구성**:

| 파일 | 내용 |
|------|------|
| `01-plan.md` | 계획 문서 — 개요, 배경, 설계 결정(검토한 대안 포함), 변경 범위 |
| `02-implementation.md` | 구현 문서 — 변경 파일 목록, API 명세(엔드포인트/요청/응답), 아키텍처 다이어그램, 설정 가이드 |
| `03-qa-report.md` | QA 보고서 — 빌드/Spotless/테스트 결과, 컨벤션 검증 체크리스트, 발견 및 수정된 이슈, 보안 검토 |
| `04-pr.md` | PR 문구 — Title, Summary, Changes(모듈별), Test plan(체크리스트) |

**작성 원칙**:
- 각 문서는 자체 완결적이어야 한다. 다른 문서를 참조하지 않고도 해당 관점에서 작업을 이해할 수 있어야 한다.
- 구현 문서의 API 명세는 실제 응답 JSON 예시를 포함한다.
- QA 보고서는 _workspace/03_qa_report.md의 내용을 기반으로 정리한다.
- PR 문구는 GitHub PR 생성 시 바로 복사해서 사용할 수 있는 형태로 작성한다.

## 데이터 흐름

```
[리더] → TeamCreate → [analyst] ──SendMessage──→ [implementer] ←─SendMessage─→ [qa]
                          │                           │                           │
                          ↓                           ↓                           ↓
                   01_analyst_report.md    소스 파일 변경 +               03_qa_report.md
                                          02_implementer_changes.md
                                                      │
                                                      ↓
                                              [리더: 결과 보고]
```

## 에러 핸들링

| 상황 | 전략 |
|------|------|
| analyst 유휴/중지 | 리더가 직접 간단한 분석 수행, implementer에게 결과 전달 |
| implementer 중지 | 리더가 에러 내용 확인, SendMessage로 재개 시도. 실패 시 사용자 보고 |
| qa 중지 | 리더가 직접 빌드/테스트 실행으로 기본 검증 |
| implementer ↔ qa 피드백 루프 2회 초과 | 리더가 개입하여 문제 분석, 사용자에게 판단 요청 |
| 팀원 간 충돌 | 리더가 SendMessage로 방향 결정 |

## 단일 모듈 변경 시 경량 팀

proto 변경 없이 한 모듈만 수정하는 경우:

```
TeamCreate(
  team_name: "backend-dev-team",
  members: [
    { name: "implementer", ... },
    { name: "qa", ... }
  ]
)
```

analyst 없이 2인 팀. implementer에게 직접 맥락 전달. 작업 수도 3~4개로 축소.

## 테스트 시나리오

### 정상 흐름: 새 gRPC API 추가
1. 사용자: "사용자 즐겨찾기 기능의 gRPC API를 추가해줘"
2. Phase 2: 3인 팀 구성
3. Phase 3: 8개 작업 등록 (분석 → proto → api → Fast QA → gateway → Fast QA → 테스트 → Slow QA)
4. Phase 4:
   - analyst가 survey 모듈 패턴을 참고 코드로 발굴 → implementer에게 전달
   - implementer가 favorite.proto 작성 → backend-protocol 빌드 → qa에게 알림
   - qa: **Fast Tier** — proto 빌드 PASS (수십 초)
   - implementer가 backend-api gRPC 서비스 완성 → qa에게 알림
   - qa: **Fast Tier** — 빌드 + spotlessApply(에러 직접 수정) + 단위 테스트 + 컨벤션 → PASS
   - implementer가 gateway 구현 → qa에게 알림
   - qa: **Fast Tier** — gateway 빌드 + 단위 테스트 → PASS
   - implementer가 테스트 작성 완료 → qa에게 알림
   - qa: **Slow Tier** — 전체 테스트(K8s/ES 관련 없으므로 해당 통합 테스트 SKIP) + 모듈 간 정합성 교차 검증 → PASS
5. Phase 5: QA 종합 PASS, 사용자에게 결과 보고

### 에러 흐름: Fast Tier에서 빌드 실패
1. implementer가 gRPC 서비스 구현 완료 → qa에게 알림
2. qa: **Fast Tier** 빌드 → 실패: "미구현 RPC 메서드 getFavoriteById"
3. qa가 implementer에게 SendMessage: "proto에 정의된 GetFavoriteById RPC 미구현. FavoriteGrpcService.java에 추가 필요."
4. implementer가 수정 → qa에게 "수정 완료, 재검증 부탁"
5. qa: **Fast Tier** 재빌드 → PASS → 다음 단계 진행 (Slow Tier는 전체 Fast PASS 후)

### 에러 흐름: Slow Tier에서 K8s 통합 테스트 실패
1. 모든 Fast Tier PASS 완료
2. qa: **Slow Tier** K8sProxyK3sAcceptanceTest 실행 → K3s 컨테이너 기동 (~3분) → 테스트 실패
3. qa가 implementer에게 SendMessage: "K8s 프록시 테스트 실패. ProxyController의 경로 매핑 확인 필요."
4. implementer 수정 → qa Slow Tier 해당 테스트만 재실행
5. PASS → 종합 보고

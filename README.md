# CoolingVerse Backend

유휴 주차자원 정책 시뮬레이션 플랫폼의 백엔드 API 서버입니다.
Spring Boot(Java 17) 기반이며, 프론트엔드(Vue) 프로토타입의 타입 정의와 1:1로 맞춘 JSON을 반환합니다.

## 빠른 시작

```bash
./gradlew bootRun        # Windows는 gradlew.bat bootRun
```

- 기본 포트: **8080**
- **모든 API는 `/api` 접두사를 가진다** (예: `POST http://localhost:8080/api/login`).
  팀 합의(2026-08-02)에 따라 배포 시 nginx가 `/api`를 떼지 않고 그대로 전달하므로,
  백엔드가 접두사를 직접 처리한다.
- CORS: `http://localhost:5173` (Vite 개발서버) 허용됨
- 관리자 계정: 환경변수 `ADMIN_ID`, `ADMIN_PASSWORD_HASH`(BCrypt)로 주입.
  로컬 테스트용 기본값이 있어 환경변수 없이도 바로 실행됩니다.

## 인증 방식 (정적 토큰)

세션/쿠키/JWT를 쓰지 않습니다. 흐름은 3단계입니다.

1. `POST /login` 으로 ID/비밀번호 전송 → 성공 시 `token` 수신
2. 이후 모든 요청 헤더에 `Authorization: Bearer <token>` 첨부
3. 토큰이 없거나 틀리면 **401** 응답

토큰은 서버가 시작될 때마다 새로 생성됩니다(서버 재시작 = 전체 재로그인).

### 프론트 연동 설정

프론트 저장소의 `.env` 파일에 (접두사 `/api` 포함):

```
VITE_API_BASE_URL=http://localhost:8080/api
```

배포 환경에서는 `.env.production`의 `VITE_API_BASE_URL=/api`가 적용되고,
nginx가 `/api` 경로를 접두사째로 백엔드에 전달한다.

`src/api/*.ts`의 목(mock) 함수를 주석에 적힌 대로 `http()` 호출로 교체하면 됩니다.
로그인 응답의 `token`은 이미 `stores/auth.ts`가 `setAuthToken()`으로 처리하게 되어 있습니다.

---

## API 목록

### 1. 로그인

```
POST /login          (인증 불필요 — 유일하게 열린 창구)
```

요청:
```json
{ "username": "관리자ID", "password": "비밀번호" }
```

응답 — 성공 / 실패 (실패도 HTTP 200으로 옵니다. 본문의 `success`로 판단):
```json
{ "success": true, "token": "88RUE8vaYkJizWu8..." }
{ "success": false, "message": "계정 또는 비밀번호를 확인해 주세요." }
```

### 2. 시뮬레이션

```
GET  /simulate/initial    대시보드 첫 화면용 기본 결과 (표준 개방안 30%, 08~19시)
POST /simulate            슬라이더 조절 시 재계산
```

POST 요청 (프론트 `SimulationSettings` 타입 그대로):
```json
{
  "openToPublic": true,
  "residentsOnly": true,
  "participationRate": 30,
  "openFrom": "08:00",
  "openTo": "19:00",
  "commercialRadiusM": 500
}
```

응답: 프론트 `SimulationResult` 타입 그대로 — `kpis`(KPI 카드 5장),
`metricChanges`(before/after 3쌍), `participation`(도넛), `hourlySupply`(시간대별),
`riskTrend`(개방률 단계별 현재/예측).

계산 수식은 데이터분석 확정본(2026-07-26)을 그대로 구현했으며,
참여율 10/30/50/70/100% 입력 시 분석 가이드 시나리오 표의 수치
(추가 공급·CO2·위험지수)가 소수점 둘째 자리까지 일치함을 검증했습니다.

### 3. 지도 히트맵

```
GET /simulate/grid-risk?hour=14&region=pangyo&participation=30
```

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `hour` | 14 | 조회 시간대 (0~23) |
| `region` | `pangyo` | 현재 pangyo(분당)만 데이터 보유 — 그 외 지역은 404 |
| `participation` | 0 | 시나리오 참여율(%) — 주면 `hourlyRisk.projected` 커브에 감소폭 반영 |

응답: 프론트 `geo.ts`의 `GridRiskResponse` 타입 그대로 —
`grids`(위험지수 보유 격자 1,306개의 좌표+점수), `globalRisk`(해당 시간대 평균),
`breakdown`(주차/환경/교통 3요소 점수+등급), `hourlyRisk`(24시간 current·projected 커브).

- 기동 시 risk_index 31,344행을 메모리 캐시에 적재해 시간 스크러버 드래그에도 DB 왕복 없음 (응답 약 70KB)
- breakdown의 "주차"는 수요압력(0.25)·공급부족(0.35)을 가중치 비율로 합산한 값 (4요소→3요소 매핑)

### 4. 시나리오 관리

```
GET    /scenarios                 목록 (검색·정렬·페이지네이션)
POST   /scenarios                 저장
GET    /scenarios/{id}            상세 (열기)
PATCH  /scenarios/{id}            이름·메모 부분 수정
DELETE /scenarios/{id}            삭제 (성공 204 / 없으면 404)
```

목록 쿼리 파라미터 (프론트 `ScenarioFilter` 대응, 전부 생략 가능):

| 파라미터 | 기본값 | 설명 |
|---|---|---|
| `keyword` | (빈값) | 시나리오 이름 부분 검색 |
| `region` | `all` | 지역 필터 — 코드(`pangyo`/`ingye`) 표준, 표시명도 허용 |
| `sort` | `updatedDesc` | `updatedDesc`(최신순) / `updatedAsc` |
| `page` | 1 | 1부터 시작 |
| `pageSize` | 10 | 페이지당 개수 |

목록 응답 (프론트 `Paginated<Scenario>` 그대로):
```json
{
  "items": [
    {
      "id": "2", "name": "2단계: 표준 개방안 (30%)", "region": "성남시 분당구",
      "conditions": "30%, 08~19시", "supplyDelta": 11734,
      "riskBefore": 37.81, "riskAfter": 36.85, "updatedAt": "2026.07.28"
    }
  ],
  "total": 5, "page": 1, "pageSize": 10
}
```

저장 요청 — 이름 + 메모(선택) + 현재 슬라이더 설정을 보내면,
서버가 결과(공급·위험지수·CO2)를 계산해 스냅샷으로 함께 저장합니다:
```json
{
  "name": "내 시나리오",
  "memo": "메모 (선택)",
  "settings": { "openToPublic": true, "residentsOnly": true, "participationRate": 45,
                "openFrom": "09:00", "openTo": "18:00", "commercialRadiusM": 500 }
}
```

수정 요청 — 이름·메모만 대상이며, **보내지 않은 필드는 기존 값을 유지**합니다.
설정값과 결과 스냅샷은 불변이라 재계산이 일어나지 않습니다:
```json
{ "name": "새 이름", "memo": "새 메모" }
```
`name`과 `memo`를 모두 생략하거나 `name`을 빈 문자열로 보내면 400,
없는 id면 404를 반환합니다.

---

## 현재 제한사항 (1차 버전)

- **시나리오 저장소는 서버 메모리입니다.** 서버를 재시작하면 저장한 시나리오가
  사라지고, 대표 개방률 5단계 시드만 다시 생성됩니다. Oracle ADB 연결 후
  DB 저장으로 교체 예정입니다 (API 주소·모양은 그대로 유지).
- 시뮬레이션의 집계 상수(미개방 유휴면 39,114면 등)는 전처리 CSV 실측값을
  코드에 담은 것으로, DB 연동 후 쿼리로 교체됩니다.
- KPI 중 불법주정차/교통혼잡 감소율은 위험지수 감소율 기반 근사치입니다
  (전용 산식은 팀 협의 후 정밀화).

## DB 스키마 (`db/` 폴더)

| 파일 | 용도 |
|---|---|
| `db/schema.sql` | Oracle ADB용 DDL 전문 (8테이블, 2026-07-28 확정본) |
| `db/seed.sql` | 초기 데이터 — 분당구 INSERT + 대표 개방률 5단계 시나리오/결과 |
| `db/erd.dbml` | ERD 원본 (dbdiagram.io 에 붙여넣으면 다이어그램 렌더링) |

실행 순서: ADB Database Actions → SQL에서 `schema.sql` 전체 실행 → `seed.sql` 실행 → CSV 5종 적재.
데이터분석 인수인계 시드 SQL 원본은 구버전 컬럼(open_target 등)을 사용하므로,
반드시 이 폴더의 `seed.sql`(프론트 정렬 스키마 기준 변환본)을 사용할 것. 결과 수치는 동일함.

## 테스트

```bash
./gradlew test
```

시뮬레이션 수식이 분석 가이드의 시나리오 표(5단계 × 공급/CO2/위험지수)를 그대로
재현하는지 자동 검증합니다. 코드를 수정한 뒤에는 반드시 이 명령으로 확인하세요.

## DB 연동 준비 상태 (JPA)

- 기본 실행 = **H2 메모리 DB** (로컬 개발용, 재시작하면 비워짐). 엔티티 매핑 검증용.
- Oracle ADB 연결 시 = `--spring.profiles.active=oracle` 로 전환.
  `application-oracle.properties`에 Wallet 경로·계정을 환경변수로 채우면 됨.
- `src/main/java/com/example/demo/db/`에 ERD 8테이블과 1:1인 엔티티 +
  리포지토리(시뮬레이션 상수를 실측 쿼리로 교체할 집계 쿼리 포함)가 준비되어 있음.
- 아직 API는 메모리 저장소(ScenarioStore)를 사용 중 — ADB 데이터 적재 후 교체 예정.

## 프로젝트 구조

```
src/main/java/com/example/demo/
├── ParkingAuthApplication.java   # 시작점
├── AdminAuthConfig.java          # 환경변수 → 관리자 1명 등록 (BCrypt)
├── SecurityConfig.java           # 접근 규칙: /login만 공개, 나머지 토큰 필수, CORS
├── TokenStore.java               # 기동 시 무작위 토큰 생성·보관
├── TokenAuthFilter.java          # Bearer 헤더 검사 문지기
├── AuthController.java           # POST /login
├── SimulationDtos.java           # 시뮬레이션 요청/응답 모양 (프론트 타입과 1:1)
├── SimulationService.java        # What-If 계산 수식 (규칙 기반)
├── SimulationController.java     # GET /simulate/initial, POST /simulate
├── ScenarioDtos.java             # 시나리오 요청/응답 모양
├── ScenarioStore.java            # 임시 메모리 저장소 (→ ADB 후 JPA 교체)
└── ScenarioController.java       # /scenarios CRUD
```

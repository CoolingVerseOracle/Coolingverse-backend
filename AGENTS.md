# AGENTS.md — CoolingVerse Backend

AI 코딩 에이전트를 위한 프로젝트 안내서. 사람이 읽어도 유용합니다.

## 프로젝트 개요

유휴 아파트 주차장 개방 효과를 시뮬레이션하는 **B2G 정책 지원 플랫폼**의 백엔드.
지자체 공무원이 개방률 슬라이더를 조절하면 위험지수 감소·CO2 저감을 즉시 계산해 보여준다.
분석 대상: 성남시 분당구 (100m 격자 122,318개, 아파트 210단지, 단속 16.1만 건).

- 스택: **Spring Boot 4.1 / Java 17 / Gradle** + Oracle Autonomous DB(ADB)
- 프론트: Vue 3 SPA (별도 레포 `CoolingVerse-frontend`, localhost:5173)
- 팀 구성: 프론트 / 백엔드 / 데이터분석 3인

## 명령어

```bash
./gradlew test        # 전체 테스트 (H2 메모리 DB로 실행, 네트워크 불필요)
./gradlew bootRun     # 로컬 개발 모드 (H2 + 검증된 기본 집계값)
./gradlew bootRun --args='--spring.profiles.active=oracle'   # 실제 ADB 연결 모드
```

- oracle 프로필은 환경변수 `ADB_PASSWORD` + 로컬 Wallet(`C:/oracle/wallet`)이 필요하다.
- 포트 8080. 프론트 개발서버(5173)가 CORS 허용 목록에 있다.

## 아키텍처 (src/main/java/com/example/demo/)

| 파일 | 역할 |
| --- | --- |
| AuthController, TokenStore, TokenAuthFilter, SecurityConfig, AdminAuthConfig | 정적 토큰 인증 (세션/JWT 없음). `/login`만 공개, 나머지 전부 401 |
| SimulationService, SimulationDtos, SimulationController | What-If 계산 엔진 (규칙 기반 수식) |
| SupplyStats, StatsConfig | 집계값 주입 — 기동 시 DB 쿼리 1회, 빈 DB면 검증된 기본값 폴백 |
| ScenarioStore, ScenarioDtos, ScenarioController | 시나리오 CRUD (DB 영속, 같은 날 재계산은 덮어쓰기) |
| db/ 패키지 | JPA 엔티티 8종 + 리포지토리 (ERD와 1:1) |

DB 스키마·시드는 `db/schema.sql`, `db/seed.sql` 참고. ERD 원본은 `db/erd.dbml`.
API 명세와 프론트 연동법은 `README.md`, 진행 이력은 `docs/PROGRESS.md`, 보안 설계는 `docs/SECURITY.md`.

## 절대 규칙 (위반하면 시스템이 조용히 어긋남)

1. **검증된 수식을 건드리지 마라.** `SimulationServiceTest`의 5단계 표(참여율 10~100% ×
   공급/CO2/위험지수 15개 수치)는 데이터분석팀 공식 산출물의 재현이다. 이 테스트가 깨지는
   변경은 수치 계약 위반이다. 운영시간 계산이 "시작·종료 블록 포함"(08~19시=12시간)인 것도
   분석 수치와 맞추기 위한 의도된 설계다.
2. **프론트 계약 필드명을 바꾸지 마라.** DTO(record)들의 필드명은 프론트 타입 파일
   (simulation.ts, scenario.ts, auth.ts)과 1:1이다. 이름을 바꾸면 프론트가 조용히 깨진다.
3. **로그인 실패는 HTTP 200 + `{success:false, message}`로 응답한다.** 401/400이 아니다 —
   프론트 http()가 4xx에서 예외를 던지는 구조라 의도적으로 맞춘 계약이다.
4. **시크릿을 커밋하지 마라.** Wallet 파일, `ADB_PASSWORD`, 운영 해시값은 절대 저장소에
   넣지 않는다. 접속 정보는 환경변수로만 주입한다.
5. **시드 데이터와 IDENTITY의 관계를 기억하라.** `db/seed.sql`은 번호를 수동 지정한 뒤
   `START WITH LIMIT VALUE`로 자동 번호를 재정렬한다. 시드를 수정하면 이 재정렬도 함께 챙길 것.
6. **커밋 메시지는 한글로, AI 공동작성자 트레일러(Co-Authored-By)는 붙이지 않는다.**

## 도메인 지식 (자주 헷갈리는 것)

- **open_count** = "현재 개방 대수"가 아니라 "개방 시 확보 가능한 잠재 유휴면수"
  (총 주차면수 × 유휴율 29.1%를 분석팀이 사전 반영). 시뮬레이션의 기준값.
- **위험지수** = (0.35 공급부족 + 0.25 수요압력 + 0.15 교통혼잡 + 0.25 환경민감도) × 100.
  baseline 37.81은 risk_index **24시간 전체 평균**이다 (운영시간대만 평균 내면 38.74로 달라짐).
- **CO2 저감(kg/일)** = 추가 개방 면수 × 0.306 × (운영시간/10). 0.306 = 배회 1.5km + 공회전 4분.
- 개방 대상 체크박스 2개: `openToPublic`(기개방 17단지 = 기본 공급 3,684면),
  `residentsOnly`(미개방 193단지 = 시뮬 대상, 100% 시 39,114면). **후자가 꺼지면 효과가 전부 0.**
- enforcement의 grid_id NULL 471건은 지오코딩 실패분 — 버그가 아니라 의도된 보존.
- air_quality의 원천(에어코리아)은 자정을 "24:00"로 표기한다. 적재 시 익일 00:00으로 변환했다.
- `commercialRadiusM`(상업 반경)은 프론트가 보내지만 현재 계산에 미사용 — 팀 협의 대기 항목.
- 불법주정차/교통혼잡 KPI는 위험지수 감소율 기반 근사치 — 정식 산식은 분석팀 협의 대기.

## 인프라 메모

- ADB: 이름 `cvadb`, 리전 South Korea North(춘천), 컴파트먼트 `coolingverse`.
  앱 접속은 `cvadb_tp` 서비스 + Instance Wallet(mTLS). Always Free 등급이라
  **7일 미사용 시 자동 정지**됨 — 시연 전 콘솔에서 Available 상태 확인 필수.
- 배포 목표: OCI Compute (미완). 배포 시 CORS 허용 목록에 배포 출처 추가 필요.

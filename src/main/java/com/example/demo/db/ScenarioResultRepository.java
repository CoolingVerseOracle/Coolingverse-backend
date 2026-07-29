package com.example.demo.db;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

/** scenario_results 조회/저장 창구 */
public interface ScenarioResultRepository extends JpaRepository<ScenarioResult, Long> {

    /** 특정 시나리오의 결과 이력 (최신순) */
    List<ScenarioResult> findByScenarioIdOrderByRunDateDesc(Long scenarioId);

    /** 특정 시나리오의 가장 최근 결과 1건 — 목록 화면의 스냅샷 표시용 */
    Optional<ScenarioResult> findTopByScenarioIdOrderByRunDateDesc(Long scenarioId);

    /** 특정 시나리오의 특정 날짜 결과 — 같은 날 재계산 시 덮어쓰기(upsert) 판단용 */
    Optional<ScenarioResult> findByScenarioIdAndRunDate(Long scenarioId, java.time.LocalDate runDate);

    /** 특정 시나리오의 결과 전부 — 시나리오 삭제 시 이력 동반 삭제용 */
    List<ScenarioResult> findByScenarioId(Long scenarioId);
}

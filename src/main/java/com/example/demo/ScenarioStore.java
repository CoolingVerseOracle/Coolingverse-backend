package com.example.demo;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.SimulationService.CoreNumbers;
import com.example.demo.db.Scenario;
import com.example.demo.db.ScenarioRepository;
import com.example.demo.db.ScenarioResult;
import com.example.demo.db.ScenarioResultRepository;

import jakarta.annotation.PostConstruct;

/**
 * 시나리오 저장소 — JPA(DB) 기반.
 *
 * 조건은 scenarios 테이블에, 결과 스냅샷은 scenario_results 테이블에 저장한다
 * (ERD 구조 그대로: 시나리오 1개당 결과 이력 N건, 같은 날짜 재계산은 덮어쓰기).
 * - oracle 프로필: 실제 ADB에 영구 저장 (seed.sql의 5단계가 이미 들어 있음)
 * - 기본(H2) 프로필: 로컬 임시 DB — 비어 있으면 기동 시 5단계 시드 자동 생성
 */
@Component
public class ScenarioStore {

    /** 컨트롤러/테스트가 쓰는 화면용 묶음 (조건 + 최신 결과 스냅샷) */
    public static class ScenarioEntity {
        public long id;
        public String name;
        public String memo;
        public String region;
        public SimulationSettings settings;
        public int addedSupply;
        public double riskBefore;
        public double riskAfter;
        public double carbonReduction;
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    private static final String DEFAULT_REGION = "성남시 분당구";
    private static final long DEFAULT_DISTRICT_ID = 1L;  // 분당구 (districts 시드)

    private final ScenarioRepository scenarios;
    private final ScenarioResultRepository results;
    private final SimulationService simulationService;

    public ScenarioStore(ScenarioRepository scenarios,
                         ScenarioResultRepository results,
                         SimulationService simulationService) {
        this.scenarios = scenarios;
        this.results = results;
        this.simulationService = simulationService;
    }

    /** 기동 시: DB가 비어 있으면(로컬 H2) 대표 개방률 5단계 시드 생성. ADB에는 이미 있어 건너뜀 */
    @PostConstruct
    public void seedIfEmpty() {
        if (scenarios.count() > 0) return;
        record Seed(String name, String memo, int rate) {}
        List<Seed> seeds = List.of(
                new Seed("1단계: 기초 개방안 (10%)", "출근 시간대(08~19시) 10% 추가 개방 시 시범 정책", 10),
                new Seed("2단계: 표준 개방안 (30%)", "출근 시간대(08~19시) 30% 추가 개방 표준 정책", 30),
                new Seed("3단계: 적극 개방안 (50%)", "출근 시간대(08~19시) 50% 추가 개방 적극 정책", 50),
                new Seed("4단계: 전면 개방안 (70%)", "출근 시간대(08~19시) 70% 추가 개방 공공 주도 정책", 70),
                new Seed("5단계: 최대 상한안 (100%)", "출근 시간대(08~19시) 유휴 주차면 100% 완전 개방", 100));
        for (Seed seed : seeds) {
            save(seed.name(), seed.memo(),
                    new SimulationSettings(true, true, seed.rate(), "08:00", "19:00", 500));
        }
    }

    /** 저장: 조건을 scenarios에 넣고, 결과를 계산해 scenario_results에 스냅샷으로 보관 */
    @Transactional
    public ScenarioEntity save(String name, String memo, SimulationSettings settings) {
        Scenario entity = scenarios.save(new Scenario(
                name, memo == null ? "" : memo, DEFAULT_DISTRICT_ID,
                settings.openToPublic() ? "Y" : "N",
                settings.residentsOnly() ? "Y" : "N",
                settings.participationRate(),
                settings.openFrom(), settings.openTo(),
                settings.commercialRadiusM()));

        ScenarioResult snapshot = upsertTodayResult(entity.getScenarioId(), settings);
        return toView(entity, snapshot);
    }

    /** 같은 시나리오를 같은 날 다시 계산하면 새 줄 추가 대신 그날 결과를 교체(upsert) */
    private ScenarioResult upsertTodayResult(Long scenarioId, SimulationSettings settings) {
        CoreNumbers core = simulationService.core(settings);
        LocalDate today = LocalDate.now();

        results.findByScenarioIdAndRunDate(scenarioId, today).ifPresent(existing -> {
            results.delete(existing);
            results.flush();  // 유니크 제약(scenario_id, run_date) 충돌 방지: 지우기를 먼저 확정
        });
        return results.save(new ScenarioResult(scenarioId, today,
                core.addedSupply(), core.riskBefore(), core.riskAfter(), core.co2Kg()));
    }

    /**
     * 메타데이터(이름·메모)만 수정. 없는 id면 null 반환.
     * 설정값과 결과 스냅샷은 건드리지 않으므로 재계산이 일어나지 않는다.
     */
    @Transactional
    public ScenarioEntity updateMetadata(long id, String name, String memo) {
        return scenarios.findById(id)
                .map(entity -> {
                    entity.updateMetadata(name, memo);
                    return toView(scenarios.save(entity), latestResult(entity.getScenarioId()));
                })
                .orElse(null);
    }

    public List<ScenarioEntity> findAll() {
        return scenarios.findAll().stream()
                .map(e -> toView(e, latestResult(e.getScenarioId())))
                .toList();
    }

    public ScenarioEntity findById(long id) {
        return scenarios.findById(id)
                .map(e -> toView(e, latestResult(e.getScenarioId())))
                .orElse(null);
    }

    @Transactional
    public boolean deleteById(long id) {
        if (!scenarios.existsById(id)) return false;
        results.deleteAll(results.findByScenarioId(id));  // FK: 이력 먼저 삭제
        scenarios.deleteById(id);
        return true;
    }

    // ── 변환 도우미 ───────────────────────────────────────────────────

    private ScenarioResult latestResult(Long scenarioId) {
        return results.findTopByScenarioIdOrderByRunDateDesc(scenarioId).orElse(null);
    }

    private ScenarioEntity toView(Scenario e, ScenarioResult r) {
        ScenarioEntity view = new ScenarioEntity();
        view.id = e.getScenarioId();
        view.name = e.getName();
        view.memo = e.getMemo() == null ? "" : e.getMemo();
        view.region = DEFAULT_REGION;
        view.settings = new SimulationSettings(
                "Y".equals(e.getOpenToPublic()),
                "Y".equals(e.getResidentsOnly()),
                e.getParticipation() == null ? 0 : e.getParticipation(),
                e.getOperationStart(), e.getOperationEnd(),
                e.getCommercialRadiusM() == null ? 500 : e.getCommercialRadiusM());
        if (r != null) {
            view.addedSupply = r.getAddedSupply() == null ? 0 : r.getAddedSupply();
            view.riskBefore = r.getRiskBefore() == null ? 0 : r.getRiskBefore();
            view.riskAfter = r.getRiskAfter() == null ? 0 : r.getRiskAfter();
            view.carbonReduction = r.getCarbonReduction() == null ? 0 : r.getCarbonReduction();
        }
        view.createdAt = e.getCreatedAt();
        view.updatedAt = Optional.ofNullable(e.getUpdatedAt()).orElse(e.getCreatedAt());
        return view;
    }
}

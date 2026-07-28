package com.example.demo;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.SimulationService.CoreNumbers;

/**
 * 시나리오 임시 저장소 (서버 메모리).
 *
 * ⚠️ ADB 연결 전까지 쓰는 임시 창고다. 서버를 재시작하면 저장한 시나리오가
 * 사라진다(시드 5개는 기동 시 다시 생성됨). DB가 열리면 이 클래스만
 * JPA 리포지토리로 교체하면 된다 — ERD의 scenarios + scenario_results
 * 테이블 구조(조건 + 결과 스냅샷)를 그대로 본떠 만들었다.
 */
@Component
public class ScenarioStore {

    /** ERD scenarios + scenario_results 를 합친 모양의 저장 단위 */
    public static class ScenarioEntity {
        public long id;
        public String name;
        public String memo;
        public String region;
        public SimulationSettings settings;   // 조건 (레시피)
        public int addedSupply;               // 결과 스냅샷: 추가 확보 주차면
        public double riskBefore;             // 결과 스냅샷: 적용 전 위험지수
        public double riskAfter;              // 결과 스냅샷: 적용 후 위험지수
        public double carbonReduction;        // 결과 스냅샷: 일일 CO2 저감(kg)
        public LocalDateTime createdAt;
        public LocalDateTime updatedAt;
    }

    private static final String DEFAULT_REGION = "성남시 분당구";

    private final Map<Long, ScenarioEntity> data = new ConcurrentHashMap<>();
    private final AtomicLong sequence = new AtomicLong(0);
    private final SimulationService simulationService;

    public ScenarioStore(SimulationService simulationService) {
        this.simulationService = simulationService;
        seedDefaults();
    }

    /** 시드: 대표 개방률 5단계 (seed-초기데이터.sql 과 동일한 구성) */
    private void seedDefaults() {
        record Seed(String name, String memo, int rate) {}
        List<Seed> seeds = List.of(
                new Seed("1단계: 기초 개방안 (10%)", "출근 시간대(08~19시) 10% 추가 개방 시 시범 정책", 10),
                new Seed("2단계: 표준 개방안 (30%)", "출근 시간대(08~19시) 30% 추가 개방 표준 정책", 30),
                new Seed("3단계: 적극 개방안 (50%)", "출근 시간대(08~19시) 50% 추가 개방 적극 정책", 50),
                new Seed("4단계: 전면 개방안 (70%)", "출근 시간대(08~19시) 70% 추가 개방 공공 주도 정책", 70),
                new Seed("5단계: 최대 상한안 (100%)", "출근 시간대(08~19시) 유휴 주차면 100% 완전 개방", 100));
        for (Seed seed : seeds) {
            SimulationSettings settings =
                    new SimulationSettings(true, true, seed.rate(), "08:00", "19:00", 500);
            save(seed.name(), seed.memo(), settings);
        }
    }

    /** 저장: 결과 스냅샷은 시뮬레이션 수식으로 즉석 계산해 함께 보관 */
    public ScenarioEntity save(String name, String memo, SimulationSettings settings) {
        CoreNumbers core = simulationService.core(settings);

        ScenarioEntity entity = new ScenarioEntity();
        entity.id = sequence.incrementAndGet();
        entity.name = name;
        entity.memo = memo == null ? "" : memo;
        entity.region = DEFAULT_REGION;
        entity.settings = settings;
        entity.addedSupply = core.addedSupply();
        entity.riskBefore = core.riskBefore();
        entity.riskAfter = core.riskAfter();
        entity.carbonReduction = core.co2Kg();
        entity.createdAt = LocalDateTime.now();
        entity.updatedAt = entity.createdAt;

        data.put(entity.id, entity);
        return entity;
    }

    public List<ScenarioEntity> findAll() {
        return data.values().stream()
                .sorted(Comparator.comparingLong(e -> e.id))
                .toList();
    }

    public ScenarioEntity findById(long id) {
        return data.get(id);
    }

    public boolean deleteById(long id) {
        return data.remove(id) != null;
    }
}

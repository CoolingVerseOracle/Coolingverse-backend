package com.example.demo;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.example.demo.db.ApartmentRepository;
import com.example.demo.db.ApartmentRepository.SupplyAggregate;
import com.example.demo.db.RiskIndexRepository;

/** 지역·연도·월별 활성 데이터로 계산용 집계를 제공한다. */
@Service
public class RegionalStatsService {
    private static final Logger log = LoggerFactory.getLogger(RegionalStatsService.class);
    private record Key(String region, int year, int month) {}

    private final ApartmentRepository apartments;
    private final RiskIndexRepository risks;
    private final Map<Key, SupplyStats> cache = new ConcurrentHashMap<>();

    public RegionalStatsService(ApartmentRepository apartments, RiskIndexRepository risks) {
        this.apartments = apartments;
        this.risks = risks;
    }

    public SupplyStats get(String region, int year, int month) {
        return cache.computeIfAbsent(new Key(region, year, month), this::load);
    }

    /**
     * 활성 집계가 없으면 예외(→ 컨트롤러 400). 이전에는 검증 기본값(분당 구모델)으로 폴백했는데,
     * 미적재 지역·월 요청이 분당 옛 수치를 200으로 돌려주는 정합성 문제가 있어 제거했다 (이슈 #30).
     *
     * 단 하나의 예외: DB에 아파트가 한 건도 없는 빈 DB(로컬 H2 개발·테스트)는 검증 기본값을 유지한다 —
     * 기동 시 시나리오 시드가 시뮬레이션을 호출하므로 빈 DB에서도 부팅은 되어야 한다.
     * 운영(데이터가 있는 DB)에서는 이 분기를 타지 않는다.
     */
    private SupplyStats load(Key key) {
        SupplyAggregate a;
        Double average;
        try {
            if (apartments.count() == 0) {
                log.info("빈 DB(로컬 개발) — 검증 기본값 사용: region={}", key.region);
                return SupplyStats.VERIFIED_DEFAULTS;
            }
            a = apartments.aggregateActive(key.region, key.year);
            average = risks.averageActiveRisk(key.region, key.year, key.month);
        } catch (Exception e) {
            log.warn("지역 집계 조회 실패: region={}, month={}: {}", key.region, key.month, e.getMessage());
            throw new IllegalArgumentException("지역 데이터 조회에 실패했습니다: " + key.region, e);
        }
        int total = intValue(a == null ? null : a.getTotalParking());
        if (a == null || total == 0 || average == null) {
            log.warn("활성 집계 없음: region={}, year={}, month={}", key.region, key.year, key.month);
            throw new IllegalArgumentException(
                    "해당 지역·월 데이터가 아직 적재되지 않았습니다: " + key.region + " " + key.year + "-" + key.month);
        }
        SupplyStats stats = new SupplyStats(
                intValue(a.getIdleUnopened()), intValue(a.getIdleOpened()), total,
                intValue(a.getAptOpened()), intValue(a.getAptUnopened()),
                Math.round(average * 100) / 100.0);
        log.info("활성 집계: region={}, year={}, month={}, baseline={}", key.region, key.year, key.month, stats.riskBaseline());
        return stats;
    }

    private int intValue(Number value) { return value == null ? 0 : value.intValue(); }
    public void clear() { cache.clear(); }
}

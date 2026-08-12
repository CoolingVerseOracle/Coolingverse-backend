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

    private SupplyStats load(Key key) {
        try {
            SupplyAggregate a = apartments.aggregateActive(key.region, key.year);
            int total = intValue(a == null ? null : a.getTotalParking());
            Double average = risks.averageActiveRisk(key.region, key.year, key.month);
            if (a == null || total == 0 || average == null) {
                log.warn("활성 집계 없음: region={}, year={}, month={} — 검증 기본값 사용", key.region, key.year, key.month);
                return SupplyStats.VERIFIED_DEFAULTS;
            }
            SupplyStats stats = new SupplyStats(
                    intValue(a.getIdleUnopened()), intValue(a.getIdleOpened()), total,
                    intValue(a.getAptOpened()), intValue(a.getAptUnopened()),
                    Math.round(average * 100) / 100.0);
            log.info("활성 집계: region={}, year={}, month={}, baseline={}", key.region, key.year, key.month, stats.riskBaseline());
            return stats;
        } catch (Exception e) {
            log.warn("지역 집계 실패: region={}, month={} — 검증 기본값 사용: {}", key.region, key.month, e.getMessage());
            return SupplyStats.VERIFIED_DEFAULTS;
        }
    }

    private int intValue(Number value) { return value == null ? 0 : value.intValue(); }
    public void clear() { cache.clear(); }
}

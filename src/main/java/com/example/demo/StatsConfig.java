package com.example.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.example.demo.db.ApartmentRepository;
import com.example.demo.db.RiskIndexRepository;

/**
 * 집계값(SupplyStats)을 준비하는 설정.
 *
 * 서버가 켜질 때 딱 한 번 DB에서 집계 쿼리를 실행해 채운다.
 * DB가 비어 있으면(로컬 H2) 검증된 기본값으로 대체 — 어느 쪽이 쓰였는지 로그로 남긴다.
 * 분석팀이 데이터를 갱신하면 서버 재시작만으로 새 수치가 반영된다.
 */
@Configuration
public class StatsConfig {

    private static final Logger log = LoggerFactory.getLogger(StatsConfig.class);

    @Bean
    public SupplyStats supplyStats(ApartmentRepository apartments, RiskIndexRepository riskIndex) {
        try {
            long idleUnopened = apartments.sumIdleOfUnopened();
            if (idleUnopened == 0) {
                log.info("집계: DB가 비어 있어 검증된 기본값 사용 (로컬 개발 모드)");
                return SupplyStats.VERIFIED_DEFAULTS;
            }

            Double avgRisk = riskIndex.averageRiskScore();
            SupplyStats stats = new SupplyStats(
                    (int) idleUnopened,
                    (int) apartments.sumIdleOfOpened(),
                    (int) apartments.sumTotalParking(),
                    (int) apartments.countByIsOpen("Y"),
                    (int) apartments.countByIsOpen("N"),
                    avgRisk == null ? 37.81 : Math.round(avgRisk * 100) / 100.0);
            log.info("집계: DB 실측값 사용 — 미개방 유휴면 {}, 기개방 {}, baseline {}",
                    stats.idleUnopened(), stats.idleOpened(), stats.riskBaseline());
            return stats;
        } catch (Exception e) {
            log.warn("집계 쿼리 실패 — 검증된 기본값으로 대체: {}", e.getMessage());
            return SupplyStats.VERIFIED_DEFAULTS;
        }
    }
}

package com.example.demo.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** risk_index 조회 창구 — 지도 히트맵과 baseline 계산에 사용 */
public interface RiskIndexRepository extends JpaRepository<RiskIndex, Long> {

    /** 특정 시간대의 격자별 위험지수 (히트맵 한 장면) */
    List<RiskIndex> findByHourOfDay(Integer hourOfDay);

    /**
     * 기준지역(분당, district 1) 평균 위험지수 — 시뮬레이션 baseline.
     * 비교지역(부천 등) 위험지수가 같은 테이블에 있어도 baseline이 오염되지 않도록
     * 분당 격자 소속 행만 집계한다.
     */
    @Query("""
            select avg(r.riskScore) from RiskIndex r
            where r.gridId in (select g.gridId from Grid g where g.districtId = 1)
            """)
    Double averageRiskScore();

    /** 지도 캐시 적재용 한 행: 격자 좌표·소속 지역 + 시간대 + 지수·구성요소 */
    interface GridRiskRow {
        Long getGridId();
        Long getDistrictId();
        Integer getHourOfDay();
        Double getLat();
        Double getLng();
        Double getRiskScore();
        Double getDemand();
        Double getSupply();
        Double getTraffic();
        Double getEnv();
    }

    /**
     * 히트맵 재료 전체를 격자 좌표·소속 지역과 함께 조회.
     * 기동 시 1회만 실행해 지역별 메모리 캐시에 담는다 (GridRiskCache).
     */
    @Query("""
            select r.gridId as gridId, g.districtId as districtId,
                   r.hourOfDay as hourOfDay,
                   g.centerLat as lat, g.centerLng as lng,
                   r.riskScore as riskScore,
                   r.demandPressure as demand, r.supplyShortage as supply,
                   r.trafficCongest as traffic, r.envSensitivity as env
            from RiskIndex r, Grid g
            where g.gridId = r.gridId
            """)
    List<GridRiskRow> findAllWithCoordinates();
}

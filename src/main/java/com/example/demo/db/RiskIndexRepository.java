package com.example.demo.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** risk_index 조회 창구 — 지도 히트맵과 baseline 계산에 사용 */
public interface RiskIndexRepository extends JpaRepository<RiskIndex, Long> {

    /** 특정 시간대의 격자별 위험지수 (히트맵 한 장면) */
    List<RiskIndex> findByHourOfDay(Integer hourOfDay);

    /** 전체 평균 위험지수 — 현재 코드 상수 37.81(baseline)을 대체할 쿼리 */
    @Query("select avg(r.riskScore) from RiskIndex r")
    Double averageRiskScore();
}

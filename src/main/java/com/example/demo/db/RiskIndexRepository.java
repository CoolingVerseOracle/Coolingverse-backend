package com.example.demo.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 활성 데이터 버전의 지역·연도·월별 위험지수만 조회한다. */
public interface RiskIndexRepository extends JpaRepository<RiskIndex, Long> {

    interface GridRiskRow {
        Long getGridId();
        String getRegionCode();
        Integer getAnalysisYear();
        Integer getAnalysisMonth();
        Integer getHourOfDay();
        Double getLat();
        Double getLng();
        Double getRiskScore();
        Double getDemand();
        Double getSupply();
        Double getTraffic();
        Double getEnv();
    }

    @Query(value = """
            SELECT r.grid_id AS gridId, r.region_code AS regionCode,
                   r.analysis_year AS analysisYear, r.analysis_month AS analysisMonth,
                   r.hour_of_day AS hourOfDay, g.center_lat AS lat, g.center_lng AS lng,
                   r.risk_score AS riskScore, r.demand_pressure AS demand,
                   r.supply_shortage AS supply, r.traffic_congest AS traffic,
                   r.env_sensitivity AS env
              FROM risk_index r
              JOIN active_dataset_versions a
                ON a.region_code = r.region_code
               AND a.analysis_year = r.analysis_year
               AND a.active_run_id = r.pipeline_run_id
              JOIN grids g
                ON g.region_code = r.region_code AND g.grid_id = r.grid_id
             ORDER BY r.region_code, r.analysis_year, r.analysis_month, r.hour_of_day, r.grid_id
            """, nativeQuery = true)
    List<GridRiskRow> findAllActiveWithCoordinates();

    @Query(value = """
            SELECT AVG(r.risk_score)
              FROM risk_index r
              JOIN active_dataset_versions a
                ON a.region_code = r.region_code
               AND a.analysis_year = r.analysis_year
               AND a.active_run_id = r.pipeline_run_id
             WHERE r.region_code = :regionCode
               AND r.analysis_year = :analysisYear
               AND r.analysis_month = :analysisMonth
            """, nativeQuery = true)
    Double averageActiveRisk(@Param("regionCode") String regionCode,
                             @Param("analysisYear") int analysisYear,
                             @Param("analysisMonth") int analysisMonth);
}

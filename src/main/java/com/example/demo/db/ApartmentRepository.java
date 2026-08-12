package com.example.demo.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 활성 실행 ID에 속한 지역별 공급 통계만 조회한다. */
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {
    interface SupplyAggregate {
        Number getIdleUnopened();
        Number getIdleOpened();
        Number getTotalParking();
        Number getAptOpened();
        Number getAptUnopened();
    }

    @Query(value = """
            SELECT COALESCE(SUM(CASE WHEN ap.is_open = 'N' THEN ap.open_count ELSE 0 END), 0) AS idleUnopened,
                   COALESCE(SUM(CASE WHEN ap.is_open = 'Y' THEN ap.open_count ELSE 0 END), 0) AS idleOpened,
                   COALESCE(SUM(ap.total_parking), 0) AS totalParking,
                   COALESCE(SUM(CASE WHEN ap.is_open = 'Y' THEN 1 ELSE 0 END), 0) AS aptOpened,
                   COALESCE(SUM(CASE WHEN ap.is_open = 'N' THEN 1 ELSE 0 END), 0) AS aptUnopened
              FROM apartments ap
              JOIN active_dataset_versions a
                ON a.region_code = ap.region_code
               AND a.active_run_id = ap.pipeline_run_id
             WHERE ap.region_code = :regionCode
               AND a.analysis_year = :analysisYear
            """, nativeQuery = true)
    SupplyAggregate aggregateActive(@Param("regionCode") String regionCode,
                                    @Param("analysisYear") int analysisYear);
}

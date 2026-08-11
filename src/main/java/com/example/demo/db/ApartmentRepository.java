package com.example.demo.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * apartments 조회 창구 — 시뮬레이션 상수를 DB 실측값으로 교체할 때 사용.
 *
 * 시뮬레이션은 기준지역(분당, district 1) 전제이므로 모든 집계를 분당 격자
 * 소속 단지로 한정한다. 비교지역(부천 등) 단지가 섞이면 공급·CO2가 부풀어진다.
 */
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    /** 분당 미개방(N) 단지의 잠재 유휴면 합계 — 검증값 39,114의 출처 */
    @Query("""
            select coalesce(sum(a.openCount), 0) from Apartment a
            where a.isOpen = 'N'
              and a.gridId in (select g.gridId from Grid g where g.districtId = 1)
            """)
    long sumIdleOfUnopened();

    /** 분당 기개방(Y) 단지의 유휴면 합계 — 검증값 3,684의 출처 */
    @Query("""
            select coalesce(sum(a.openCount), 0) from Apartment a
            where a.isOpen = 'Y'
              and a.gridId in (select g.gridId from Grid g where g.districtId = 1)
            """)
    long sumIdleOfOpened();

    /** 분당 전체 주차면 합계 — 검증값 147,580의 출처 */
    @Query("""
            select coalesce(sum(a.totalParking), 0) from Apartment a
            where a.gridId in (select g.gridId from Grid g where g.districtId = 1)
            """)
    long sumTotalParking();

    /** 분당 개방 여부별 단지 수 — 검증값 Y=17 / N=193의 출처 */
    @Query("""
            select count(a) from Apartment a
            where a.isOpen = :isOpen
              and a.gridId in (select g.gridId from Grid g where g.districtId = 1)
            """)
    long countByIsOpen(@Param("isOpen") String isOpen);
}

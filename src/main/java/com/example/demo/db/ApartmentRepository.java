package com.example.demo.db;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

/** apartments 조회 창구 — 시뮬레이션 상수를 DB 실측값으로 교체할 때 사용 */
public interface ApartmentRepository extends JpaRepository<Apartment, Long> {

    /** 미개방(N) 단지의 잠재 유휴면 합계 — 현재 코드 상수 39,114를 대체할 쿼리 */
    @Query("select coalesce(sum(a.openCount), 0) from Apartment a where a.isOpen = 'N'")
    long sumIdleOfUnopened();

    /** 기개방(Y) 단지의 유휴면 합계 — 현재 코드 상수 3,684를 대체할 쿼리 */
    @Query("select coalesce(sum(a.openCount), 0) from Apartment a where a.isOpen = 'Y'")
    long sumIdleOfOpened();

    /** 전체 주차면 합계 — 현재 코드 상수 147,580을 대체할 쿼리 */
    @Query("select coalesce(sum(a.totalParking), 0) from Apartment a")
    long sumTotalParking();

    long countByIsOpen(String isOpen);
}

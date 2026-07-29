package com.example.demo;

/**
 * 시뮬레이션 계산에 쓰는 집계값 묶음.
 *
 * 운영(oracle 프로필)에서는 DB 실측 쿼리로 채워지고,
 * 로컬 개발(H2, 빈 DB)이나 단위 테스트에서는 검증된 기본값을 쓴다.
 * 기본값 출처: 분석팀 최종 CSV 실측 (2026-07-28 검증) — DB 값과 동일함을 확인함.
 */
public record SupplyStats(
        int idleUnopened,    // 미개방(N) 단지 잠재 유휴면 합 = 슬라이더 100% 시 추가 공급
        int idleOpened,      // 기개방(Y) 단지 유휴면 합 = 기본 공급
        int totalParking,    // 전체 주차면 합 (공급률 분모)
        int aptOpened,       // 기개방 단지 수
        int aptUnopened,     // 미개방 단지 수
        double riskBaseline  // 위험지수 baseline (24시간 전체 평균)
) {

    /** CSV 실측으로 검증된 기본값 — DB 없이 돌 때 사용 */
    public static final SupplyStats VERIFIED_DEFAULTS =
            new SupplyStats(39_114, 3_684, 147_580, 17, 193, 37.81);
}

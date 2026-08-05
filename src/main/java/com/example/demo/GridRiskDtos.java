package com.example.demo;

import java.util.List;

/**
 * 지도 히트맵 API의 응답 모양 모음.
 *
 * 프론트 PR #22의 계약(src/types/geo.ts)과 1:1 — 필드명을 바꾸면 지도가 조용히 깨진다.
 * GET /simulate/grid-risk?hour&region&participation
 */
public class GridRiskDtos {

    /** 위험지수 보유 격자 1개 — 위험지수 없는 격자는 응답에 포함하지 않는다 (팀 합의, 이슈 #18) */
    public record GridRiskPoint(
            double lat,
            double lng,
            double riskScore    // 0~100
    ) {}

    /** 리스크 구성 요소 1개: 점수(0~100) + 등급 */
    public record RiskFactor(
            double score,
            String level        // high / medium / low
    ) {}

    /**
     * 위험 구성 3요소 (프론트 화면 기준).
     * 우리 위험지수는 4요소라서 매핑함: parking = 수요압력+공급부족을 가중치 비율로 합산,
     * environment = 환경민감도, traffic = 교통혼잡. (근거는 GridRiskService 참고)
     */
    public record RiskBreakdown(
            RiskFactor parking,
            RiskFactor environment,
            RiskFactor traffic
    ) {}

    /** 24시간 위험지수 커브 — 현재 vs 시나리오 적용 (각 24개, 00~23시) */
    public record HourlyRiskCurve(
            List<Double> current,
            List<Double> projected
    ) {}

    /** GET /simulate/grid-risk 응답 전체 */
    public record GridRiskResponse(
            int hour,
            double globalRisk,
            List<GridRiskPoint> grids,
            RiskBreakdown breakdown,
            HourlyRiskCurve hourlyRisk
    ) {}
}

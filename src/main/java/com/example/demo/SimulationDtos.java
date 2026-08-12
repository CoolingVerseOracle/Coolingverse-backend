package com.example.demo;

import java.util.List;

/**
 * 시뮬레이션 API의 요청/응답 모양 모음.
 *
 * 프론트 타입 파일(src/types/simulation.ts)과 1:1로 맞춘 구조라서
 * 필드 이름을 함부로 바꾸면 프론트가 못 읽는다.
 */
public class SimulationDtos {

    /** 요청: 프론트 SimulationSettings 와 동일 */
    public record SimulationSettings(
            boolean openToPublic,      // 외부인 개방 아파트 포함 여부
            boolean residentsOnly,     // 입주민 전용(미개방) 아파트 포함 여부
            int participationRate,     // 참여율 0~100 (%)
            String openFrom,           // 운영 시작 "HH:mm"
            String openTo,             // 운영 종료 "HH:mm"
            int commercialRadiusM,     // 상업시설 반경(m)
            String region,             // "pangyo" | "bucheon" — null이면 pangyo
            Integer month              // 1~12, null이면 10월
    ) {
        /** 지역·월 없이 만드는 기존 호출용 축약 생성자 (기본 지역 pangyo) */
        public SimulationSettings(boolean openToPublic, boolean residentsOnly, int participationRate,
                                  String openFrom, String openTo, int commercialRadiusM) {
            this(openToPublic, residentsOnly, participationRate, openFrom, openTo,
                    commercialRadiusM, "pangyo", 10);
        }
    }

    /** 대시보드 KPI 카드 1장 (프론트 KpiMetric) */
    public record KpiMetric(
            String id,
            String label,
            double value,
            String unit,
            Double baseline,           // 비교 기준값(없으면 null → JSON에서 생략됨)
            String badge,
            String tone                // positive / negative / warning / neutral
    ) {}

    /** before/after 비교 바 1쌍 (프론트 BeforeAfterMetric) */
    public record BeforeAfterMetric(
            String id,
            String label,
            double before,
            double after,
            String deltaLabel,
            String tone
    ) {}

    /** 차트 점 하나 (프론트 ChartPoint) */
    public record ChartPoint(String label, double value) {}

    /** 주차 지원 현황 도넛 (프론트 ParticipationBreakdown) */
    public record ParticipationBreakdown(
            double rate,
            List<Segment> segments
    ) {
        public record Segment(String label, double value) {}
    }

    /** 위험지수 추이 (프론트 riskTrend) */
    public record RiskTrend(
            List<String> labels,
            List<Double> current,
            List<Double> projected
    ) {}

    /** 응답: 프론트 SimulationResult 와 동일 */
    public record SimulationResult(
            List<KpiMetric> kpis,
            List<BeforeAfterMetric> metricChanges,
            ParticipationBreakdown participation,
            List<ChartPoint> hourlySupply,
            RiskTrend riskTrend
    ) {}
}

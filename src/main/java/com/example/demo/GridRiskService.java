package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.GridRiskDtos.GridRiskResponse;
import com.example.demo.GridRiskDtos.HourlyRiskCurve;
import com.example.demo.GridRiskDtos.RiskBreakdown;
import com.example.demo.GridRiskDtos.RiskFactor;
import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.SimulationService.CoreNumbers;

/**
 * 지도 히트맵 응답 조립 (프론트 PR #22 계약).
 *
 * 4요소 → 3요소 매핑 근거:
 *   프론트 화면은 [주차/환경/교통] 3개, 우리 위험지수는 [수요·공급·혼잡·환경] 4개.
 *   "주차" = 수요압력(가중치 0.25)과 공급부족(0.35)을 가중치 비율로 합산 —
 *   위험지수 수식에서 두 요소가 차지하던 비중(합 0.60)을 그대로 보존한다.
 *   ⚠️ 분석팀 확인 대기 항목 (PR 본문에 명시).
 */
@Service
public class GridRiskService {

    private static final int HOURS = 24;
    /** 수요·공급을 하나로 합칠 때의 가중치 (위험지수 수식의 비중 그대로) */
    private static final double W_DEMAND = 0.25, W_SUPPLY = 0.35;

    private final GridRiskCache cache;
    private final SimulationService simulationService;

    public GridRiskService(GridRiskCache cache, SimulationService simulationService) {
        this.cache = cache;
        this.simulationService = simulationService;
    }

    public GridRiskResponse gridRisk(int hour, int participation) {
        int h = Math.floorMod(hour, HOURS);

        // 시나리오 적용 감소폭: 검증된 시뮬레이션 수식 재사용 (표준 운영시간 08~19시 기준)
        // 이슈 #19 초기 버전: 전 격자 동일 감쇠 (격자 특성별 차등은 분석팀 모델 확정 후)
        double delta = 0;
        if (participation > 0) {
            CoreNumbers core = simulationService.core(
                    new SimulationSettings(true, true, participation, "08:00", "19:00", 500));
            delta = core.riskBefore() - core.riskAfter();
        }
        final double d = delta;

        // 격자별 현재/적용 후 점수
        List<GridRiskDtos.GridRiskPoint> grids = cache.pointsAt(h).stream()
                .map(p -> new GridRiskDtos.GridRiskPoint(
                        p.lat(), p.lng(), p.score(), round1(Math.max(0, p.score() - d))))
                .toList();

        List<Double> current = new ArrayList<>(HOURS);
        List<Double> projected = new ArrayList<>(HOURS);
        for (int i = 0; i < HOURS; i++) {
            double base = round1(cache.avgScoreAt(i));
            current.add(base);
            projected.add(round1(Math.max(0, base - d)));
        }

        double globalRisk = round1(cache.avgScoreAt(h));
        return new GridRiskResponse(
                h,
                globalRisk,
                round1(Math.max(0, globalRisk - d)),
                grids,
                buildBreakdown(h),
                new HourlyRiskCurve(current, projected));
    }

    private RiskBreakdown buildBreakdown(int hour) {
        // 구성요소는 0~1 스케일로 저장돼 있어 0~100 점수로 환산
        double parking = 100 * (W_DEMAND * cache.avgDemandAt(hour) + W_SUPPLY * cache.avgSupplyAt(hour))
                / (W_DEMAND + W_SUPPLY);
        double environment = 100 * cache.avgEnvAt(hour);
        double traffic = 100 * cache.avgTrafficAt(hour);

        return new RiskBreakdown(factor(parking), factor(environment), factor(traffic));
    }

    /** 등급 구간: 60 이상 high / 30 이상 medium / 그 미만 low (팀 확인 대기) */
    private RiskFactor factor(double score) {
        double s = round1(score);
        String level = s >= 60 ? "high" : s >= 30 ? "medium" : "low";
        return new RiskFactor(s, level);
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }
}

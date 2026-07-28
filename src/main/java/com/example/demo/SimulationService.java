package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.example.demo.SimulationDtos.BeforeAfterMetric;
import com.example.demo.SimulationDtos.ChartPoint;
import com.example.demo.SimulationDtos.KpiMetric;
import com.example.demo.SimulationDtos.ParticipationBreakdown;
import com.example.demo.SimulationDtos.RiskTrend;
import com.example.demo.SimulationDtos.SimulationResult;
import com.example.demo.SimulationDtos.SimulationSettings;

/**
 * What-If 시뮬레이션 계산기 (규칙 기반 수식 — 데이터분석 가이드 2026-07-26 확정).
 *
 * 아직 DB(ADB) 연결 전이라, 데이터분석이 확정해 준 집계 상수를 코드에 담아 계산한다.
 * 상수 출처: apartments_with_grid_id.csv 집계 + 분석 가이드의 시나리오 표.
 * DB 연동 후에는 상수 부분만 쿼리로 교체하면 된다(수식은 동일).
 *
 * 검증 기준: 참여율 10/30/50/70/100% 입력 시 분석 가이드 표의
 * 추가 공급·CO2·위험지수 수치가 그대로 재현되어야 한다.
 */
@Service
public class SimulationService {

    // ── 집계 상수 (출처: CSV 실측 + 분석 가이드) ─────────────────────────
    /** 미개방(is_open='N') 193개 단지의 잠재 유휴면 합계 = 슬라이더 100% 시 추가 공급 */
    private static final int IDLE_UNOPENED_TOTAL = 39_114;
    /** 기개방(is_open='Y') 17개 단지의 유휴면 합계 = 이미 활용 가능한 기본 공급 */
    private static final int IDLE_OPENED_TOTAL = 3_684;
    /** 전체 210개 단지 주차면 합계 (공급률 분모) */
    private static final int TOTAL_PARKING = 147_580;
    /** 단지 수 */
    private static final int APT_OPENED = 17, APT_UNOPENED = 193, APT_TOTAL = 210;

    /** 분당구 전체 평균 위험지수 (개방 전 baseline) */
    private static final double RISK_BASELINE = 37.81;
    /** 주차면 1면 개방 시 일일 CO2 저감 계수(kg) — 배회 1.5km + 공회전 4분 */
    private static final double CO2_PER_SPACE_KG = 0.306;

    /**
     * 위험지수 감소폭 앵커 (분석 가이드 시나리오 표).
     * 참여율이 앵커 사이 값이면 직선으로 잇는 선형 보간을 쓴다.
     * (개방 효과는 수확 체감이라 직선 하나로는 못 그리고, 구간별 직선으로 근사)
     */
    private static final double[] ANCHOR_RATE  = {0, 10, 30, 50, 70, 100};
    private static final double[] ANCHOR_DELTA = {0, 0.36, 0.96, 1.31, 1.52, 1.68};

    /** 핵심 계산 결과 묶음 — 시뮬레이션 응답과 시나리오 저장 스냅샷이 공유한다 */
    public record CoreNumbers(int addedSupply, int totalSupply, double co2Kg,
                              double riskBefore, double riskAfter, double deltaPct) {}

    /** 핵심 수식 계산 (공급·CO2·위험지수) — 시나리오 저장 시에도 이 메서드를 쓴다 */
    public CoreNumbers core(SimulationSettings s) {
        int p = clamp(s.participationRate(), 0, 100);

        // 1) 추가 공급: 미개방 단지 유휴면 × 참여율 (입주민 전용을 포함해야 발생)
        int added = s.residentsOnly() ? (int) Math.floor(IDLE_UNOPENED_TOTAL * p / 100.0) : 0;
        // 기본 공급: 이미 외부인에게 개방된 단지의 유휴면 (외부인 개방 포함 시)
        int base = s.openToPublic() ? IDLE_OPENED_TOTAL : 0;
        int totalSupply = base + added;

        // 2) 운영시간: 시작·종료 시각 블록을 모두 세는 방식 (08:00~19:00 = 12시간)
        //    — 분석 가이드의 시나리오 표 수치(예: 10%→1,436.12kg)와 일치하도록 맞춤
        double hours = (toMinutes(s.openTo()) - toMinutes(s.openFrom())) / 60.0 + 1;

        // 3) 일일 CO2 저감량(kg) = 추가 개방 면수 × 0.306 × (운영시간 / 10)
        double co2Kg = round2(added * CO2_PER_SPACE_KG * (hours / 10.0));

        // 4) 위험지수: baseline − 감소폭(앵커 보간). 추가 개방이 없으면 감소도 없음
        double delta = s.residentsOnly() ? interpolateDelta(p) : 0;
        double riskAfter = round2(RISK_BASELINE - delta);

        // 위험지수 감소율(%) — 근사 지표들의 재료
        double deltaPct = round1(100.0 * delta / RISK_BASELINE);

        return new CoreNumbers(added, totalSupply, co2Kg, RISK_BASELINE, riskAfter, deltaPct);
    }

    /** 시뮬레이션 실행 — 프론트 SimulationResult 모양으로 반환 */
    public SimulationResult simulate(SimulationSettings s) {
        int p = clamp(s.participationRate(), 0, 100);
        CoreNumbers c = core(s);

        // 공급률(%): 개방 유휴면 / 전체 주차면
        double supplyRateBefore = round1(100.0 * (s.openToPublic() ? IDLE_OPENED_TOTAL : 0) / TOTAL_PARKING);
        double supplyRateAfter = round1(100.0 * c.totalSupply() / TOTAL_PARKING);

        return new SimulationResult(
                buildKpis(c.totalSupply(), c.addedSupply(), c.co2Kg(), c.riskAfter(), c.deltaPct()),
                buildMetricChanges(c.riskAfter(), c.deltaPct(), supplyRateBefore, supplyRateAfter),
                buildParticipation(p),
                buildHourlySupply(s, c.totalSupply()),
                buildRiskTrend());
    }

    // ── 응답 조립 ─────────────────────────────────────────────────────

    private List<KpiMetric> buildKpis(int totalSupply, int added, double co2Kg,
                                      double riskAfter, double deltaPct) {
        // ⚠️ illegalParking/congestion 은 아직 전용 산식이 없어 위험지수 감소율로 근사.
        //    DB 연동 후 격자별 단속·혼잡 데이터로 정밀화 예정 (TODO)
        return List.of(
                new KpiMetric("supply", "유휴 주차 공급 가능 대수", totalSupply, "면",
                        null, "+" + added + "면", "positive"),
                new KpiMetric("illegalParking", "불법주정차 감소 예측", -deltaPct, "%",
                        null, deltaPct > 0 ? "개선 중" : "변화 없음", "warning"),
                new KpiMetric("congestion", "교통 혼잡 완화 예측", -round1(deltaPct * 0.6), "%",
                        null, "원활", "neutral"),
                new KpiMetric("carbon", "탄소배출 저감 예측", co2Kg, "kg/일",
                        null, co2Kg > 0 ? "우수" : "변화 없음", "positive"),
                new KpiMetric("risk", "도시 교통·환경 위험지수", riskAfter, "",
                        RISK_BASELINE, "위험도 감소", "negative"));
    }

    private List<BeforeAfterMetric> buildMetricChanges(double riskAfter, double deltaPct,
                                                       double supplyRateBefore, double supplyRateAfter) {
        double supplyRateGain = round1(supplyRateAfter - supplyRateBefore);
        return List.of(
                new BeforeAfterMetric("risk", "위험지수", RISK_BASELINE, riskAfter,
                        "-" + deltaPct + "%", "negative"),
                new BeforeAfterMetric("illegal", "불법주정차 건수", 100, round1(100 - deltaPct),
                        "-" + deltaPct + "%", "warning"),
                new BeforeAfterMetric("supplyRate", "주차 공급률", supplyRateBefore, supplyRateAfter,
                        "+" + supplyRateGain + "%p", "positive"));
    }

    /** 도넛: 210개 단지를 [기개방 / 참여 예정 / 미참여] 로 나눈 비율(%) */
    private ParticipationBreakdown buildParticipation(int p) {
        int joining = (int) Math.floor(APT_UNOPENED * p / 100.0); // 참여 예정 단지 수
        double opened = round1(100.0 * APT_OPENED / APT_TOTAL);
        double planned = round1(100.0 * joining / APT_TOTAL);
        double none = round1(100 - opened - planned);
        return new ParticipationBreakdown(p, List.of(
                new ParticipationBreakdown.Segment("개방", opened),
                new ParticipationBreakdown.Segment("예정", planned),
                new ParticipationBreakdown.Segment("미참여", none)));
    }

    /**
     * 시간대별 공급: 운영시간 안에서는 총 공급량이 그대로 유지되는 평평한 그래프.
     * (시간대별 유휴 변동 데이터(PARKING_IDLE)가 1차에서 제외되어 상세 곡선은 없음)
     */
    private List<ChartPoint> buildHourlySupply(SimulationSettings s, int totalSupply) {
        int from = toMinutes(s.openFrom()) / 60;
        int to = toMinutes(s.openTo()) / 60;
        List<ChartPoint> points = new ArrayList<>();
        for (int h = from; h <= to; h++) {
            points.add(new ChartPoint(String.format("%02d시", h), totalSupply));
        }
        return points;
    }

    /** 위험지수 추이: 개방률 단계별 현재(고정) vs 예측(감소) — 분석 가이드 표 그대로 */
    private RiskTrend buildRiskTrend() {
        List<String> labels = new ArrayList<>();
        List<Double> current = new ArrayList<>();
        List<Double> projected = new ArrayList<>();
        for (int i = 0; i < ANCHOR_RATE.length; i++) {
            labels.add((int) ANCHOR_RATE[i] + "%");
            current.add(RISK_BASELINE);
            projected.add(round2(RISK_BASELINE - ANCHOR_DELTA[i]));
        }
        return new RiskTrend(labels, current, projected);
    }

    // ── 계산 도우미 ───────────────────────────────────────────────────

    /** 앵커 사이를 직선으로 잇는 선형 보간 */
    private double interpolateDelta(int rate) {
        for (int i = 1; i < ANCHOR_RATE.length; i++) {
            if (rate <= ANCHOR_RATE[i]) {
                double span = ANCHOR_RATE[i] - ANCHOR_RATE[i - 1];
                double t = (rate - ANCHOR_RATE[i - 1]) / span;
                return ANCHOR_DELTA[i - 1] + t * (ANCHOR_DELTA[i] - ANCHOR_DELTA[i - 1]);
            }
        }
        return ANCHOR_DELTA[ANCHOR_DELTA.length - 1];
    }

    /** "HH:mm" → 하루 중 분(minute) 위치. 형식이 이상하면 0 취급 */
    private int toMinutes(String hhmm) {
        try {
            String[] parts = hhmm.split(":");
            return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
        } catch (Exception e) {
            return 0;
        }
    }

    private int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private double round1(double v) {
        return Math.round(v * 10) / 10.0;
    }

    private double round2(double v) {
        return Math.round(v * 100) / 100.0;
    }
}

package com.example.demo;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

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

    // ── 집계값: 운영은 RegionalStatsService가 지역·월별 활성 데이터로 조회,
    //    아래 상수는 SupplyStats 직접 주입(테스트·검증된 기본값) 경로에서만 쓰인다 ──
    private final int IDLE_UNOPENED_TOTAL;  // 미개방 단지 잠재 유휴면 합 = 100% 시 추가 공급
    private final int IDLE_OPENED_TOTAL;    // 기개방 단지 유휴면 합 = 기본 공급
    private final int TOTAL_PARKING;        // 전체 주차면 합 (공급률 분모)
    private final int APT_OPENED, APT_UNOPENED, APT_TOTAL;  // 단지 수
    private final double RISK_BASELINE;     // 위험지수 baseline (24시간 전체 평균)
    private final RegionalStatsService regionalStats;

    /** 주차면 1면 개방 시 일일 CO2 저감 계수(kg) — 배회 1.5km + 공회전 4분 (분석 확정 상수) */
    private static final double CO2_PER_SPACE_KG = 0.306;

    public SimulationService(SupplyStats stats) {
        this.regionalStats = null;
        this.IDLE_UNOPENED_TOTAL = stats.idleUnopened();
        this.IDLE_OPENED_TOTAL = stats.idleOpened();
        this.TOTAL_PARKING = stats.totalParking();
        this.APT_OPENED = stats.aptOpened();
        this.APT_UNOPENED = stats.aptUnopened();
        this.APT_TOTAL = stats.aptOpened() + stats.aptUnopened();
        this.RISK_BASELINE = stats.riskBaseline();
    }

    /** 운영에서는 지역·월별 활성 데이터 집계를 사용한다. */
    @Autowired
    public SimulationService(RegionalStatsService regionalStats) {
        this.regionalStats = regionalStats;
        SupplyStats defaults = SupplyStats.VERIFIED_DEFAULTS;
        this.IDLE_UNOPENED_TOTAL = defaults.idleUnopened();
        this.IDLE_OPENED_TOTAL = defaults.idleOpened();
        this.TOTAL_PARKING = defaults.totalParking();
        this.APT_OPENED = defaults.aptOpened();
        this.APT_UNOPENED = defaults.aptUnopened();
        this.APT_TOTAL = defaults.aptOpened() + defaults.aptUnopened();
        this.RISK_BASELINE = defaults.riskBaseline();
    }

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
        SupplyStats stats = statsFor(s);
        int p = clamp(s.participationRate(), 0, 100);

        // 1) 추가 공급: 미개방 단지 유휴면 × 참여율 (입주민 전용을 포함해야 발생)
        int added = s.residentsOnly() ? (int) Math.floor(stats.idleUnopened() * p / 100.0) : 0;
        // 기본 공급: 이미 외부인에게 개방된 단지의 유휴면 (외부인 개방 포함 시)
        int base = s.openToPublic() ? stats.idleOpened() : 0;
        int totalSupply = base + added;

        // 2) 운영시간: 시작·종료 시각 블록을 모두 세는 방식 (08:00~19:00 = 12시간)
        //    — 분석 가이드의 시나리오 표 수치(예: 10%→1,436.12kg)와 일치하도록 맞춤
        //    자정을 넘기는 설정(예: 22:00~06:00)도 음수가 되지 않게 하루 단위로 감아서 계산
        double hours = Math.floorMod(toMinutes(s.openTo()) - toMinutes(s.openFrom()), 24 * 60) / 60.0 + 1;

        // 3) 일일 CO2 저감량(kg) = 추가 개방 면수 × 0.306 × (운영시간 / 10)
        double co2Kg = round2(added * CO2_PER_SPACE_KG * (hours / 10.0));

        // 4) 위험지수: baseline − 감소폭(앵커 보간). 추가 개방이 없으면 감소도 없음
        double delta = s.residentsOnly() ? interpolateDelta(p) : 0;
        double riskAfter = round2(stats.riskBaseline() - delta);

        // 위험지수 감소율(%) — 근사 지표들의 재료
        double deltaPct = round1(100.0 * delta / stats.riskBaseline());

        return new CoreNumbers(added, totalSupply, co2Kg, stats.riskBaseline(), riskAfter, deltaPct);
    }

    /** 시뮬레이션 실행 — 프론트 SimulationResult 모양으로 반환 */
    public SimulationResult simulate(SimulationSettings s) {
        SupplyStats stats = statsFor(s);
        int p = clamp(s.participationRate(), 0, 100);
        CoreNumbers c = core(s);

        // 공급률(%): 개방 유휴면 / 전체 주차면
        double supplyRateBefore = round1(100.0 * (s.openToPublic() ? stats.idleOpened() : 0) / stats.totalParking());
        double supplyRateAfter = round1(100.0 * c.totalSupply() / stats.totalParking());

        return new SimulationResult(
                buildKpis(c.totalSupply(), c.addedSupply(), c.co2Kg(), c.riskBefore(), c.riskAfter(), c.deltaPct()),
                buildMetricChanges(c.riskBefore(), c.riskAfter(), c.deltaPct(), supplyRateBefore, supplyRateAfter),
                buildParticipation(p, stats),
                buildHourlySupply(s, c.totalSupply()),
                buildRiskTrend(c.riskBefore()));
    }

    // ── 응답 조립 ─────────────────────────────────────────────────────

    private List<KpiMetric> buildKpis(int totalSupply, int added, double co2Kg, double riskBaseline,
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
                        riskBaseline, "위험도 감소", "negative"));
    }

    private List<BeforeAfterMetric> buildMetricChanges(double riskBaseline, double riskAfter, double deltaPct,
                                                       double supplyRateBefore, double supplyRateAfter) {
        double supplyRateGain = round1(supplyRateAfter - supplyRateBefore);
        return List.of(
                new BeforeAfterMetric("risk", "위험지수", riskBaseline, riskAfter,
                        "-" + deltaPct + "%", "negative"),
                new BeforeAfterMetric("illegal", "불법주정차 건수", 100, round1(100 - deltaPct),
                        "-" + deltaPct + "%", "warning"),
                new BeforeAfterMetric("supplyRate", "주차 공급률", supplyRateBefore, supplyRateAfter,
                        "+" + supplyRateGain + "%p", "positive"));
    }

    /** 도넛: 210개 단지를 [기개방 / 참여 예정 / 미참여] 로 나눈 비율(%) */
    private ParticipationBreakdown buildParticipation(int p, SupplyStats stats) {
        int apartmentTotal = Math.max(stats.aptOpened() + stats.aptUnopened(), 1);
        int joining = (int) Math.floor(stats.aptUnopened() * p / 100.0); // 참여 예정 단지 수
        double opened = round1(100.0 * stats.aptOpened() / apartmentTotal);
        double planned = round1(100.0 * joining / apartmentTotal);
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
        int blocks = Math.floorMod(toMinutes(s.openTo()) / 60 - from, 24) + 1; // 자정 넘김도 안전
        List<ChartPoint> points = new ArrayList<>();
        for (int i = 0; i < blocks; i++) {
            points.add(new ChartPoint(String.format("%02d시", (from + i) % 24), totalSupply));
        }
        return points;
    }

    /** 위험지수 추이: 개방률 단계별 현재(고정) vs 예측(감소) — 분석 가이드 표 그대로 */
    private RiskTrend buildRiskTrend(double riskBaseline) {
        List<String> labels = new ArrayList<>();
        List<Double> current = new ArrayList<>();
        List<Double> projected = new ArrayList<>();
        for (int i = 0; i < ANCHOR_RATE.length; i++) {
            labels.add((int) ANCHOR_RATE[i] + "%");
            current.add(riskBaseline);
            projected.add(round2(riskBaseline - ANCHOR_DELTA[i]));
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

    private SupplyStats statsFor(SimulationSettings settings) {
        if (regionalStats == null) {
            return new SupplyStats(IDLE_UNOPENED_TOTAL, IDLE_OPENED_TOTAL, TOTAL_PARKING,
                    APT_OPENED, APT_UNOPENED, RISK_BASELINE);
        }
        Regions region = Regions.requireActive(settings.region());
        int month = settings.month() == null ? 10 : settings.month();
        if (month < 1 || month > 12) throw new IllegalArgumentException("월은 1~12 범위여야 합니다.");
        return regionalStats.get(region.code(), GridRiskCache.DEFAULT_YEAR, month);
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

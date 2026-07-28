package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.SimulationService.CoreNumbers;

/**
 * 시뮬레이션 수식 검증 테스트.
 *
 * 기준: 데이터분석 최종 가이드(2026-07-26)의 단계별 개방 시나리오 표.
 * 이 숫자들이 틀어지면 대시보드·시나리오 스냅샷이 전부 어긋나므로,
 * 코드를 고칠 때마다 이 테스트로 안전을 확인한다. (gradlew test)
 */
class SimulationServiceTest {

    private final SimulationService service = new SimulationService();

    /** 표준 설정(08~19시, 외부인+입주민 포함)으로 참여율만 바꾼 설정 생성 */
    private SimulationSettings settings(int rate) {
        return new SimulationSettings(true, true, rate, "08:00", "19:00", 500);
    }

    @ParameterizedTest(name = "참여율 {0}% → 공급 {1}면, CO2 {2}kg, 위험지수 {3}")
    @CsvSource({
            //  참여율, 추가공급,   CO2(kg),   위험지수(후)  — 분석 가이드 표 그대로
            "  10,    3911,   1436.12,  37.45",
            "  30,   11734,   4308.72,  36.85",
            "  50,   19557,   7181.33,  36.50",
            "  70,   27379,  10053.57,  36.29",
            " 100,   39114,  14362.66,  36.13",
    })
    @DisplayName("분석 가이드 시나리오 표 5단계 재현")
    void reproducesAnalysisGuideTable(int rate, int expectedSupply,
                                      double expectedCo2, double expectedRisk) {
        CoreNumbers core = service.core(settings(rate));

        assertEquals(expectedSupply, core.addedSupply());
        assertEquals(expectedCo2, core.co2Kg(), 0.001);
        assertEquals(expectedRisk, core.riskAfter(), 0.001);
        assertEquals(37.81, core.riskBefore(), 0.001);
    }

    @Test
    @DisplayName("참여율 0% = baseline 그대로 (변화 없음)")
    void zeroParticipationMeansNoChange() {
        CoreNumbers core = service.core(settings(0));

        assertEquals(0, core.addedSupply());
        assertEquals(0.0, core.co2Kg(), 0.001);
        assertEquals(37.81, core.riskAfter(), 0.001);
    }

    @Test
    @DisplayName("앵커 사이 값(45%)은 선형 보간: 30%(0.96)과 50%(1.31) 사이")
    void interpolatesBetweenAnchors() {
        CoreNumbers core = service.core(settings(45));

        // 45%는 30~50 구간의 75% 지점: 0.96 + 0.75×(1.31-0.96) = 1.2225 → 37.81-1.2225 = 36.59
        assertEquals(36.59, core.riskAfter(), 0.001);
        // 공급은 정비례: floor(39114 × 0.45) = 17601
        assertEquals(17601, core.addedSupply());
    }

    @Test
    @DisplayName("입주민 전용 미포함이면 추가 개방이 없다 (공급·CO2·위험지수 변화 없음)")
    void noResidentsMeansNoAdditionalSupply() {
        SimulationSettings s = new SimulationSettings(true, false, 50, "08:00", "19:00", 500);
        CoreNumbers core = service.core(s);

        assertEquals(0, core.addedSupply());
        assertEquals(0.0, core.co2Kg(), 0.001);
        assertEquals(37.81, core.riskAfter(), 0.001);
        // 기개방 단지 공급(3,684면)은 그대로 포함
        assertEquals(3684, core.totalSupply());
    }

    @Test
    @DisplayName("운영시간이 CO2에 반영: 09~18시(10시간 블록)는 08~19시(12시간)보다 적다")
    void operatingHoursAffectCo2() {
        CoreNumbers full = service.core(settings(45)); // 08~19 = 12시간 블록
        CoreNumbers shorter = service.core(
                new SimulationSettings(true, true, 45, "09:00", "18:00", 500)); // 10시간 블록

        // 17601 × 0.306 × (10/10) = 5385.906 → 5385.91
        assertEquals(5385.91, shorter.co2Kg(), 0.001);
        assertTrue(shorter.co2Kg() < full.co2Kg());
    }

    @Test
    @DisplayName("참여율은 0~100으로 잘린다 (150 입력 = 100 취급)")
    void participationIsClamped() {
        CoreNumbers over = service.core(settings(150));
        CoreNumbers max = service.core(settings(100));

        assertEquals(max.addedSupply(), over.addedSupply());
        assertEquals(max.riskAfter(), over.riskAfter(), 0.001);
    }

    @Test
    @DisplayName("응답 조립: KPI 5장, 도넛 3조각, 시간대 점 12개(08~19시)")
    void buildsFullResultShape() {
        var result = service.simulate(settings(30));

        assertEquals(5, result.kpis().size());
        assertEquals(3, result.participation().segments().size());
        assertEquals(12, result.hourlySupply().size());
        assertEquals(6, result.riskTrend().labels().size()); // 앵커 0/10/30/50/70/100
    }
}

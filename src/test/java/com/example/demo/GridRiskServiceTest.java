package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.demo.GridRiskDtos.GridRiskResponse;
import com.example.demo.db.RiskIndexRepository.GridRiskRow;

/**
 * 지도 히트맵 응답 조립 검증 — 표본 격자 2개로 캐시를 채워 계산 논리를 확인한다.
 * (실데이터 규모 검증은 배포 후 실 ADB 호출로 별도 수행)
 */
class GridRiskServiceTest {

    /** 테스트용 행 — 리포지토리 프로젝션 인터페이스를 record로 구현 */
    record Row(Long getGridId, String getRegionCode, Integer getAnalysisYear, Integer getAnalysisMonth,
               Integer getHourOfDay, Double getLat, Double getLng,
               Double getRiskScore, Double getDemand, Double getSupply,
               Double getTraffic, Double getEnv) implements GridRiskRow {}

    private GridRiskService service;

    @BeforeEach
    void setUp() {
        List<GridRiskRow> rows = new ArrayList<>();
        // 24시간 × 격자 2개: 14시에만 구분되는 값(30/50), 나머지 시간은 전부 40
        for (int h = 0; h < 24; h++) {
            double a = (h == 14) ? 30 : 40;
            double b = (h == 14) ? 50 : 40;
            rows.add(new Row(1L, "pangyo", 2025, 10, h, 37.37, 127.11, a, 0.2, 0.4, 0.1, 0.5));
            rows.add(new Row(2L, "pangyo", 2025, 10, h, 37.40, 127.14, b, 0.2, 0.4, 0.1, 0.5));
            rows.add(new Row(1L, "bucheon", 2025, 10, h, 37.50, 126.76, 70.0, 0.7, 0.7, 0.7, 0.7));
        }
        GridRiskCache cache = new GridRiskCache(null) {};  // 리포지토리 미사용 생성
        cache.loadRows(rows);
        service = new GridRiskService(cache, new SimulationService(SupplyStats.VERIFIED_DEFAULTS));
    }

    @Test
    @DisplayName("응답 모양: 격자 목록·전체 평균·24시간 커브가 계약대로 나온다")
    void buildsContractShape() {
        GridRiskResponse res = service.gridRisk(14, 0);

        assertEquals(14, res.hour());
        assertEquals(2, res.grids().size());
        assertEquals(40.0, res.globalRisk(), 0.001);          // (30+50)/2
        assertEquals(24, res.hourlyRisk().current().size());
        assertEquals(24, res.hourlyRisk().projected().size());
        // 참여율 0 → projected == current
        assertEquals(res.hourlyRisk().current(), res.hourlyRisk().projected());
    }

    @Test
    @DisplayName("participation을 주면 projected 커브가 검증된 감소폭만큼 내려간다 (30% = -0.96)")
    void appliesScenarioDelta() {
        GridRiskResponse res = service.gridRisk(14, 30);

        // current 40.0 → projected 39.04 ≈ 39.0 (소수 1자리 반올림)
        assertEquals(40.0, res.hourlyRisk().current().get(0), 0.001);
        assertEquals(39.0, res.hourlyRisk().projected().get(0), 0.001);
        assertTrue(res.hourlyRisk().projected().get(14) < res.hourlyRisk().current().get(14));
    }

    @Test
    @DisplayName("이슈 #19: 격자별 projectedRiskScore와 globalRiskProjected가 감소폭을 반영한다")
    void providesProjectedPerGrid() {
        GridRiskResponse res = service.gridRisk(14, 30);   // 30% → 감소폭 0.96

        // 격자별: 30.0 → 29.0, 50.0 → 49.0 (round1)
        assertEquals(30.0, res.grids().get(0).riskScore(), 0.001);
        assertEquals(29.0, res.grids().get(0).projectedRiskScore(), 0.001);
        assertEquals(49.0, res.grids().get(1).projectedRiskScore(), 0.001);
        // 전체 평균: 40.0 → 39.0
        assertEquals(39.0, res.globalRiskProjected(), 0.001);

        // 참여율 0이면 현재와 동일
        GridRiskResponse zero = service.gridRisk(14, 0);
        assertEquals(zero.globalRisk(), zero.globalRiskProjected(), 0.001);
        assertEquals(zero.grids().get(0).riskScore(), zero.grids().get(0).projectedRiskScore(), 0.001);
    }

    @Test
    @DisplayName("breakdown 4→3 매핑: parking은 수요·공급의 가중 평균, 스케일은 0~100")
    void mapsBreakdown() {
        GridRiskResponse res = service.gridRisk(14, 0);

        // parking = (0.25×0.2 + 0.35×0.4) / 0.60 × 100 = 31.7
        assertEquals(31.7, res.breakdown().parking().score(), 0.05);
        assertEquals("medium", res.breakdown().parking().level());
        assertEquals(50.0, res.breakdown().environment().score(), 0.001);  // 0.5×100
        assertEquals("medium", res.breakdown().environment().level());
        assertEquals(10.0, res.breakdown().traffic().score(), 0.001);      // 0.1×100
        assertEquals("low", res.breakdown().traffic().level());
    }

    @Test
    @DisplayName("hour 경계값: 음수·24 이상도 0~23으로 안전하게 순환")
    void clampsHour() {
        assertEquals(23, service.gridRisk(-1, 0).hour());
        assertEquals(1, service.gridRisk(25, 0).hour());
    }

    @Test
    @DisplayName("활성 데이터가 없는 지역·월은 빈 200 응답 대신 IllegalArgumentException(→400)")
    void rejectsMissingDataset() {
        assertThrows(IllegalArgumentException.class,
                () -> service.gridRisk("pangyo", 2025, 3, 14, 0));   // 3월 데이터 미적재
        assertThrows(IllegalArgumentException.class,
                () -> service.gridRisk("bucheon", 2024, 10, 14, 0)); // 다른 연도
    }

    @Test
    @DisplayName("신규 지역(산본·일산)은 활성 카탈로그에 있어야 API가 열리고, 데이터 미적재면 400")
    void newRegionsRegisteredButRequireData() {
        assertEquals("sanbon", Regions.requireActive("sanbon").code());
        assertEquals("ilsan", Regions.requireActive("ilsan").code());
        assertEquals("pyeongchon", Regions.requireActive("pyeongchon").code());
        assertThrows(IllegalArgumentException.class, () -> Regions.requireActive("ingye"));
        // 캐시에 데이터가 없으면 400 — 카탈로그 등록만으로 빈 응답이 나가지 않는다
        assertThrows(IllegalArgumentException.class,
                () -> service.gridRisk("sanbon", 2025, 10, 14, 0));
        assertThrows(IllegalArgumentException.class,
                () -> service.gridRisk("pyeongchon", 2025, 10, 14, 0));
    }

    @Test
    @DisplayName("같은 grid_id·월·시간이어도 판교와 부천 캐시는 서로 섞이지 않는다")
    void isolatesRegions() {
        GridRiskResponse pangyo = service.gridRisk("pangyo", 2025, 10, 14, 0);
        GridRiskResponse bucheon = service.gridRisk("bucheon", 2025, 10, 14, 0);
        assertEquals(2, pangyo.grids().size());
        assertEquals(1, bucheon.grids().size());
        assertEquals(40.0, pangyo.globalRisk(), 0.001);
        assertEquals(70.0, bucheon.globalRisk(), 0.001);
    }
}

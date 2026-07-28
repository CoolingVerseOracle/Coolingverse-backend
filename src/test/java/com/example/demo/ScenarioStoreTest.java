package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.ScenarioStore.ScenarioEntity;

/**
 * 시나리오 임시 저장소 테스트.
 * 시드 5건 생성, 저장 시 결과 스냅샷 계산, 삭제 동작을 확인한다.
 */
class ScenarioStoreTest {

    private ScenarioStore store;

    @BeforeEach
    void setUp() {
        store = new ScenarioStore(new SimulationService());
    }

    @Test
    @DisplayName("기동 시 대표 개방률 5단계 시드가 생성된다")
    void seedsFiveDefaults() {
        assertEquals(5, store.findAll().size());
    }

    @Test
    @DisplayName("시드 스냅샷이 분석 가이드 표와 일치 (30% = 공급 11,734 / 위험 36.85 / CO2 4,308.72)")
    void seedSnapshotsMatchAnalysisGuide() {
        ScenarioEntity standard = store.findAll().stream()
                .filter(e -> e.settings.participationRate() == 30)
                .findFirst().orElseThrow();

        assertEquals(11734, standard.addedSupply);
        assertEquals(37.81, standard.riskBefore, 0.001);
        assertEquals(36.85, standard.riskAfter, 0.001);
        assertEquals(4308.72, standard.carbonReduction, 0.001);
    }

    @Test
    @DisplayName("저장하면 결과 스냅샷이 즉석 계산되어 함께 보관된다")
    void saveComputesSnapshot() {
        SimulationSettings settings =
                new SimulationSettings(true, true, 45, "09:00", "18:00", 500);

        ScenarioEntity saved = store.save("테스트", "메모", settings);

        assertEquals(17601, saved.addedSupply);          // floor(39114 × 0.45)
        assertEquals(36.59, saved.riskAfter, 0.001);     // 선형 보간
        assertEquals(5385.91, saved.carbonReduction, 0.001); // 10시간 블록
        assertEquals(6, store.findAll().size());         // 시드 5 + 새 저장 1
    }

    @Test
    @DisplayName("삭제하면 사라지고, 없는 id 삭제는 false")
    void deleteRemovesEntity() {
        ScenarioEntity saved = store.save("지울 것", null,
                new SimulationSettings(true, true, 10, "08:00", "19:00", 500));

        assertTrue(store.deleteById(saved.id));
        assertNull(store.findById(saved.id));
        assertFalse(store.deleteById(99_999));
    }
}

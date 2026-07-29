package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.demo.SimulationDtos.SimulationSettings;
import com.example.demo.ScenarioStore.ScenarioEntity;

/**
 * 시나리오 저장소(JPA 기반) 테스트 — 로컬 H2 DB로 실행.
 * 시드 생성, 저장 시 결과 스냅샷 계산, 삭제 동작을 확인한다.
 * 다른 테스트와 DB를 공유할 수 있어 개수 검증은 상대값으로 한다.
 */
@SpringBootTest
class ScenarioStoreTest {

    @Autowired
    private ScenarioStore store;

    @Test
    @DisplayName("기동 시 대표 개방률 5단계 시드가 생성된다 (10~100% 전부 존재)")
    void seedsFiveDefaults() {
        var all = store.findAll();
        assertTrue(all.size() >= 5);
        for (int rate : new int[] {10, 30, 50, 70, 100}) {
            assertTrue(all.stream().anyMatch(e -> e.settings.participationRate() == rate),
                    rate + "% 시드가 없음");
        }
    }

    @Test
    @DisplayName("시드 스냅샷이 분석 가이드 표와 일치 (30% = 공급 11,734 / 위험 36.85 / CO2 4,308.72)")
    void seedSnapshotsMatchAnalysisGuide() {
        ScenarioEntity standard = store.findAll().stream()
                .filter(e -> e.name.contains("표준"))
                .findFirst().orElseThrow();

        assertEquals(11734, standard.addedSupply);
        assertEquals(37.81, standard.riskBefore, 0.001);
        assertEquals(36.85, standard.riskAfter, 0.001);
        assertEquals(4308.72, standard.carbonReduction, 0.001);
    }

    @Test
    @DisplayName("저장하면 결과 스냅샷이 즉석 계산되어 DB에 함께 보관된다")
    void saveComputesSnapshot() {
        int before = store.findAll().size();
        SimulationSettings settings =
                new SimulationSettings(true, true, 45, "09:00", "18:00", 500);

        ScenarioEntity saved = store.save("테스트", "메모", settings);

        assertEquals(17601, saved.addedSupply);          // floor(39114 × 0.45)
        assertEquals(36.59, saved.riskAfter, 0.001);     // 선형 보간
        assertEquals(5385.91, saved.carbonReduction, 0.001); // 10시간 블록
        assertEquals(before + 1, store.findAll().size());

        store.deleteById(saved.id);                       // 뒷정리
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

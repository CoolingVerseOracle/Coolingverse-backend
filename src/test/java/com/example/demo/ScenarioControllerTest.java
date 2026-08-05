package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.demo.ScenarioDtos.CreateScenarioRequest;
import com.example.demo.ScenarioDtos.ScenarioDetail;
import com.example.demo.SimulationDtos.SimulationSettings;

/** 시나리오 저장 API의 입력 검증(400)과 정상 저장(200)을 확인한다. (로컬 H2 DB) */
@SpringBootTest
class ScenarioControllerTest {

    @Autowired
    private ScenarioController controller;

    private SimulationSettings validSettings() {
        return new SimulationSettings(true, true, 30, "08:00", "19:00", 500);
    }

    @Test
    @DisplayName("정상 저장 → 200 (뒷정리로 삭제까지 확인)")
    void validCreateReturnsOk() {
        var res = controller.create(new CreateScenarioRequest("이름", "메모", validSettings()));
        assertEquals(200, res.getStatusCode().value());

        // 뒷정리 겸 삭제 경로 확인
        ScenarioDetail body = (ScenarioDetail) res.getBody();
        assertEquals(204, controller.delete(Long.parseLong(body.id())).getStatusCode().value());
    }

    @Test
    @DisplayName("참여율(lt10/gte10)·시간대(day/night) 필터가 동작한다 — 이슈 #1")
    void filtersByParticipationAndTimeSlot() {
        // 구분되는 표본 2개: 5% 야간(19~22시) / 50% 주간(09~17시)
        var low = (ScenarioDetail) controller.create(new CreateScenarioRequest(
                "필터검증 야간", "", new SimulationSettings(true, true, 5, "19:00", "22:00", 500))).getBody();
        var high = (ScenarioDetail) controller.create(new CreateScenarioRequest(
                "필터검증 주간", "", new SimulationSettings(true, true, 50, "09:00", "17:00", 500))).getBody();

        // 참여율 10% 미만 → 야간(5%)만
        var lt10 = controller.list("all", "필터검증", "lt10", "all", "updatedDesc", 1, 10);
        assertEquals(1, lt10.total());
        assertEquals("필터검증 야간", lt10.items().get(0).name());

        // 참여율 10% 이상 → 주간(50%)만
        var gte10 = controller.list("all", "필터검증", "gte10", "all", "updatedDesc", 1, 10);
        assertEquals(1, gte10.total());
        assertEquals("필터검증 주간", gte10.items().get(0).name());

        // 시간대 주간(09~18) → 주간 운영만 / 야간(18~23) → 야간 운영만
        var day = controller.list("all", "필터검증", "all", "day", "updatedDesc", 1, 10);
        assertEquals(1, day.total());
        assertEquals("필터검증 주간", day.items().get(0).name());

        var night = controller.list("all", "필터검증", "all", "night", "updatedDesc", 1, 10);
        assertEquals(1, night.total());
        assertEquals("필터검증 야간", night.items().get(0).name());

        // 뒷정리
        controller.delete(Long.parseLong(low.id()));
        controller.delete(Long.parseLong(high.id()));
    }

    @Test
    @DisplayName("이슈 #22: 지역 코드 필터와 participationRate 필드가 동작한다")
    void filtersByRegionCode() {
        var pangyo = (ScenarioDetail) controller.create(new CreateScenarioRequest("지역검증 판교", "",
                new SimulationSettings(true, true, 20, "08:00", "19:00", 500, "pangyo", null))).getBody();
        var ingye = (ScenarioDetail) controller.create(new CreateScenarioRequest("지역검증 인계", "",
                new SimulationSettings(true, true, 60, "08:00", "19:00", 500, "ingye", null))).getBody();

        // 코드로 필터
        var onlyIngye = controller.list("ingye", "지역검증", "all", "all", "updatedDesc", 1, 10);
        assertEquals(1, onlyIngye.total());
        assertEquals("지역검증 인계", onlyIngye.items().get(0).name());
        assertEquals("수원 인계동", onlyIngye.items().get(0).region());
        assertEquals(60, onlyIngye.items().get(0).participationRate());  // 요청 3

        // 표시명으로도 필터 가능 (구버전 호환)
        var byName = controller.list("판교테크노밸리", "지역검증", "all", "all", "updatedDesc", 1, 10);
        assertEquals(1, byName.total());

        controller.delete(Long.parseLong(pangyo.id()));
        controller.delete(Long.parseLong(ingye.id()));
    }

    @Test
    @DisplayName("PATCH — 이름·메모 부분 수정, 설정·스냅샷은 불변 (이슈 #14)")
    void patchUpdatesMetadataOnly() {
        var created = (ScenarioDetail) controller.create(new CreateScenarioRequest(
                "원래 이름", "원래 메모", validSettings())).getBody();
        long id = Long.parseLong(created.id());

        // 이름만 수정 → 메모는 유지
        var renamed = (ScenarioDetail) controller.update(id,
                new ScenarioDtos.UpdateScenarioRequest("바뀐 이름", null)).getBody();
        assertEquals("바뀐 이름", renamed.name());
        assertEquals("원래 메모", renamed.memo());

        // 메모만 수정 → 이름은 유지
        var remarked = (ScenarioDetail) controller.update(id,
                new ScenarioDtos.UpdateScenarioRequest(null, "바뀐 메모")).getBody();
        assertEquals("바뀐 이름", remarked.name());
        assertEquals("바뀐 메모", remarked.memo());

        // 설정값·결과 스냅샷은 그대로 (재계산 없음)
        assertEquals(created.settings().participationRate(), remarked.settings().participationRate());
        assertEquals(created.addedSupply(), remarked.addedSupply());
        assertEquals(created.riskAfter(), remarked.riskAfter(), 0.001);

        controller.delete(id);
    }

    @Test
    @DisplayName("PATCH — 빈 본문·빈 이름은 400, 없는 id는 404")
    void patchValidatesInput() {
        assertEquals(400, controller.update(1L, null).getStatusCode().value());
        assertEquals(400, controller.update(1L,
                new ScenarioDtos.UpdateScenarioRequest(null, null)).getStatusCode().value());
        assertEquals(400, controller.update(1L,
                new ScenarioDtos.UpdateScenarioRequest("  ", null)).getStatusCode().value());
        assertEquals(404, controller.update(99_999L,
                new ScenarioDtos.UpdateScenarioRequest("이름", null)).getStatusCode().value());
    }

    @Test
    @DisplayName("이름/설정 누락 → 500 대신 400으로 안내")
    void invalidCreateReturns400() {
        assertEquals(400, controller.create(new CreateScenarioRequest(null, null, validSettings()))
                .getStatusCode().value());
        assertEquals(400, controller.create(new CreateScenarioRequest("  ", null, validSettings()))
                .getStatusCode().value());
        assertEquals(400, controller.create(new CreateScenarioRequest("이름", null, null))
                .getStatusCode().value());
        assertEquals(400, controller.create(null).getStatusCode().value());
    }
}

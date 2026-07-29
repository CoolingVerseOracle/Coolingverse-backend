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

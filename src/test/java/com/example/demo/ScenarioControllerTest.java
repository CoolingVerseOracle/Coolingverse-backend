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

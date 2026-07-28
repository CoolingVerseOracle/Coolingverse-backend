package com.example.demo;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.SimulationDtos.SimulationResult;
import com.example.demo.SimulationDtos.SimulationSettings;

/**
 * What-If 시뮬레이션 API 창구.
 *
 * 프론트 계약(src/api/simulation.ts 기준):
 *   - fetchSimulationResult()  → GET  /simulate/initial   (대시보드 첫 화면용 기본 결과)
 *   - runSimulation(settings)  → POST /simulate           (슬라이더 조절 시 재계산)
 *
 * 두 API 모두 로그인 토큰(Authorization: Bearer ...)이 있어야 호출된다.
 */
@RestController
public class SimulationController {

    /** 첫 화면 기본 설정: 표준 개방안(30%), 주간 08~19시 — 분석 가이드의 '메인 목표 정책 단계' */
    private static final SimulationSettings DEFAULT_SETTINGS =
            new SimulationSettings(true, true, 30, "08:00", "19:00", 500);

    private final SimulationService simulationService;

    public SimulationController(SimulationService simulationService) {
        this.simulationService = simulationService;
    }

    @GetMapping("/simulate/initial")
    public SimulationResult initial() {
        return simulationService.simulate(DEFAULT_SETTINGS);
    }

    @PostMapping("/simulate")
    public SimulationResult simulate(@RequestBody SimulationSettings settings) {
        return simulationService.simulate(settings);
    }
}

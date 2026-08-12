package com.example.demo;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
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
    private final GridRiskService gridRiskService;

    public SimulationController(SimulationService simulationService, GridRiskService gridRiskService) {
        this.simulationService = simulationService;
        this.gridRiskService = gridRiskService;
    }

    @GetMapping("/simulate/initial")
    public SimulationResult initial() {
        return simulationService.simulate(DEFAULT_SETTINGS);
    }

    @PostMapping("/simulate")
    public SimulationResult simulate(@RequestBody SimulationSettings settings) {
        return simulationService.simulate(settings);
    }

    /**
     * 지도 히트맵 데이터 (이슈 #19 계약).
     * - hour: 0~23 (기본 14시)
     * - region: 위험지수 데이터를 보유한 지역만 응답 — 미지의 코드나 데이터 없는
     *   지역(ingye 등)은 404 (프론트가 샘플 폴백 처리)
     * - participationRate: 마지막 실행 참여율(%) — 격자별 projectedRiskScore와
     *   projected 커브에 감소폭 반영. (구명칭 participation도 호환 수신)
     */
    @GetMapping("/simulate/grid-risk")
    public ResponseEntity<?> gridRisk(
            @RequestParam(defaultValue = "14") int hour,
            @RequestParam(defaultValue = "pangyo") String region,
            @RequestParam(required = false) Integer participationRate,
            @RequestParam(defaultValue = "0") int participation) {
        Regions resolved = Regions.find(region);
        if (resolved == null || !gridRiskService.hasData(resolved)) {
            return ResponseEntity.status(404)
                    .body(Map.of("message", "해당 지역 데이터는 아직 준비되지 않았습니다: " + region));
        }
        int rate = participationRate != null ? participationRate : participation;
        return ResponseEntity.ok(gridRiskService.gridRisk(resolved, hour, rate));
    }
}

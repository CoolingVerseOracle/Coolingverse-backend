package com.example.demo;

import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.ExceptionHandler;

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
            new SimulationSettings(true, true, 30, "08:00", "19:00", 500, "pangyo", 10);

    private final SimulationService simulationService;
    private final GridRiskService gridRiskService;

    public SimulationController(SimulationService simulationService, GridRiskService gridRiskService) {
        this.simulationService = simulationService;
        this.gridRiskService = gridRiskService;
    }

    @GetMapping("/simulate/initial")
    public SimulationResult initial(
            @RequestParam(defaultValue = "pangyo") String region,
            @RequestParam(defaultValue = "10") int month) {
        validate(region, month);
        return simulationService.simulate(new SimulationSettings(true, true, 30, "08:00", "19:00", 500, region, month));
    }

    @PostMapping("/simulate")
    public SimulationResult simulate(@RequestBody SimulationSettings settings) {
        validate(settings.region(), settings.month() == null ? 10 : settings.month());
        return simulationService.simulate(settings);
    }

    /**
     * 지도 히트맵 데이터 (이슈 #19 계약).
     * - hour: 0~23 (기본 14시)
     * - region: 활성 지역 pangyo/bucheon, 비활성·알 수 없는 지역은 400
     * - month: 1~12, 기본 10월
     * - participationRate: 마지막 실행 참여율(%) — 격자별 projectedRiskScore와
     *   projected 커브에 감소폭 반영. (구명칭 participation도 호환 수신)
     */
    @GetMapping("/simulate/grid-risk")
    public ResponseEntity<?> gridRisk(
            @RequestParam(defaultValue = "14") int hour,
            @RequestParam(defaultValue = "pangyo") String region,
            @RequestParam(defaultValue = "10") int month,
            @RequestParam(required = false) Integer participationRate,
            @RequestParam(defaultValue = "0") int participation) {
        Regions active = Regions.requireActive(region);
        validate(active.code(), month);
        int rate = participationRate != null ? participationRate : participation;
        return ResponseEntity.ok(gridRiskService.gridRisk(active.code(), GridRiskCache.DEFAULT_YEAR, month, hour, rate));
    }

    private void validate(String region, int month) {
        Regions.requireActive(region);
        if (month < 1 || month > 12) throw new IllegalArgumentException("월은 1~12 범위여야 합니다.");
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, String>> badRequest(IllegalArgumentException exception) {
        return ResponseEntity.badRequest().body(Map.of("message", exception.getMessage()));
    }
}

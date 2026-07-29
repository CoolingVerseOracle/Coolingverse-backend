package com.example.demo;

import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.demo.ScenarioDtos.CreateScenarioRequest;
import com.example.demo.ScenarioDtos.Paginated;
import com.example.demo.ScenarioDtos.ScenarioDetail;
import com.example.demo.ScenarioDtos.ScenarioRow;
import com.example.demo.ScenarioStore.ScenarioEntity;

/**
 * 시나리오 관리 API 창구 (게시판형 화면 대응).
 *
 * 프론트 계약(src/api/scenarios.ts 기준):
 *   - fetchScenarios(filter) → GET /scenarios?keyword=&sort=&page=&pageSize=
 *     응답은 Paginated<ScenarioRow> (검색/정렬/페이지네이션은 서버가 처리)
 *   - 저장  → POST /scenarios   {name, memo, settings}
 *   - 열기  → GET /scenarios/{id}   (조건 + 결과 스냅샷)
 *   - 삭제  → DELETE /scenarios/{id}   (다중선택 삭제는 프론트가 반복 호출)
 *
 * 모든 API는 로그인 토큰이 있어야 호출된다.
 */
@RestController
public class ScenarioController {

    private static final DateTimeFormatter DOT_DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd");
    private static final DateTimeFormatter FULL_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

    private final ScenarioStore store;

    public ScenarioController(ScenarioStore store) {
        this.store = store;
    }

    @GetMapping("/scenarios")
    public Paginated<ScenarioRow> list(
            @RequestParam(defaultValue = "all") String region,
            @RequestParam(defaultValue = "") String keyword,
            @RequestParam(defaultValue = "all") String participation,
            @RequestParam(defaultValue = "all") String timeSlot,
            @RequestParam(defaultValue = "updatedDesc") String sort,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int pageSize) {

        // 1) 필터: 지역, 이름 검색어, 참여율, 시간대 (프론트 ScenarioFilter와 1:1)
        List<ScenarioEntity> filtered = store.findAll().stream()
                .filter(e -> region.equals("all") || e.region.equals(region))
                .filter(e -> keyword.isBlank()
                        || e.name.toLowerCase().contains(keyword.trim().toLowerCase()))
                .filter(e -> matchesParticipation(e, participation))
                .filter(e -> matchesTimeSlot(e, timeSlot))
                .toList();

        // 2) 정렬: 수정일 최신순/오래된순
        Comparator<ScenarioEntity> byUpdated = Comparator.comparing(e -> e.updatedAt);
        List<ScenarioEntity> sorted = filtered.stream()
                .sorted(sort.equals("updatedAsc") ? byUpdated : byUpdated.reversed())
                .toList();

        // 3) 페이지 자르기
        int total = sorted.size();
        int from = Math.max(0, (page - 1) * pageSize);
        int to = Math.min(total, from + pageSize);
        List<ScenarioRow> items = (from >= total ? List.<ScenarioEntity>of() : sorted.subList(from, to))
                .stream().map(this::toRow).toList();

        return new Paginated<>(items, total, page, pageSize);
    }

    @PostMapping("/scenarios")
    public ResponseEntity<?> create(@RequestBody CreateScenarioRequest req) {
        // 필수값 방어: 이름/설정 없이 저장하면 NPE 500이 나므로 400으로 안내
        if (req == null || req.name() == null || req.name().isBlank() || req.settings() == null) {
            return ResponseEntity.badRequest()
                    .body(Map.of("message", "시나리오 이름과 설정값은 필수입니다."));
        }
        ScenarioEntity saved = store.save(req.name(), req.memo(), req.settings());
        return ResponseEntity.ok(toDetail(saved));
    }

    @GetMapping("/scenarios/{id}")
    public ResponseEntity<ScenarioDetail> detail(@PathVariable long id) {
        ScenarioEntity entity = store.findById(id);
        if (entity == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(toDetail(entity));
    }

    @DeleteMapping("/scenarios/{id}")
    public ResponseEntity<Void> delete(@PathVariable long id) {
        return store.deleteById(id)
                ? ResponseEntity.noContent().build()   // 204: 지웠음
                : ResponseEntity.notFound().build();   // 404: 그런 시나리오 없음
    }

    // ── 필터 도우미 ───────────────────────────────────────────────────

    /** 참여율 필터 (프론트 드롭다운 값): lt10 = 10% 미만, gte10 = 10% 이상 */
    private boolean matchesParticipation(ScenarioEntity e, String filter) {
        int rate = e.settings.participationRate();
        return switch (filter) {
            case "lt10" -> rate < 10;
            case "gte10" -> rate >= 10;
            default -> true;  // "all" 또는 알 수 없는 값 → 필터 없음
        };
    }

    /**
     * 시간대 필터 (프론트 드롭다운 값): day = 주간(09~18시), night = 야간(18~23시).
     * 운영시간이 해당 구간과 한 시간이라도 "겹치면" 매칭으로 판정한다
     * (예: 08~19시 운영은 주간·야간 양쪽에 해당). 자정 넘김 운영도 안전.
     */
    private boolean matchesTimeSlot(ScenarioEntity e, String filter) {
        return switch (filter) {
            case "day" -> operatesWithin(e, 9, 18);
            case "night" -> operatesWithin(e, 18, 23);
            default -> true;
        };
    }

    /** 운영시간 블록 중 하나라도 [fromHour, toHour) 구간에 들어가면 true */
    private boolean operatesWithin(ScenarioEntity e, int fromHour, int toHour) {
        int start = hourOf(e.settings.openFrom());
        int blocks = Math.floorMod(hourOf(e.settings.openTo()) - start, 24) + 1;
        for (int i = 0; i < blocks; i++) {
            int hour = (start + i) % 24;
            if (hour >= fromHour && hour < toHour) return true;
        }
        return false;
    }

    private int hourOf(String hhmm) {
        try {
            return Integer.parseInt(hhmm.split(":")[0]);
        } catch (Exception ex) {
            return 0;
        }
    }

    // ── 변환 도우미 ───────────────────────────────────────────────────

    /** 목록 행: 조건 요약(예: "30%, 08~19시")을 만들어 프론트 테이블 모양으로 */
    private ScenarioRow toRow(ScenarioEntity e) {
        String conditions = e.settings.participationRate() + "%, "
                + e.settings.openFrom().substring(0, 2) + "~"
                + e.settings.openTo().substring(0, 2) + "시";
        return new ScenarioRow(String.valueOf(e.id), e.name, e.region, conditions,
                e.addedSupply, e.riskBefore, e.riskAfter, e.updatedAt.format(DOT_DATE));
    }

    private ScenarioDetail toDetail(ScenarioEntity e) {
        return new ScenarioDetail(String.valueOf(e.id), e.name, e.memo, e.region,
                e.settings, e.addedSupply, e.riskBefore, e.riskAfter, e.carbonReduction,
                e.createdAt.format(FULL_TIME), e.updatedAt.format(FULL_TIME));
    }
}

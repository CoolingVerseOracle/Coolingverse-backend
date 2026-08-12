package com.example.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.demo.db.RiskIndexRepository;
import com.example.demo.db.RiskIndexRepository.GridRiskRow;

import jakarta.annotation.PostConstruct;

/**
 * 히트맵 데이터 메모리 캐시.
 *
 * risk_index는 분석 배치 산출물(정적)이라, 기동 시 1회만 DB에서 읽어
 * 지역(district)별·시간대별로 정리해 둔다. 이후 요청은 DB를 거치지 않아
 * 24시간 스크러버를 드래그해도 즉시 응답한다. (수만 행 ≈ 수 MB, 메모리 부담 없음)
 *
 * 지역을 나누는 이유: 분당·부천처럼 여러 지역의 위험지수가 한 테이블에 쌓이면
 * 전체 평균·격자 목록이 섞여 지도와 baseline이 오염된다. 캐시가 지역별 조각을
 * 들고 있고, 서비스가 요청 지역의 조각만 꺼내 쓴다.
 * 빈 DB(로컬 H2)에서는 빈 캐시로 동작 — 데이터 없는 지역은 hasDistrict가 false.
 */
@Component
public class GridRiskCache {

    private static final Logger log = LoggerFactory.getLogger(GridRiskCache.class);
    private static final int HOURS = 24;

    /** 캐시가 보관하는 격자 원본 점수 — "적용 후" 값은 요청 시점에 서비스가 계산 */
    public record BasePoint(double lat, double lng, double score) {}

    /** 한 지역의 시간대별 캐시 조각 */
    private static final class Slice {
        final List<List<BasePoint>> pointsByHour = new ArrayList<>();
        final double[][] sums = new double[HOURS][5];
        final int[] counts = new int[HOURS];
        final double[] avgScore = new double[HOURS];
        final double[] avgDemand = new double[HOURS];
        final double[] avgSupply = new double[HOURS];
        final double[] avgTraffic = new double[HOURS];
        final double[] avgEnv = new double[HOURS];

        Slice() {
            for (int h = 0; h < HOURS; h++) pointsByHour.add(new ArrayList<>());
        }

        void finish() {
            for (int h = 0; h < HOURS; h++) {
                int n = Math.max(counts[h], 1);
                avgScore[h] = sums[h][0] / n;
                avgDemand[h] = sums[h][1] / n;
                avgSupply[h] = sums[h][2] / n;
                avgTraffic[h] = sums[h][3] / n;
                avgEnv[h] = sums[h][4] / n;
            }
        }
    }

    private final Map<Long, Slice> slices = new HashMap<>();
    private final RiskIndexRepository repository;

    public GridRiskCache(RiskIndexRepository repository) {
        this.repository = repository;
    }

    @PostConstruct
    void load() {
        try {
            loadRows(repository.findAllWithCoordinates());
        } catch (Exception e) {
            log.warn("히트맵 캐시 적재 실패 — 빈 캐시로 동작: {}", e.getMessage());
        }
    }

    /** 행 목록으로 캐시 구성 (테스트에서도 직접 호출) */
    void loadRows(List<GridRiskRow> rows) {
        for (GridRiskRow row : rows) {
            Integer hour = row.getHourOfDay();
            if (hour == null || hour < 0 || hour >= HOURS) continue;
            if (row.getLat() == null || row.getLng() == null) continue;

            long district = row.getDistrictId() == null ? 1L : row.getDistrictId();
            Slice slice = slices.computeIfAbsent(district, d -> new Slice());

            double score = nz(row.getRiskScore());
            slice.pointsByHour.get(hour).add(new BasePoint(row.getLat(), row.getLng(), score));
            slice.sums[hour][0] += score;
            slice.sums[hour][1] += nz(row.getDemand());
            slice.sums[hour][2] += nz(row.getSupply());
            slice.sums[hour][3] += nz(row.getTraffic());
            slice.sums[hour][4] += nz(row.getEnv());
            slice.counts[hour]++;
        }

        slices.forEach((district, slice) -> {
            slice.finish();
            int total = 0;
            for (int c : slice.counts) total += c;
            log.info("히트맵 캐시 적재: district {} — {}행 (시간대당 격자 약 {}개)",
                    district, total, total / HOURS);
        });
    }

    private double nz(Double v) {
        return v == null ? 0 : v;
    }

    /** 해당 지역의 위험지수 데이터 보유 여부 (없으면 API는 404) */
    public boolean hasDistrict(long districtId) {
        return slices.containsKey(districtId);
    }

    public List<BasePoint> pointsAt(long districtId, int hour) {
        Slice s = slices.get(districtId);
        return s == null ? List.of() : Collections.unmodifiableList(s.pointsByHour.get(hour));
    }

    public double avgScoreAt(long districtId, int hour) { return avg(districtId, s -> s.avgScore[hour]); }
    public double avgDemandAt(long districtId, int hour) { return avg(districtId, s -> s.avgDemand[hour]); }
    public double avgSupplyAt(long districtId, int hour) { return avg(districtId, s -> s.avgSupply[hour]); }
    public double avgTrafficAt(long districtId, int hour) { return avg(districtId, s -> s.avgTraffic[hour]); }
    public double avgEnvAt(long districtId, int hour) { return avg(districtId, s -> s.avgEnv[hour]); }

    private double avg(long districtId, java.util.function.ToDoubleFunction<Slice> getter) {
        Slice s = slices.get(districtId);
        return s == null ? 0 : getter.applyAsDouble(s);
    }
}

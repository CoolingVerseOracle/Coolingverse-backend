package com.example.demo;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.example.demo.GridRiskDtos.GridRiskPoint;
import com.example.demo.db.RiskIndexRepository;
import com.example.demo.db.RiskIndexRepository.GridRiskRow;

import jakarta.annotation.PostConstruct;

/**
 * 히트맵 데이터 메모리 캐시.
 *
 * risk_index는 분석 배치 산출물(정적)이라, 기동 시 1회만 DB에서 읽어
 * 시간대별로 정리해 둔다. 이후 요청은 DB를 거치지 않아 24시간 스크러버를
 * 드래그해도 즉시 응답한다. (31,344행 ≈ 수 MB, 메모리 부담 없음)
 * 빈 DB(로컬 H2)에서는 빈 캐시로 동작 — 응답은 빈 배열/0이 된다.
 */
@Component
public class GridRiskCache {

    private static final Logger log = LoggerFactory.getLogger(GridRiskCache.class);
    private static final int HOURS = 24;

    /** 시간대별 격자 점 목록 [0..23] */
    private final List<List<GridRiskPoint>> pointsByHour = new ArrayList<>();
    /** 시간대별 평균: 위험지수 / 수요 / 공급 / 혼잡 / 환경 */
    private final double[] avgScore = new double[HOURS];
    private final double[] avgDemand = new double[HOURS];
    private final double[] avgSupply = new double[HOURS];
    private final double[] avgTraffic = new double[HOURS];
    private final double[] avgEnv = new double[HOURS];

    private final RiskIndexRepository repository;

    public GridRiskCache(RiskIndexRepository repository) {
        this.repository = repository;
        for (int h = 0; h < HOURS; h++) pointsByHour.add(new ArrayList<>());
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
        double[][] sums = new double[HOURS][5];
        int[] counts = new int[HOURS];

        for (GridRiskRow row : rows) {
            Integer hour = row.getHourOfDay();
            if (hour == null || hour < 0 || hour >= HOURS) continue;
            if (row.getLat() == null || row.getLng() == null) continue;

            double score = nz(row.getRiskScore());
            pointsByHour.get(hour).add(new GridRiskPoint(row.getLat(), row.getLng(), score));
            sums[hour][0] += score;
            sums[hour][1] += nz(row.getDemand());
            sums[hour][2] += nz(row.getSupply());
            sums[hour][3] += nz(row.getTraffic());
            sums[hour][4] += nz(row.getEnv());
            counts[hour]++;
        }

        for (int h = 0; h < HOURS; h++) {
            int n = Math.max(counts[h], 1);
            avgScore[h] = sums[h][0] / n;
            avgDemand[h] = sums[h][1] / n;
            avgSupply[h] = sums[h][2] / n;
            avgTraffic[h] = sums[h][3] / n;
            avgEnv[h] = sums[h][4] / n;
        }

        int total = rows.size();
        if (total > 0) {
            log.info("히트맵 캐시 적재 완료: {}행 (시간대당 격자 약 {}개)", total, total / HOURS);
        }
    }

    private double nz(Double v) {
        return v == null ? 0 : v;
    }

    public List<GridRiskPoint> pointsAt(int hour) {
        return Collections.unmodifiableList(pointsByHour.get(hour));
    }

    public double avgScoreAt(int hour) { return avgScore[hour]; }
    public double avgDemandAt(int hour) { return avgDemand[hour]; }
    public double avgSupplyAt(int hour) { return avgSupply[hour]; }
    public double avgTrafficAt(int hour) { return avgTraffic[hour]; }
    public double avgEnvAt(int hour) { return avgEnv[hour]; }
}

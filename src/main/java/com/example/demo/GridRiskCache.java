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

/** 활성 위험지수를 region → year → month → hour 단위로 격리해 보관한다. */
@Component
public class GridRiskCache {
    private static final Logger log = LoggerFactory.getLogger(GridRiskCache.class);
    private static final int HOURS = 24;
    public static final int DEFAULT_YEAR = 2025;

    public record BasePoint(double lat, double lng, double score) {}
    private record DatasetKey(String regionCode, int year, int month) {}

    private static final class Dataset {
        final List<List<BasePoint>> points = new ArrayList<>();
        final double[][] averages = new double[5][HOURS];
        Dataset() { for (int h = 0; h < HOURS; h++) points.add(new ArrayList<>()); }
    }

    private final Map<DatasetKey, Dataset> datasets = new HashMap<>();
    private final RiskIndexRepository repository;

    public GridRiskCache(RiskIndexRepository repository) { this.repository = repository; }

    @PostConstruct
    void load() {
        if (repository == null) return;
        try {
            loadRows(repository.findAllActiveWithCoordinates());
        } catch (Exception e) {
            log.warn("지역별 히트맵 캐시 적재 실패 — 빈 캐시로 동작: {}", e.getMessage());
        }
    }

    void loadRows(List<GridRiskRow> rows) {
        datasets.clear();
        Map<DatasetKey, double[][]> sums = new HashMap<>();
        Map<DatasetKey, int[]> counts = new HashMap<>();
        for (GridRiskRow row : rows) {
            int hour = row.getHourOfDay() == null ? -1 : row.getHourOfDay();
            if (hour < 0 || hour >= HOURS || row.getLat() == null || row.getLng() == null) continue;
            String region = row.getRegionCode() == null ? "pangyo" : row.getRegionCode();
            int year = row.getAnalysisYear() == null ? DEFAULT_YEAR : row.getAnalysisYear();
            int month = row.getAnalysisMonth() == null ? 10 : row.getAnalysisMonth();
            DatasetKey key = new DatasetKey(region, year, month);
            Dataset data = datasets.computeIfAbsent(key, ignored -> new Dataset());
            double score = nz(row.getRiskScore());
            data.points.get(hour).add(new BasePoint(row.getLat(), row.getLng(), score));
            double[][] sum = sums.computeIfAbsent(key, ignored -> new double[HOURS][5]);
            int[] count = counts.computeIfAbsent(key, ignored -> new int[HOURS]);
            sum[hour][0] += score;
            sum[hour][1] += nz(row.getDemand());
            sum[hour][2] += nz(row.getSupply());
            sum[hour][3] += nz(row.getTraffic());
            sum[hour][4] += nz(row.getEnv());
            count[hour]++;
        }
        datasets.forEach((key, data) -> {
            double[][] sum = sums.get(key);
            int[] count = counts.get(key);
            for (int h = 0; h < HOURS; h++) {
                int n = Math.max(count[h], 1);
                for (int metric = 0; metric < 5; metric++) data.averages[metric][h] = sum[h][metric] / n;
            }
            int total = count == null ? 0 : java.util.Arrays.stream(count).sum();
            log.info("위험지수 캐시: region={}, year={}, month={}, rows={}, avg={}",
                    key.regionCode, key.year, key.month, total, round2(java.util.Arrays.stream(data.averages[0]).average().orElse(0)));
        });
    }

    private Dataset dataset(String region, int year, int month) {
        return datasets.getOrDefault(new DatasetKey(region, year, month), new Dataset());
    }
    private double nz(Double value) { return value == null ? 0 : value; }
    private double round2(double value) { return Math.round(value * 100) / 100.0; }

    /** 해당 지역·연도·월의 활성 데이터가 적재돼 있는지 (없으면 API가 400으로 실패해야 한다) */
    public boolean hasData(String region, int year, int month) {
        return datasets.containsKey(new DatasetKey(region, year, month));
    }

    public List<BasePoint> pointsAt(String region, int year, int month, int hour) {
        return Collections.unmodifiableList(dataset(region, year, month).points.get(hour));
    }
    public double averageAt(String region, int year, int month, int metric, int hour) {
        return dataset(region, year, month).averages[metric][hour];
    }

    // 기존 단위 테스트와 바이너리 계약을 위한 판교 10월 축약 API.
    public List<BasePoint> pointsAt(int hour) { return pointsAt("pangyo", DEFAULT_YEAR, 10, hour); }
    public double avgScoreAt(int hour) { return averageAt("pangyo", DEFAULT_YEAR, 10, 0, hour); }
    public double avgDemandAt(int hour) { return averageAt("pangyo", DEFAULT_YEAR, 10, 1, hour); }
    public double avgSupplyAt(int hour) { return averageAt("pangyo", DEFAULT_YEAR, 10, 2, hour); }
    public double avgTrafficAt(int hour) { return averageAt("pangyo", DEFAULT_YEAR, 10, 3, hour); }
    public double avgEnvAt(int hour) { return averageAt("pangyo", DEFAULT_YEAR, 10, 4, hour); }
}

package com.example.demo.db;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/** risk_index 테이블 — 격자×시간 위험지수 (1,306 핵심격자 × 24h = 31,344행, 읽기 전용) */
@Entity
@Table(name = "risk_index",
       uniqueConstraints = @UniqueConstraint(columnNames = {
               "pipeline_run_id", "region_code", "grid_id", "analysis_year", "analysis_month", "hour_of_day"}))
public class RiskIndex {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "risk_id")
    private Long riskId;

    @Column(name = "pipeline_run_id", nullable = false, length = 80)
    private String pipelineRunId;

    @Column(name = "region_code", nullable = false, length = 30)
    private String regionCode;

    @Column(name = "grid_id", nullable = false)
    private Long gridId;                 // FK: grids (핵심격자)

    @Column(name = "analysis_year", nullable = false)
    private Integer analysisYear;

    @Column(name = "analysis_month", nullable = false)
    private Integer analysisMonth;

    @Column(name = "hour_of_day", nullable = false)
    private Integer hourOfDay;           // 0~23

    @Column(name = "demand_pressure")
    private Double demandPressure;       // 수요 압력 (가중치 0.25)

    @Column(name = "supply_shortage")
    private Double supplyShortage;       // 공급 부족 (가중치 0.35)

    @Column(name = "traffic_congest")
    private Double trafficCongest;       // 교통 혼잡 (가중치 0.15)

    @Column(name = "env_sensitivity")
    private Double envSensitivity;       // 환경 민감도 (가중치 0.25)

    @Column(name = "risk_score")
    private Double riskScore;            // 가중합×100, baseline 37.81

    @Column(name = "batch_date")
    private LocalDate batchDate;

    protected RiskIndex() {}

    public Long getRiskId() { return riskId; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getRegionCode() { return regionCode; }
    public Long getGridId() { return gridId; }
    public Integer getAnalysisYear() { return analysisYear; }
    public Integer getAnalysisMonth() { return analysisMonth; }
    public Integer getHourOfDay() { return hourOfDay; }
    public Double getDemandPressure() { return demandPressure; }
    public Double getSupplyShortage() { return supplyShortage; }
    public Double getTrafficCongest() { return trafficCongest; }
    public Double getEnvSensitivity() { return envSensitivity; }
    public Double getRiskScore() { return riskScore; }
    public LocalDate getBatchDate() { return batchDate; }
}

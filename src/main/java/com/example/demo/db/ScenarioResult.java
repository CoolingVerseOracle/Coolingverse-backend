package com.example.demo.db;

import java.time.LocalDate;
import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

/**
 * scenario_results 테이블 — 시나리오 실행 결과 이력.
 * 갱신마다 새 줄 INSERT (덮어쓰기 X). 같은 scenario_id + 다른 run_date = 같은 조건, 다른 결과.
 */
@Entity
@Table(name = "scenario_results",
       uniqueConstraints = @UniqueConstraint(columnNames = {"scenario_id", "run_date"}))
public class ScenarioResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "result_id")
    private Long resultId;

    @Column(name = "scenario_id", nullable = false)
    private Long scenarioId;             // FK: scenarios

    @Column(name = "run_date", nullable = false)
    private LocalDate runDate;           // 어느 갱신분인지

    @Column(name = "added_supply")
    private Integer addedSupply;         // 추가 확보 주차면

    @Column(name = "risk_before")
    private Double riskBefore;

    @Column(name = "risk_after")
    private Double riskAfter;

    @Column(name = "carbon_reduction")
    private Double carbonReduction;      // 일일 CO2 저감(kg)

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    protected ScenarioResult() {}

    public ScenarioResult(Long scenarioId, LocalDate runDate, Integer addedSupply,
                          Double riskBefore, Double riskAfter, Double carbonReduction) {
        this.scenarioId = scenarioId;
        this.runDate = runDate;
        this.addedSupply = addedSupply;
        this.riskBefore = riskBefore;
        this.riskAfter = riskAfter;
        this.carbonReduction = carbonReduction;
        this.createdAt = LocalDateTime.now();
    }

    public Long getResultId() { return resultId; }
    public Long getScenarioId() { return scenarioId; }
    public LocalDate getRunDate() { return runDate; }
    public Integer getAddedSupply() { return addedSupply; }
    public Double getRiskBefore() { return riskBefore; }
    public Double getRiskAfter() { return riskAfter; }
    public Double getCarbonReduction() { return carbonReduction; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}

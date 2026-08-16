package com.example.demo.db;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** enforcement 테이블 — 불법주정차 단속 이력 (평일만 161,231건, 읽기 전용) */
@Entity
@Table(name = "enforcement")
public class Enforcement {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "enforcement_id")
    private Long enforcementId;

    @Column(name = "pipeline_run_id", nullable = false, length = 80)
    private String pipelineRunId;

    @Column(name = "region_code", nullable = false, length = 30)
    private String regionCode;

    @Column(name = "grid_id")
    private Long gridId;                 // FK: grids (지오코딩 실패 471건은 NULL)

    @Column(name = "place_text", length = 500)
    private String placeText;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "enforced_at")
    private LocalDate enforcedAt;        // 단속 일자 (2025, 시각 없음)

    @Column(name = "day_of_week", length = 10)
    private String dayOfWeek;            // 요일 한글

    @Column(name = "geocode_status", length = 20)
    private String geocodeStatus;        // 성공/실패

    protected Enforcement() {}

    public Long getEnforcementId() { return enforcementId; }
    public String getPipelineRunId() { return pipelineRunId; }
    public String getRegionCode() { return regionCode; }
    public Long getGridId() { return gridId; }
    public String getPlaceText() { return placeText; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public LocalDate getEnforcedAt() { return enforcedAt; }
    public String getDayOfWeek() { return dayOfWeek; }
    public String getGeocodeStatus() { return geocodeStatus; }
}

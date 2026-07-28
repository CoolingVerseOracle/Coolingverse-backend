package com.example.demo.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** grids 테이블 — 100m 격자 마스터 (분당구 122,318행, 읽기 전용) */
@Entity
@Table(name = "grids")
public class Grid {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "grid_id")
    private Long gridId;

    @Column(name = "district_id", nullable = false)
    private Long districtId;             // FK: districts

    @Column(name = "grid_code", length = 50)
    private String gridCode;             // 국가지점번호 (예: 다사515191)

    @Column(name = "min_lat")
    private Double minLat;

    @Column(name = "min_lng")
    private Double minLng;

    @Column(name = "max_lat")
    private Double maxLat;

    @Column(name = "max_lng")
    private Double maxLng;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @Column(name = "area_km2")
    private Double areaKm2;              // 0.01㎢ (100m×100m)

    @Column(name = "effective_area_km2")
    private Double effectiveAreaKm2;     // 녹지 제외 실질면적

    protected Grid() {}

    public Long getGridId() { return gridId; }
    public Long getDistrictId() { return districtId; }
    public String getGridCode() { return gridCode; }
    public Double getMinLat() { return minLat; }
    public Double getMinLng() { return minLng; }
    public Double getMaxLat() { return maxLat; }
    public Double getMaxLng() { return maxLng; }
    public Double getCenterLat() { return centerLat; }
    public Double getCenterLng() { return centerLng; }
    public Double getAreaKm2() { return areaKm2; }
    public Double getEffectiveAreaKm2() { return effectiveAreaKm2; }
}

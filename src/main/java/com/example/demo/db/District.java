package com.example.demo.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** districts 테이블 — 분석 대상 지역 (분당구 1행, 수동 INSERT) */
@Entity
@Table(name = "districts")
public class District {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "district_id")
    private Long districtId;

    @Column(name = "name", nullable = false, length = 100)
    private String name;                 // 지역명 (예: 성남시 분당구)

    @Column(name = "sido", length = 50)
    private String sido;

    @Column(name = "sigungu", length = 50)
    private String sigungu;

    @Column(name = "center_lat")
    private Double centerLat;

    @Column(name = "center_lng")
    private Double centerLng;

    @Column(name = "vehicle_count")
    private Long vehicleCount;

    @Column(name = "is_base", length = 1)
    private String isBase;               // Y=기준지역(분당구)

    protected District() {}              // JPA 기본 생성자

    public Long getDistrictId() { return districtId; }
    public String getName() { return name; }
    public String getSido() { return sido; }
    public String getSigungu() { return sigungu; }
    public Double getCenterLat() { return centerLat; }
    public Double getCenterLng() { return centerLng; }
    public Long getVehicleCount() { return vehicleCount; }
    public String getIsBase() { return isBase; }
}

package com.example.demo.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** apartments 테이블 — 유휴 주차 공급원 (210단지, 읽기 전용) */
@Entity
@Table(name = "apartments")
public class Apartment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "apartment_id")
    private Long apartmentId;

    @Column(name = "grid_id")
    private Long gridId;                 // FK: grids (NULL 허용)

    @Column(name = "kapt_code", length = 50)
    private String kaptCode;

    @Column(name = "name", nullable = false, length = 150)
    private String name;

    @Column(name = "address", length = 300)
    private String address;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "total_parking")
    private Integer totalParking;

    @Column(name = "is_open", length = 1)
    private String isOpen;               // Y=17단지 / N=193단지 (결측 없음)

    @Column(name = "open_count")
    private Integer openCount;           // 개방 시 잠재 유휴면수 (총면수×29.1%)

    @Column(name = "source", length = 50)
    private String source;

    protected Apartment() {}

    public Long getApartmentId() { return apartmentId; }
    public Long getGridId() { return gridId; }
    public String getKaptCode() { return kaptCode; }
    public String getName() { return name; }
    public String getAddress() { return address; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public Integer getTotalParking() { return totalParking; }
    public String getIsOpen() { return isOpen; }
    public Integer getOpenCount() { return openCount; }
    public String getSource() { return source; }
}

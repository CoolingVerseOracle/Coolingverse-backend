package com.example.demo.db;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** air_quality 테이블 — 대기질 측정 (2025 연간 시간별, 측정소 4곳, 23,424건, 읽기 전용) */
@Entity
@Table(name = "air_quality")
public class AirQuality {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "air_id")
    private Long airId;

    @Column(name = "grid_id")
    private Long gridId;                 // FK: grids

    @Column(name = "station_name", length = 100)
    private String stationName;

    @Column(name = "lat")
    private Double lat;

    @Column(name = "lng")
    private Double lng;

    @Column(name = "measured_at")
    private LocalDateTime measuredAt;

    @Column(name = "month")
    private Integer month;               // 월 1~12

    @Column(name = "day_of_week", length = 10)
    private String dayOfWeek;            // 요일 한글 한 글자

    @Column(name = "hour")
    private Integer hour;                // 시 0~23

    @Column(name = "no2")
    private Double no2;

    @Column(name = "co")
    private Double co;

    protected AirQuality() {}

    public Long getAirId() { return airId; }
    public Long getGridId() { return gridId; }
    public String getStationName() { return stationName; }
    public Double getLat() { return lat; }
    public Double getLng() { return lng; }
    public LocalDateTime getMeasuredAt() { return measuredAt; }
    public Integer getMonth() { return month; }
    public String getDayOfWeek() { return dayOfWeek; }
    public Integer getHour() { return hour; }
    public Double getNo2() { return no2; }
    public Double getCo() { return co; }
}

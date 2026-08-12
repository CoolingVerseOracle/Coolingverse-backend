package com.example.demo.db;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * scenarios 테이블 — 정책 시나리오 조건 (유일한 CRUD 대상).
 * 컬럼은 프론트 SimulationSettings 필드와 1:1 (2026-07-28 프론트 정렬).
 */
@Entity
@Table(name = "scenarios")
public class Scenario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "scenario_id")
    private Long scenarioId;

    @Column(name = "name", nullable = false, length = 200)
    private String name;

    @Column(name = "memo", length = 1000)
    private String memo;

    @Column(name = "district_id", nullable = false)
    private Long districtId;             // FK: districts

    @Column(name = "open_to_public", length = 1)
    private String openToPublic;         // 외부인 개방 아파트 포함 Y/N

    @Column(name = "residents_only", length = 1)
    private String residentsOnly;        // 입주민 전용 포함 Y/N

    @Column(name = "participation")
    private Integer participation;       // 참여율 0~100

    @Column(name = "operation_start", length = 5)
    private String operationStart;       // "HH:mm"

    @Column(name = "operation_end", length = 5)
    private String operationEnd;         // "HH:mm"

    @Column(name = "commercial_radius_m")
    private Integer commercialRadiusM;   // 상업시설 반경(m), 기본 500

    @Column(name = "analysis_month", nullable = false)
    private Integer analysisMonth;

    @Column(name = "parking_fee")
    private Integer parkingFee;          // NULL 허용 (프론트 미전송)

    @Column(name = "created_by", length = 50)
    private String createdBy = "admin";

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    protected Scenario() {}

    public Scenario(String name, String memo, Long districtId, String openToPublic,
                    String residentsOnly, Integer participation, String operationStart,
                    String operationEnd, Integer commercialRadiusM, Integer analysisMonth) {
        this.name = name;
        this.memo = memo;
        this.districtId = districtId;
        this.openToPublic = openToPublic;
        this.residentsOnly = residentsOnly;
        this.participation = participation;
        this.operationStart = operationStart;
        this.operationEnd = operationEnd;
        this.commercialRadiusM = commercialRadiusM;
        this.analysisMonth = analysisMonth == null ? 10 : analysisMonth;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    /**
     * 메타데이터(이름·메모)만 부분 수정. null이면 해당 항목은 건드리지 않는다.
     * 설정값과 결과 스냅샷은 불변이므로 재계산도 일어나지 않는다.
     */
    public void updateMetadata(String newName, String newMemo) {
        if (newName != null) this.name = newName;
        if (newMemo != null) this.memo = newMemo;
        this.updatedAt = LocalDateTime.now();
    }

    public Long getScenarioId() { return scenarioId; }
    public String getName() { return name; }
    public String getMemo() { return memo; }
    public Long getDistrictId() { return districtId; }
    public String getOpenToPublic() { return openToPublic; }
    public String getResidentsOnly() { return residentsOnly; }
    public Integer getParticipation() { return participation; }
    public String getOperationStart() { return operationStart; }
    public String getOperationEnd() { return operationEnd; }
    public Integer getCommercialRadiusM() { return commercialRadiusM; }
    public Integer getAnalysisMonth() { return analysisMonth; }
    public Integer getParkingFee() { return parkingFee; }
    public String getCreatedBy() { return createdBy; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
}

package com.example.demo.db;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

/** 활성 포인터 매핑. 앱은 읽기만 하고 데이터 파이프라인이 원자적으로 갱신한다. */
@Entity
@IdClass(ActiveDatasetVersionId.class)
@Table(name = "active_dataset_versions")
public class ActiveDatasetVersion {
    @Id
    @Column(name = "region_code", length = 30)
    private String regionCode;
    @Id
    @Column(name = "analysis_year")
    private Integer analysisYear;
    @Column(name = "active_run_id", nullable = false, length = 80)
    private String activeRunId;
    @Column(name = "previous_run_id", length = 80)
    private String previousRunId;

    protected ActiveDatasetVersion() {}
}

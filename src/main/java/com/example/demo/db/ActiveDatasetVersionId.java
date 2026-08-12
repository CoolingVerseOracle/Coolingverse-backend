package com.example.demo.db;

import java.io.Serializable;
import java.util.Objects;

public class ActiveDatasetVersionId implements Serializable {
    private String regionCode;
    private Integer analysisYear;

    public ActiveDatasetVersionId() {}
    public ActiveDatasetVersionId(String regionCode, Integer analysisYear) {
        this.regionCode = regionCode;
        this.analysisYear = analysisYear;
    }
    @Override public boolean equals(Object value) {
        if (this == value) return true;
        if (!(value instanceof ActiveDatasetVersionId other)) return false;
        return Objects.equals(regionCode, other.regionCode) && Objects.equals(analysisYear, other.analysisYear);
    }
    @Override public int hashCode() { return Objects.hash(regionCode, analysisYear); }
}

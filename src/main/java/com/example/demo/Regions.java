package com.example.demo;

/**
 * 분석 지역 카탈로그 — 코드("pangyo") ↔ districts FK ↔ 화면 표시명 변환.
 *
 * 표시명은 팀 결정(이슈 #22)에 따라 행정구가 아닌 "분석 지역명" 기준
 * (프론트 지역 셀렉터 라벨과 일치). 지역이 늘면 여기에 한 줄 추가.
 */
public enum Regions {

    PANGYO("pangyo", 1L, "판교테크노밸리"),
    INGYE("ingye", 2L, "수원 인계동"),
    BUCHEON("bucheon", 3L, "부천시");

    private final String code;
    private final long districtId;
    private final String displayName;

    Regions(String code, long districtId, String displayName) {
        this.code = code;
        this.districtId = districtId;
        this.displayName = displayName;
    }

    public String code() { return code; }
    public long districtId() { return districtId; }
    public String displayName() { return displayName; }

    /** 코드 → 지역 (정확히 일치할 때만). 미지의 코드는 null — grid-risk 404 판정용 */
    public static Regions find(String code) {
        for (Regions r : values()) {
            if (r.code.equals(code)) return r;
        }
        return null;
    }

    /** 코드 → 지역. null/미지의 값은 기본 지역(판교) 취급 (이슈 #22 계약) */
    public static Regions fromCode(String code) {
        for (Regions r : values()) {
            if (r.code.equals(code)) return r;
        }
        return PANGYO;
    }

    /** districts FK → 지역. 미지의 번호는 판교 취급 (기존 시드 호환) */
    public static Regions fromDistrictId(Long districtId) {
        if (districtId != null) {
            for (Regions r : values()) {
                if (r.districtId == districtId) return r;
            }
        }
        return PANGYO;
    }

    /** 필터 매칭: 코드 우선, 표시명도 허용 (구버전 프론트 호환) */
    public boolean matchesFilter(String filter) {
        return code.equals(filter) || displayName.equals(filter);
    }
}
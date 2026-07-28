package com.example.demo;

import java.util.List;

import com.example.demo.SimulationDtos.SimulationSettings;

/**
 * 시나리오 API의 요청/응답 모양 모음.
 *
 * 목록 행(ScenarioRow)과 페이지 묶음(Paginated)은 프론트 타입
 * (src/types/scenario.ts, src/types/common.ts)과 1:1로 맞춘 구조다.
 */
public class ScenarioDtos {

    /** 시나리오 관리 테이블 1행 (프론트 Scenario 타입과 동일) */
    public record ScenarioRow(
            String id,
            String name,
            String region,          // 대상 지역 (예: 성남시 분당구)
            String conditions,      // 주요 조건 요약 (예: 30%, 08~19시)
            int supplyDelta,        // 공급 증감(면)
            double riskBefore,
            double riskAfter,
            String updatedAt        // "YYYY.MM.DD"
    ) {}

    /** 페이지 묶음 (프론트 Paginated<T> 와 동일) */
    public record Paginated<T>(
            List<T> items,
            int total,
            int page,
            int pageSize
    ) {}

    /** 저장 요청: 이름 + 메모(선택) + 현재 슬라이더 설정 */
    public record CreateScenarioRequest(
            String name,
            String memo,
            SimulationSettings settings
    ) {}

    /** 상세 조회 응답: 조건(설정)과 결과 스냅샷을 모두 담는다 — "열기" 버튼용 */
    public record ScenarioDetail(
            String id,
            String name,
            String memo,
            String region,
            SimulationSettings settings,
            int addedSupply,        // 결과: 추가 확보 주차면
            double riskBefore,      // 결과: 적용 전 위험지수
            double riskAfter,       // 결과: 적용 후 위험지수
            double carbonReduction, // 결과: 일일 CO2 저감량(kg)
            String createdAt,
            String updatedAt
    ) {}
}

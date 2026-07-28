package com.example.demo.db;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * scenarios 조회/저장 창구.
 * JpaRepository를 상속하면 findAll, findById, save, deleteById 등이 자동 제공된다.
 * 메서드 이름 규칙으로 검색 쿼리도 자동 생성된다 (아래 참고).
 */
public interface ScenarioRepository extends JpaRepository<Scenario, Long> {

    /** 이름 부분 검색 (대소문자 무시) — GET /scenarios?keyword= 대응 */
    List<Scenario> findByNameContainingIgnoreCase(String keyword);
}

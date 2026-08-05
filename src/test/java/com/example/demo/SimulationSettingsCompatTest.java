package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import com.example.demo.SimulationDtos.SimulationSettings;
import tools.jackson.databind.ObjectMapper;   // Spring Boot 4 = Jackson 3 (패키지명 변경됨)

/**
 * 이슈 #19 요청 3 검증: 프론트가 추가로 보내는 region/month 필드가
 * 역직렬화를 깨뜨리지 않는지 확인. (스프링 부트 기본 Jackson 설정은
 * 모르는 필드를 무시하도록 되어 있어야 한다)
 */
@SpringBootTest
class SimulationSettingsCompatTest {

    @Autowired
    private ObjectMapper objectMapper;   // 스프링이 실제로 쓰는 그 변환기

    @Test
    @DisplayName("이슈 #22: region·month가 정식 필드로 수신된다 (버려지지 않음)")
    void receivesRegionAndMonth() throws Exception {
        String json = """
                {"openToPublic":true,"residentsOnly":true,"participationRate":45,
                 "openFrom":"09:00","openTo":"18:00","commercialRadiusM":500,
                 "region":"ingye","month":10}
                """;

        SimulationSettings settings = objectMapper.readValue(json, SimulationSettings.class);

        assertTrue(settings.openToPublic());
        assertEquals(45, settings.participationRate());
        assertEquals("ingye", settings.region());
        assertEquals(10, settings.month());
    }

    @Test
    @DisplayName("구버전 프론트: region·month 없이 보내도 역직렬화 성공 (null 허용)")
    void toleratesMissingRegionAndMonth() throws Exception {
        String json = """
                {"openToPublic":true,"residentsOnly":true,"participationRate":30,
                 "openFrom":"08:00","openTo":"19:00","commercialRadiusM":500}
                """;

        SimulationSettings settings = objectMapper.readValue(json, SimulationSettings.class);

        assertEquals(30, settings.participationRate());
        assertEquals(null, settings.region());   // 저장 시 Regions.fromCode가 판교로 해석
    }
}

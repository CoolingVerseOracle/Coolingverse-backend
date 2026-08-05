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
    @DisplayName("region·month 등 미지의 필드가 있어도 SimulationSettings 역직렬화가 성공한다")
    void ignoresUnknownFields() throws Exception {
        String json = """
                {"openToPublic":true,"residentsOnly":true,"participationRate":45,
                 "openFrom":"09:00","openTo":"18:00","commercialRadiusM":500,
                 "region":"pangyo","month":10}
                """;

        SimulationSettings settings = objectMapper.readValue(json, SimulationSettings.class);

        assertTrue(settings.openToPublic());
        assertEquals(45, settings.participationRate());
        assertEquals("09:00", settings.openFrom());
    }
}

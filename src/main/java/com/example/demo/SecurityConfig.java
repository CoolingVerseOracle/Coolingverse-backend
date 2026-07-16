package com.example.demo;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 접근 규칙(문지기 규칙) 설정.
 *
 * 이 서비스는 지자체 전용(B2G)이라, 대시보드 조회를 포함한 '모든 요청'에
 * 로그인을 요구한다. 로그인하지 않은 사람은 아무것도 볼 수 없다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // 접근 규칙: 모든 요청은 로그인해야 통과 (조회/쓰기 모두 보호)
            .authorizeHttpRequests(auth -> auth
                .anyRequest().authenticated()
            )
            // 로그인 방식: 지금은 스프링 기본 로그인 폼 사용 (프론트 방식 확정 후 조정 예정)
            .formLogin(Customizer.withDefaults())
            .logout(Customizer.withDefaults());

        // ⚠️ CORS/CSRF는 프론트(SPA vs 같은출처) 방식이 확정된 뒤에 설정한다.
        //    지금은 스프링 기본값(CSRF 켜짐)을 그대로 둔다.

        return http.build();
    }
}

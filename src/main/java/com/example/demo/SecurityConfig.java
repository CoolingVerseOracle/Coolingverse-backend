package com.example.demo;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * 접근 규칙(문지기 규칙) 설정 — 정적 토큰 방식 (2026-07-28 프론트 기준 정렬).
 *
 * 이 서비스는 지자체 전용(B2G)이라 '모든 요청'에 인증을 요구한다.
 * 단 /login 만 예외로 열어둔다(입장권을 받는 창구이므로).
 *
 * 세션/쿠키를 쓰지 않는 무상태(stateless) 구조:
 *   로그인 성공 → 토큰 발급 → 프론트가 매 요청 Authorization 헤더로 제시
 *   → TokenAuthFilter가 대조. 쿠키가 없으므로 CSRF 보호는 불필요해서 끈다.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final TokenAuthFilter tokenAuthFilter;

    public SecurityConfig(TokenAuthFilter tokenAuthFilter) {
        this.tokenAuthFilter = tokenAuthFilter;
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // CORS: 아래 corsConfigurationSource() 규칙 사용 (Vue 개발서버 5173 허용)
            .cors(Customizer.withDefaults())
            // 쿠키 기반이 아니므로 CSRF 보호 불필요 (토큰은 헤더로만 전달됨)
            .csrf(csrf -> csrf.disable())
            // 세션을 만들지 않는다 — 인증 상태는 오직 토큰으로만 판단
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            // 접근 규칙: /login 만 공개, 나머지는 전부 인증 필요
            // /error 는 스프링의 내부 오류 처리 경로 — 막으면 "없는 주소 + 유효 토큰" 요청이
            // 404 대신 401로 응답돼 프론트의 전역 세션 만료 처리를 오작동시킨다 (이슈 #19)
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/login", "/error").permitAll()
                .anyRequest().authenticated()
            )
            // 인증 없이 접근하면 401 Unauthorized 로 응답 (기본 403 대신 의미에 맞게)
            .exceptionHandling(ex -> ex.authenticationEntryPoint(
                (request, response, e) -> response.sendError(401)
            ))
            // 매 요청마다 토큰을 검사하는 문지기를 표준 인증 필터 앞에 배치
            .addFilterBefore(tokenAuthFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    /**
     * CORS 규칙: Vue 개발서버(http://localhost:5173)의 요청을 허용한다.
     * 쿠키를 안 쓰는 토큰 방식이라 allowCredentials 는 켤 필요 없다.
     * 배포 시 프론트의 실제 주소를 이 목록에 추가하면 된다.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(List.of("http://localhost:5173"));
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("Content-Type", "Authorization"));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }
}

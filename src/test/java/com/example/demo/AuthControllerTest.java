package com.example.demo;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

import com.example.demo.AuthController.LoginRequest;

/**
 * 로그인 API 검증 테스트 — 성공/실패/필드 누락 응답이 계약대로 나오는지 확인.
 * 어떤 입력에도 500이 아니라 {success, ...} 본문으로 응답해야 한다.
 */
class AuthControllerTest {

    private AuthController controller;

    @BeforeEach
    void setUp() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        var users = new InMemoryUserDetailsManager(
                User.withUsername("admin").password(encoder.encode("secret")).roles("ADMIN").build());
        controller = new AuthController(users, encoder, new TokenStore());
    }

    @Test
    @DisplayName("올바른 계정 → success=true + 토큰 발급")
    void validLoginReturnsToken() {
        Map<String, Object> res = controller.login(new LoginRequest("admin", "secret"));

        assertEquals(true, res.get("success"));
        assertNotNull(res.get("token"));
    }

    @Test
    @DisplayName("틀린 비밀번호 → success=false (토큰 없음)")
    void wrongPasswordFails() {
        Map<String, Object> res = controller.login(new LoginRequest("admin", "wrong"));

        assertEquals(false, res.get("success"));
        assertEquals(null, res.get("token"));
    }

    @Test
    @DisplayName("없는 아이디 → success=false")
    void unknownUserFails() {
        Map<String, Object> res = controller.login(new LoginRequest("nobody", "secret"));

        assertEquals(false, res.get("success"));
    }

    @Test
    @DisplayName("필드 누락/공백 → 예외 없이 success=false 안내 (500 방지)")
    void missingFieldsHandledGracefully() {
        assertEquals(false, controller.login(new LoginRequest(null, "secret")).get("success"));
        assertEquals(false, controller.login(new LoginRequest("admin", null)).get("success"));
        assertEquals(false, controller.login(new LoginRequest("  ", "  ")).get("success"));
        assertEquals(false, controller.login(null).get("success"));
    }
}

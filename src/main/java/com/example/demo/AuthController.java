package com.example.demo;

import java.util.Map;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 프론트(Vue)가 호출하는 로그인 API.
 *
 * 프론트 계약(src/api/auth.ts 기준):
 *   요청  POST /login  {"username": "...", "password": "..."}
 *   응답  성공 {"success": true, "token": "..."}
 *         실패 {"success": false, "message": "..."}  (HTTP 200으로 보냄 —
 *              프론트 http()가 4xx면 예외를 던지므로, 실패 사유는 본문으로 전달)
 */
@RestController
public class AuthController {

    private final UserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final TokenStore tokenStore;

    public AuthController(UserDetailsService userDetailsService,
                          PasswordEncoder passwordEncoder,
                          TokenStore tokenStore) {
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.tokenStore = tokenStore;
    }

    // 프론트가 보내는 JSON 본문 모양
    record LoginRequest(String username, String password) {}

    @PostMapping("/login")
    public Map<String, Object> login(@RequestBody LoginRequest req) {
        try {
            // 환경변수로 등록된 관리자 계정을 꺼내서
            UserDetails admin = userDetailsService.loadUserByUsername(req.username());
            // 입력한 비밀번호를 BCrypt로 대조
            if (passwordEncoder.matches(req.password(), admin.getPassword())) {
                return Map.of("success", true, "token", tokenStore.getToken());
            }
        } catch (UsernameNotFoundException ignored) {
            // 아이디가 틀린 경우 — 아래 공통 실패 응답으로 (아이디/비번 중 뭐가 틀렸는지 숨김)
        }
        return Map.of("success", false, "message", "계정 또는 비밀번호를 확인해 주세요.");
    }
}

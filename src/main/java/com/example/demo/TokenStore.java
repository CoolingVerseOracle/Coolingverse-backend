package com.example.demo;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

/**
 * 정적 토큰 금고.
 *
 * 서버가 시작될 때 무작위 토큰(입장권) 1개를 만들어 메모리에 보관한다.
 * 로그인에 성공한 프론트에게 이 토큰을 주고, 이후 모든 요청에서
 * 같은 토큰인지 대조한다. 서버를 재시작하면 새 토큰으로 바뀐다
 * (= 기존 로그인은 전부 무효가 되므로 다시 로그인해야 함).
 */
@Component
public class TokenStore {

    private final String token;

    public TokenStore() {
        // 32바이트 난수를 URL-안전한 문자열로 변환 (예측 불가능한 입장권)
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        this.token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    public String getToken() {
        return token;
    }

    public boolean matches(String candidate) {
        return token.equals(candidate);
    }
}

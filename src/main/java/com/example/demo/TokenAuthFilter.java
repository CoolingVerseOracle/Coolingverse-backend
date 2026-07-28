package com.example.demo;

import java.io.IOException;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 토큰 검사 문지기.
 *
 * 매 요청마다 Authorization 헤더의 "Bearer <토큰>"을 꺼내
 * TokenStore의 입장권과 같은지 대조한다. 맞으면 "관리자 인증됨" 도장을 찍고,
 * 틀리거나 없으면 도장 없이 통과시킨다(그러면 SecurityConfig의 규칙에 걸려
 * 401로 거절된다. /login은 예외적으로 열려 있음).
 */
@Component
public class TokenAuthFilter extends OncePerRequestFilter {

    private final TokenStore tokenStore;
    private final String adminId;

    public TokenAuthFilter(TokenStore tokenStore, @Value("${admin.id}") String adminId) {
        this.tokenStore = tokenStore;
        this.adminId = adminId;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");

        if (header != null && header.startsWith("Bearer ")) {
            String candidate = header.substring("Bearer ".length());
            if (tokenStore.matches(candidate)) {
                // 인증 성공 도장: 이후 컨트롤러에서 Authentication으로 꺼내 쓸 수 있다
                var authentication = new UsernamePasswordAuthenticationToken(
                        adminId, null, List.of(new SimpleGrantedAuthority("ROLE_ADMIN")));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            }
        }

        filterChain.doFilter(request, response);
    }
}

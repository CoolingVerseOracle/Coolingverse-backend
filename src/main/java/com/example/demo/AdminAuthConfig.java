package com.example.demo;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * 관리자 로그인 설정.
 * DB를 쓰지 않고, 환경변수로 받은 관리자 1명을 메모리에 등록한다.
 */
@Configuration
public class AdminAuthConfig {

    // 앱이 시작될 때, 환경변수의 아이디와 비밀번호(해시)로 관리자 1명을 메모리에 만든다.
    @Bean
    public UserDetailsService userDetailsService(
            @Value("${admin.id}") String adminId,
            @Value("${admin.password-hash}") String adminPasswordHash) {

        UserDetails admin = User.withUsername(adminId)
                .password(adminPasswordHash)   // 이미 BCrypt로 해시된 값을 그대로 저장
                .roles("ADMIN")
                .build();

        return new InMemoryUserDetailsManager(admin);
    }

    // 로그인할 때 입력한 비밀번호를 BCrypt 방식으로 비교하는 도구.
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}

package com.example.demo;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

/**
 * 운영용 비밀번호의 BCrypt 해시를 만드는 도구 (배포 준비용, 웹과 무관).
 *
 * 사용법:  ./gradlew bcrypt -Ppw='새비밀번호'
 * 출력된 해시를 서버 환경변수 ADMIN_PASSWORD_HASH 에 등록한다.
 * 원문 비밀번호는 어디에도 저장하지 않는다.
 */
public class PasswordHashTool {

    public static void main(String[] args) {
        if (args.length != 1 || args[0].isBlank()) {
            System.out.println("사용법: ./gradlew bcrypt -Ppw='새비밀번호'");
            return;
        }
        System.out.println();
        System.out.println("ADMIN_PASSWORD_HASH=" + new BCryptPasswordEncoder().encode(args[0]));
        System.out.println();
    }
}

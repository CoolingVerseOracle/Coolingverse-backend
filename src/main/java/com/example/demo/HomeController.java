package com.example.demo;

import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// 로그인에 성공하면 보이는 간단한 확인 페이지.
@RestController
public class HomeController {

    @GetMapping("/")
    public String home(Authentication authentication) {
        return "로그인 성공! 안녕하세요, " + authentication.getName() + " 님";
    }
}

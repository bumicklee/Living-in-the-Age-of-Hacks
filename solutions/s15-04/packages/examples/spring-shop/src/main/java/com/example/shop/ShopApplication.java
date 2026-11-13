package com.example.shop;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/** 다온 가게: 15-4에서 secure-kit을 붙여 볼 「내 회사 서비스」 */
@SpringBootApplication
@EnableAspectJAutoProxy
public class ShopApplication {
    public static void main(String[] args) {
        SpringApplication.run(ShopApplication.class, args);
    }
}

package com.changnian;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@MapperScan("com.changnian.domain.mapper")
public class ChangnianApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChangnianApplication.class, args);
    }
}

package com.campusgym.pro;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("com.campusgym.pro.mapper")
@EnableScheduling
public class CampusGymProApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusGymProApplication.class, args);
    }
}
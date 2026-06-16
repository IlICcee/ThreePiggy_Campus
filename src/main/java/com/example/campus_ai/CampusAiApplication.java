package com.example.campus_ai;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@SpringBootApplication
@ComponentScan("com.example")
public class CampusAiApplication {

    public static void main(String[] args) {
        SpringApplication.run(CampusAiApplication.class, args);
    }

}

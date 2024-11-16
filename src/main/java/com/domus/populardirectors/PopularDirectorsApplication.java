package com.domus.populardirectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;

@Slf4j
@SpringBootApplication
@ComponentScan("com.domus")

public class PopularDirectorsApplication {

    public static void main(String[] args) {
        SpringApplication.run(PopularDirectorsApplication.class, args);
    }

}

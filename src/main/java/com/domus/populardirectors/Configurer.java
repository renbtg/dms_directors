package com.domus.populardirectors;

import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Component;

@Component
public class Configurer {
    @Bean
    public DataInfo getDataInfo() {
        return new DataInfo();
    }
}

package com.testryn;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class TestrynApplication {

    public static void main(String[] args) {
        SpringApplication.run(TestrynApplication.class, args);
    }
}

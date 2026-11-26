package com.pathpilot;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

@SpringBootApplication
@ConfigurationPropertiesScan
public class PathPilotApplication {

    public static void main(String[] args) {
        SpringApplication.run(PathPilotApplication.class, args);
    }
}

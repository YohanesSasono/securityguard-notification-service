package com.securityguard.notification;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class SecurityguardNotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(SecurityguardNotificationServiceApplication.class, args);
    }

}

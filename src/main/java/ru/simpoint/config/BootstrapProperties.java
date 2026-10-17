package ru.simpoint.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Первый администратор: создаётся при запуске, если задан ADMIN_PASSWORD. */
@ConfigurationProperties(prefix = "app.bootstrap")
public record BootstrapProperties(String adminUsername, String adminPassword) {

    public BootstrapProperties {
        if (adminUsername == null || adminUsername.isBlank()) {
            adminUsername = "admin";
        }
    }
}

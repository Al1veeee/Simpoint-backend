package ru.simpoint.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.telegram")
public record TelegramProperties(boolean enabled, String token, int pollTimeoutSeconds) {
}

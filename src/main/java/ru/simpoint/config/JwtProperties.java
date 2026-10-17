package ru.simpoint.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Секрет не имеет значения по умолчанию: без JWT_SECRET приложение не запустится. */
@Validated
@ConfigurationProperties(prefix = "app.jwt")
public record JwtProperties(
        @NotBlank(message = "задайте переменную окружения JWT_SECRET")
        @Size(min = 32, message = "JWT_SECRET должен быть не короче 32 символов")
        String secret,
        @Positive long ttlMinutes) {
}

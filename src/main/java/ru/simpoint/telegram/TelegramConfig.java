package ru.simpoint.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class TelegramConfig {

    @Bean
    public TelegramApi telegramApi(TelegramProperties properties) {
        if (properties.token() == null || properties.token().isBlank()) {
            throw new IllegalStateException("app.telegram.enabled=true, но TELEGRAM_BOT_TOKEN не задан");
        }
        return new TelegramApi(properties.token());
    }
}

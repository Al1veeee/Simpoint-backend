package ru.simpoint.telegram;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Сессии хранятся в памяти: после перезапуска незавершённый черновик чека теряется, продажи остаются в БД. */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
public class BotSessions {

    private final Map<Long, ChatSession> sessions = new ConcurrentHashMap<>();

    public ChatSession get(long chatId) {
        return sessions.computeIfAbsent(chatId, id -> new ChatSession());
    }

    public void remove(long chatId) {
        sessions.remove(chatId);
    }
}

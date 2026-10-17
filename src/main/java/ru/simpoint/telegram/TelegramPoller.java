package ru.simpoint.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;
import ru.simpoint.telegram.TelegramModels.Update;

/**
 * Long polling в отдельном потоке: не нужен публичный HTTPS-адрес и вебхук,
 * бот работает на любом сервере. События обрабатываются по очереди.
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class TelegramPoller implements SmartLifecycle {

    private static final int DEFAULT_TIMEOUT_SECONDS = 25;
    private static final long RETRY_DELAY_MS = 5_000;

    private final TelegramApi api;
    private final BotRouter router;
    private final TelegramProperties properties;

    private volatile boolean running;
    private Thread worker;
    private long offset;

    @Override
    public void start() {
        api.setCommands(BotRouter.COMMANDS);
        running = true;
        worker = Thread.ofPlatform().name("telegram-poller").daemon(true).start(this::loop);
        log.info("Telegram-бот запущен");
    }

    @Override
    public void stop() {
        running = false;
        if (worker != null) {
            worker.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void loop() {
        int timeout = properties.pollTimeoutSeconds() > 0 ? properties.pollTimeoutSeconds() : DEFAULT_TIMEOUT_SECONDS;
        while (running) {
            try {
                for (Update update : api.getUpdates(offset, timeout)) {
                    offset = update.updateId() + 1;
                    router.handle(update);
                }
            } catch (Exception e) {
                if (!running) {
                    return;
                }
                log.warn("Ошибка опроса Telegram ({}), повтор через {} с", e.getClass().getSimpleName(),
                        RETRY_DELAY_MS / 1000);
                try {
                    Thread.sleep(RETRY_DELAY_MS);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }
}

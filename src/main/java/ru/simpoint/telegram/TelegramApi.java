package ru.simpoint.telegram;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import ru.simpoint.telegram.TelegramModels.AnswerCallbackQuery;
import ru.simpoint.telegram.TelegramModels.ApiResponse;
import ru.simpoint.telegram.TelegramModels.BotCommand;
import ru.simpoint.telegram.TelegramModels.DeleteMessage;
import ru.simpoint.telegram.TelegramModels.EditMessageText;
import ru.simpoint.telegram.TelegramModels.GetUpdates;
import ru.simpoint.telegram.TelegramModels.InlineKeyboardMarkup;
import ru.simpoint.telegram.TelegramModels.SendMessage;
import ru.simpoint.telegram.TelegramModels.SetMyCommands;
import ru.simpoint.telegram.TelegramModels.Update;

import java.time.Duration;
import java.util.List;

/**
 * Тонкий клиент Telegram Bot API поверх RestClient.
 * В логи никогда не пишется URL запроса: в нём токен бота.
 */
@Slf4j
public class TelegramApi {

    private static final ParameterizedTypeReference<ApiResponse<List<Update>>> UPDATES =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient client;

    public TelegramApi(String token) {
        ObjectMapper mapper = new ObjectMapper()
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .setSerializationInclusion(JsonInclude.Include.NON_NULL)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(Duration.ofSeconds(10));
        requestFactory.setReadTimeout(Duration.ofSeconds(60));

        this.client = RestClient.builder()
                .baseUrl("https://api.telegram.org/bot" + token)
                .requestFactory(requestFactory)
                .messageConverters(converters -> {
                    converters.removeIf(MappingJackson2HttpMessageConverter.class::isInstance);
                    converters.add(new MappingJackson2HttpMessageConverter(mapper));
                })
                .build();
    }

    /** Long polling: Telegram держит запрос до timeoutSeconds, пока не появятся новые события. */
    public List<Update> getUpdates(long offset, int timeoutSeconds) {
        ApiResponse<List<Update>> response = client.post()
                .uri("/getUpdates")
                .contentType(MediaType.APPLICATION_JSON)
                .body(new GetUpdates(offset, timeoutSeconds, List.of("message", "callback_query")))
                .retrieve()
                .body(UPDATES);
        if (response == null || !response.ok() || response.result() == null) {
            return List.of();
        }
        return response.result();
    }

    public void sendMessage(long chatId, String text, Object replyMarkup) {
        call("sendMessage", new SendMessage(chatId, text, "HTML", replyMarkup));
    }

    public boolean editMessage(long chatId, long messageId, String text, InlineKeyboardMarkup markup) {
        return call("editMessageText", new EditMessageText(chatId, messageId, text, "HTML", markup));
    }

    /** Обновляет сообщение с кнопками, если оно есть, иначе отправляет новое. */
    public void show(long chatId, Long messageId, String text, InlineKeyboardMarkup markup) {
        if (messageId != null && editMessage(chatId, messageId, text, markup)) {
            return;
        }
        sendMessage(chatId, text, markup);
    }

    public void answerCallback(String callbackQueryId) {
        call("answerCallbackQuery", new AnswerCallbackQuery(callbackQueryId));
    }

    public void deleteMessage(long chatId, long messageId) {
        call("deleteMessage", new DeleteMessage(chatId, messageId));
    }

    public void setCommands(List<BotCommand> commands) {
        call("setMyCommands", new SetMyCommands(commands));
    }

    private boolean call(String method, Object body) {
        try {
            client.post()
                    .uri("/" + method)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .toBodilessEntity();
            return true;
        } catch (RestClientResponseException e) {
            String response = e.getResponseBodyAsString();
            if (response.contains("message is not modified")) {
                return true;
            }
            log.warn("Telegram {} вернул {}: {}", method, e.getStatusCode().value(), response);
            return false;
        } catch (RestClientException e) {
            log.warn("Telegram {} недоступен: {}", method, e.getClass().getSimpleName());
            return false;
        }
    }
}

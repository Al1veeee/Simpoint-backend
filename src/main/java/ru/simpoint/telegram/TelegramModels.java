package ru.simpoint.telegram;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

/** Минимальный набор типов Telegram Bot API. Имена полей переводятся в snake_case маппером TelegramApi. */
public final class TelegramModels {

    private TelegramModels() {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record ApiResponse<T>(boolean ok, T result, String description) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Update(@JsonProperty("update_id") long updateId,
                         Message message,
                         @JsonProperty("callback_query") CallbackQuery callbackQuery) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Message(@JsonProperty("message_id") long messageId, User from, Chat chat, String text) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record User(long id, @JsonProperty("first_name") String firstName, String username) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Chat(long id, String type) {
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CallbackQuery(String id, User from, Message message, String data) {
    }

    public record InlineButton(String text, String callbackData) {
    }

    public record InlineKeyboardMarkup(List<List<InlineButton>> inlineKeyboard) {
    }

    public record KeyboardButton(String text) {
    }

    public record ReplyKeyboardMarkup(List<List<KeyboardButton>> keyboard,
                                      @JsonProperty("resize_keyboard") boolean resizeKeyboard,
                                      @JsonProperty("is_persistent") boolean isPersistent) {
    }

    public record ReplyKeyboardRemove(boolean removeKeyboard) {
    }

    public record BotCommand(String command, String description) {
    }

    record GetUpdates(long offset, int timeout, List<String> allowedUpdates) {
    }

    record SendMessage(long chatId, String text, String parseMode, Object replyMarkup) {
    }

    record EditMessageText(long chatId, long messageId, String text, String parseMode, Object replyMarkup) {
    }

    record AnswerCallbackQuery(String callbackQueryId) {
    }

    record DeleteMessage(long chatId, long messageId) {
    }

    record SetMyCommands(List<BotCommand> commands) {
    }
}

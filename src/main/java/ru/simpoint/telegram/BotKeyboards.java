package ru.simpoint.telegram;

import ru.simpoint.domain.Operator;
import ru.simpoint.telegram.TelegramModels.InlineButton;
import ru.simpoint.telegram.TelegramModels.InlineKeyboardMarkup;
import ru.simpoint.telegram.TelegramModels.KeyboardButton;
import ru.simpoint.telegram.TelegramModels.ReplyKeyboardMarkup;
import ru.simpoint.telegram.TelegramModels.ReplyKeyboardRemove;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class BotKeyboards {

    public static final String SALE = "🧾 Новая продажа";
    public static final String TODAY = "📊 Сегодня";
    public static final String STOCK = "📦 Склад";
    public static final String RECENT = "🕘 Последние продажи";

    private BotKeyboards() {
    }

    public static ReplyKeyboardMarkup mainMenu() {
        return new ReplyKeyboardMarkup(List.of(
                List.of(new KeyboardButton(SALE)),
                List.of(new KeyboardButton(TODAY), new KeyboardButton(STOCK)),
                List.of(new KeyboardButton(RECENT))
        ), true, true);
    }

    public static ReplyKeyboardRemove removeMenu() {
        return new ReplyKeyboardRemove(true);
    }

    public static InlineButton btn(String text, String callbackData) {
        return new InlineButton(text, callbackData);
    }

    public static InlineKeyboardMarkup inline(List<List<InlineButton>> rows) {
        return new InlineKeyboardMarkup(rows);
    }

    public static List<List<InlineButton>> chunk(List<InlineButton> buttons, int perRow) {
        List<List<InlineButton>> rows = new ArrayList<>();
        for (int i = 0; i < buttons.size(); i += perRow) {
            rows.add(List.copyOf(buttons.subList(i, Math.min(i + perRow, buttons.size()))));
        }
        return rows;
    }

    public static List<InlineButton> operatorButtons(String callbackPrefix) {
        return Arrays.stream(Operator.values())
                .map(op -> btn(op.getDisplayName(), callbackPrefix + op.name()))
                .toList();
    }
}

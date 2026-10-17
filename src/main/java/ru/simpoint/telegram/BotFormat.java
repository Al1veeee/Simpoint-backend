package ru.simpoint.telegram;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.PaymentMethod;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.text.DecimalFormatSymbols;
import java.util.Locale;

/** Форматирование сумм и текстов для сообщений бота (parse_mode = HTML). */
public final class BotFormat {

    private static final char NBSP = ' ';

    private BotFormat() {
    }

    public static String money(BigDecimal value) {
        BigDecimal v = value == null ? BigDecimal.ZERO : value;
        DecimalFormatSymbols symbols = new DecimalFormatSymbols(Locale.ROOT);
        symbols.setGroupingSeparator(NBSP);
        symbols.setDecimalSeparator(',');
        boolean whole = v.stripTrailingZeros().scale() <= 0;
        DecimalFormat format = new DecimalFormat(whole ? "#,##0" : "#,##0.00", symbols);
        return format.format(v) + NBSP + "₽";
    }

    /** Разбирает «500», «1 500», «1499,90», «500р». Возвращает null, если это не сумма. */
    public static BigDecimal parsePrice(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text
                .replaceAll("[\\s\\u00A0₽]", "")
                .replaceAll("(?iu)(руб\\.?|р\\.?)$", "")
                .replace(',', '.');
        if (!cleaned.matches("\\d{1,7}(\\.\\d{1,2})?")) {
            return null;
        }
        return new BigDecimal(cleaned).setScale(2, RoundingMode.HALF_UP);
    }

    public static String esc(String text) {
        if (text == null) {
            return "";
        }
        return text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    public static String iccidTail(String iccid) {
        return "…" + iccid.substring(Math.max(0, iccid.length() - 6));
    }

    public static String operator(Operator operator) {
        return operator == null ? "без оператора" : operator.getDisplayName();
    }

    public static String payment(PaymentMethod method) {
        return switch (method) {
            case CASH -> "наличные";
            case CARD -> "карта";
            case TRANSFER -> "перевод";
        };
    }
}

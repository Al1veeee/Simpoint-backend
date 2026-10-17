package ru.simpoint.telegram;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

class BotFormatTest {

    @Test
    void wholeAmountsHaveNoKopecks() {
        assertThat(BotFormat.money(new BigDecimal("1500.00"))).isEqualTo("1 500 ₽");
        assertThat(BotFormat.money(new BigDecimal("500"))).isEqualTo("500 ₽");
    }

    @Test
    void fractionalAmountsShowTwoDigits() {
        assertThat(BotFormat.money(new BigDecimal("1499.9"))).isEqualTo("1 499,90 ₽");
    }

    @Test
    void parsesCommonPriceFormats() {
        assertThat(BotFormat.parsePrice("500")).isEqualByComparingTo("500");
        assertThat(BotFormat.parsePrice("1 500")).isEqualByComparingTo("1500");
        assertThat(BotFormat.parsePrice("1499,90")).isEqualByComparingTo("1499.90");
        assertThat(BotFormat.parsePrice("500р")).isEqualByComparingTo("500");
        assertThat(BotFormat.parsePrice("300 руб")).isEqualByComparingTo("300");
        assertThat(BotFormat.parsePrice("250 ₽")).isEqualByComparingTo("250");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "abc", "-5", "10.555", "12345678", "1,2,3"})
    void rejectsGarbage(String input) {
        assertThat(BotFormat.parsePrice(input)).isNull();
    }

    @Test
    void escapesHtml() {
        assertThat(BotFormat.esc("<b>A&B</b>")).isEqualTo("&lt;b&gt;A&amp;B&lt;/b&gt;");
    }

    @Test
    void showsIccidTail() {
        assertThat(BotFormat.iccidTail("8970100000000123456")).isEqualTo("…123456");
    }
}

package ru.simpoint.domain;

/**
 * Услуги точки. Для услуг с operatorBound = true обязателен оператор
 * и проверка по матрице возможностей (operator_capability).
 */
public enum ServiceType {
    SIM_SALE("Продажа сим-карты", true),
    MNP("Перенос номера", true),
    SIM_REPLACEMENT("Замена SIM / eSIM", true),
    IMEI_REPLACEMENT("Замена IMEI", true),
    DATA_UPDATE("Обновление данных абонента", true),
    NUMBER_CHANGE("Смена номера", true),
    TARIFF_CHANGE("Смена тарифа", true),
    CONTRACT_REISSUE("Переоформление договора", true),
    CONTRACT_TERMINATION("Расторжение договора", true),
    PHONE_REPAIR("Ремонт телефона", false),
    PHONE_SETUP("Настройка телефона", false),
    PAYMENT("Приём платежа", false),
    FOREIGNER_PACKAGE("Пакет документов для иностранца", false),
    APP_INSTALL("Установка приложения", false);

    private final String displayName;
    private final boolean operatorBound;

    ServiceType(String displayName, boolean operatorBound) {
        this.displayName = displayName;
        this.operatorBound = operatorBound;
    }

    public String getDisplayName() {
        return displayName;
    }

    public boolean isOperatorBound() {
        return operatorBound;
    }
}

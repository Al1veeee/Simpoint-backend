package ru.simpoint.domain;

public enum Operator {
    T2("Т2"),
    MTS("МТС"),
    BEELINE("Билайн"),
    MEGAFON("МегаФон"),
    YOTA("Йота");

    private final String displayName;

    Operator(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}

package ru.simpoint.dto;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.Tariff;

import java.math.BigDecimal;

public record TariffResponse(
        Long id,
        Operator operator,
        String operatorName,
        String name,
        BigDecimal monthlyPrice,
        Integer minutes,
        Integer internetGb,
        boolean unlimitedInternet,
        boolean active
) {
    public static TariffResponse from(Tariff t) {
        return new TariffResponse(
                t.getId(),
                t.getOperator(),
                t.getOperator().getDisplayName(),
                t.getName(),
                t.getMonthlyPrice(),
                t.getMinutes(),
                t.getInternetGb(),
                t.isUnlimitedInternet(),
                t.isActive()
        );
    }
}

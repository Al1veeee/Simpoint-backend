package ru.simpoint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.simpoint.domain.Operator;

import java.math.BigDecimal;

public record TariffRequest(
        @NotNull Operator operator,
        @NotBlank @Size(max = 120) String name,
        @PositiveOrZero BigDecimal monthlyPrice,
        @PositiveOrZero Integer minutes,
        @PositiveOrZero Integer internetGb,
        boolean unlimitedInternet
) {
}

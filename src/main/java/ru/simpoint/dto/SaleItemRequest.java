package ru.simpoint.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.ServiceType;

import java.math.BigDecimal;

public record SaleItemRequest(
        @NotNull ServiceType serviceType,
        Operator operator,
        Long simCardId,
        Long tariffId,
        @Size(max = 255) String description,
        @PositiveOrZero BigDecimal price
) {
}

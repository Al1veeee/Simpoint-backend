package ru.simpoint.dto;

import ru.simpoint.domain.Operator;

public record StockSummaryResponse(
        Operator operator,
        String operatorName,
        long inStock,
        long sold,
        long defective
) {
}

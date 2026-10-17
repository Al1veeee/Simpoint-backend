package ru.simpoint.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import ru.simpoint.domain.PaymentMethod;

import java.util.List;

public record CreateSaleRequest(
        Long customerId,
        @NotNull PaymentMethod paymentMethod,
        @NotEmpty List<@Valid SaleItemRequest> items,
        @Size(max = 80) String performerName
) {
}

package ru.simpoint.dto;

import ru.simpoint.domain.PaymentMethod;
import ru.simpoint.domain.Sale;
import ru.simpoint.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record SaleResponse(
        Long id,
        Instant createdAt,
        SaleStatus status,
        PaymentMethod paymentMethod,
        BigDecimal total,
        String employee,
        String performerName,
        CustomerResponse customer,
        Instant cancelledAt,
        String cancelReason,
        List<SaleItemResponse> items
) {
    public static SaleResponse from(Sale s) {
        return new SaleResponse(
                s.getId(),
                s.getCreatedAt(),
                s.getStatus(),
                s.getPaymentMethod(),
                s.getTotal(),
                s.getEmployee().getUsername(),
                s.getPerformerName(),
                s.getCustomer() != null ? CustomerResponse.from(s.getCustomer()) : null,
                s.getCancelledAt(),
                s.getCancelReason(),
                s.getItems().stream().map(SaleItemResponse::from).toList()
        );
    }

    public static SaleResponse summary(Sale s) {
        return new SaleResponse(
                s.getId(),
                s.getCreatedAt(),
                s.getStatus(),
                s.getPaymentMethod(),
                s.getTotal(),
                s.getEmployee().getUsername(),
                s.getPerformerName(),
                s.getCustomer() != null ? CustomerResponse.from(s.getCustomer()) : null,
                s.getCancelledAt(),
                s.getCancelReason(),
                List.of()
        );
    }
}

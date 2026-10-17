package ru.simpoint.dto;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SaleItem;
import ru.simpoint.domain.ServiceType;

import java.math.BigDecimal;

public record SaleItemResponse(
        Long id,
        ServiceType serviceType,
        String serviceName,
        Operator operator,
        Long simCardId,
        String iccid,
        Long tariffId,
        String tariffName,
        String description,
        BigDecimal price
) {
    public static SaleItemResponse from(SaleItem i) {
        return new SaleItemResponse(
                i.getId(),
                i.getServiceType(),
                i.getServiceType().getDisplayName(),
                i.getOperator(),
                i.getSimCard() != null ? i.getSimCard().getId() : null,
                i.getSimCard() != null ? i.getSimCard().getIccid() : null,
                i.getTariff() != null ? i.getTariff().getId() : null,
                i.getTariff() != null ? i.getTariff().getName() : null,
                i.getDescription(),
                i.getPrice()
        );
    }
}

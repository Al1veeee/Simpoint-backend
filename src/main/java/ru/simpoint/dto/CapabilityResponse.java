package ru.simpoint.dto;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.OperatorCapability;
import ru.simpoint.domain.ServiceType;

public record CapabilityResponse(
        Operator operator,
        String operatorName,
        ServiceType serviceType,
        String serviceName,
        boolean supported,
        String note
) {
    public static CapabilityResponse from(OperatorCapability c) {
        return new CapabilityResponse(
                c.getOperator(),
                c.getOperator().getDisplayName(),
                c.getServiceType(),
                c.getServiceType().getDisplayName(),
                c.isSupported(),
                c.getNote()
        );
    }
}

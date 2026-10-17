package ru.simpoint.dto;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SimCard;
import ru.simpoint.domain.SimStatus;

import java.time.Instant;

public record SimCardResponse(
        Long id,
        Operator operator,
        String operatorName,
        String iccid,
        String msisdn,
        SimStatus status,
        Instant receivedAt,
        Instant soldAt
) {
    public static SimCardResponse from(SimCard s) {
        return new SimCardResponse(
                s.getId(),
                s.getOperator(),
                s.getOperator().getDisplayName(),
                s.getIccid(),
                s.getMsisdn(),
                s.getStatus(),
                s.getReceivedAt(),
                s.getSoldAt()
        );
    }
}

package ru.simpoint.dto;

import ru.simpoint.domain.Operator;
import ru.simpoint.domain.ServiceType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public record SalesReportResponse(
        LocalDate from,
        LocalDate to,
        long salesCount,
        BigDecimal totalRevenue,
        List<OperatorRevenue> byOperator,
        List<ServiceRevenue> byServiceType,
        List<PerformerRevenue> byPerformer
) {
    public record OperatorRevenue(Operator operator, String operatorName, long items, BigDecimal revenue) {
    }

    public record ServiceRevenue(ServiceType serviceType, String serviceName, long items, BigDecimal revenue) {
    }

    public record PerformerRevenue(String name, long sales, BigDecimal revenue) {
    }
}

package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.config.AppProperties;
import ru.simpoint.domain.SaleStatus;
import ru.simpoint.dto.SalesReportResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.repository.SaleItemRepository;
import ru.simpoint.repository.SaleRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final SaleRepository sales;
    private final SaleItemRepository saleItems;
    private final AppProperties appProperties;

    @Transactional(readOnly = true)
    public SalesReportResponse salesSummary(LocalDate from, LocalDate to) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("Дата окончания раньше даты начала");
        }
        ZoneId zone = ZoneId.of(appProperties.timezone());
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();

        long count = sales.countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(SaleStatus.COMPLETED, start, end);
        BigDecimal revenue = sales.revenueBetween(SaleStatus.COMPLETED, start, end);

        List<SalesReportResponse.OperatorRevenue> byOperator = saleItems
                .revenueByOperator(SaleStatus.COMPLETED, start, end).stream()
                .map(v -> new SalesReportResponse.OperatorRevenue(
                        v.getOperator(),
                        v.getOperator() == null ? "Без оператора" : v.getOperator().getDisplayName(),
                        v.getItems(),
                        v.getRevenue()))
                .toList();

        List<SalesReportResponse.ServiceRevenue> byService = saleItems
                .revenueByServiceType(SaleStatus.COMPLETED, start, end).stream()
                .map(v -> new SalesReportResponse.ServiceRevenue(
                        v.getServiceType(),
                        v.getServiceType().getDisplayName(),
                        v.getItems(),
                        v.getRevenue()))
                .toList();

        List<SalesReportResponse.PerformerRevenue> byPerformer = sales
                .revenueByPerformer(SaleStatus.COMPLETED, start, end).stream()
                .map(v -> new SalesReportResponse.PerformerRevenue(v.getName(), v.getSales(), v.getRevenue()))
                .toList();

        return new SalesReportResponse(from, to, count, revenue, byOperator, byService, byPerformer);
    }
}

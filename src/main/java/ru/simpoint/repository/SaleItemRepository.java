package ru.simpoint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SaleItem;
import ru.simpoint.domain.SaleStatus;
import ru.simpoint.domain.ServiceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public interface SaleItemRepository extends JpaRepository<SaleItem, Long> {

    boolean existsBySimCard_Id(Long simCardId);

    interface OperatorRevenueView {
        Operator getOperator();

        long getItems();

        BigDecimal getRevenue();
    }

    interface ServiceRevenueView {
        ServiceType getServiceType();

        long getItems();

        BigDecimal getRevenue();
    }

    @Query("""
            select i.operator as operator, count(i) as items, sum(i.price) as revenue
            from SaleItem i join i.sale s
            where s.status = :status and s.createdAt >= :from and s.createdAt < :to
            group by i.operator
            order by sum(i.price) desc
            """)
    List<OperatorRevenueView> revenueByOperator(@Param("status") SaleStatus status,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    @Query("""
            select i.serviceType as serviceType, count(i) as items, sum(i.price) as revenue
            from SaleItem i join i.sale s
            where s.status = :status and s.createdAt >= :from and s.createdAt < :to
            group by i.serviceType
            order by sum(i.price) desc
            """)
    List<ServiceRevenueView> revenueByServiceType(@Param("status") SaleStatus status,
                                                  @Param("from") Instant from,
                                                  @Param("to") Instant to);
}

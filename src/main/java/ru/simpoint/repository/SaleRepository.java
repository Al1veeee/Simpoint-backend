package ru.simpoint.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import ru.simpoint.domain.Sale;
import ru.simpoint.domain.SaleStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface SaleRepository extends JpaRepository<Sale, Long> {

    interface PerformerRevenueView {
        String getName();

        long getSales();

        BigDecimal getRevenue();
    }

    /** Выручка по исполнителям; для старых продаж без исполнителя берётся имя аккаунта. */
    @Query("""
            select coalesce(s.performerName, e.fullName) as name, count(s) as sales, sum(s.total) as revenue
            from Sale s join s.employee e
            where s.status = :status and s.createdAt >= :from and s.createdAt < :to
            group by coalesce(s.performerName, e.fullName)
            order by sum(s.total) desc
            """)
    List<PerformerRevenueView> revenueByPerformer(@Param("status") SaleStatus status,
                                                @Param("from") Instant from,
                                                @Param("to") Instant to);

    @EntityGraph(attributePaths = {"items", "items.simCard", "items.tariff", "employee", "customer"})
    Optional<Sale> findWithItemsById(Long id);

    @EntityGraph(attributePaths = {"employee", "customer"})
    Page<Sale> findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(Instant from, Instant to, Pageable pageable);

    long countByStatusAndCreatedAtGreaterThanEqualAndCreatedAtLessThan(SaleStatus status, Instant from, Instant to);

    @Query("""
            select coalesce(sum(s.total), 0)
            from Sale s
            where s.status = :status and s.createdAt >= :from and s.createdAt < :to
            """)
    BigDecimal revenueBetween(@Param("status") SaleStatus status, @Param("from") Instant from, @Param("to") Instant to);
}

package ru.simpoint.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SimCard;
import ru.simpoint.domain.SimStatus;

import java.util.Collection;
import java.util.List;

public interface SimCardRepository extends JpaRepository<SimCard, Long>, JpaSpecificationExecutor<SimCard> {

    List<SimCard> findByIccidIn(Collection<String> iccids);

    List<SimCard> findTop20ByOperatorAndStatusOrderByReceivedAtAscIdAsc(Operator operator, SimStatus status);

    Page<SimCard> findByOperatorAndStatusInOrderByStatusDescReceivedAtAscIdAsc(
            Operator operator, Collection<SimStatus> statuses, Pageable pageable);

    List<SimCard> findTop20ByOperatorAndStatusAndIccidEndingWithOrderByReceivedAtAscIdAsc(
            Operator operator, SimStatus status, String iccidSuffix);

    interface StockView {
        Operator getOperator();

        SimStatus getStatus();

        long getQty();
    }

    @Query("""
            select s.operator as operator, s.status as status, count(s) as qty
            from SimCard s
            group by s.operator, s.status
            order by s.operator, s.status
            """)
    List<StockView> stockByOperatorAndStatus();
}

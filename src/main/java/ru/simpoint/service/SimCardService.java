package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SimCard;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.dto.ReceiveSimBatchRequest;
import ru.simpoint.dto.SimCardResponse;
import ru.simpoint.dto.StockSummaryResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.SaleItemRepository;
import ru.simpoint.repository.SimCardRepository;

import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SimCardService {

    private final SimCardRepository simCards;
    private final SaleItemRepository saleItems;

    @Transactional
    public List<SimCardResponse> receiveBatch(ReceiveSimBatchRequest request) {
        Set<String> unique = new LinkedHashSet<>(request.iccids());
        if (unique.size() != request.iccids().size()) {
            throw new BusinessRuleException("В партии есть повторяющиеся ICCID");
        }
        List<String> existing = simCards.findByIccidIn(unique).stream().map(SimCard::getIccid).toList();
        if (!existing.isEmpty()) {
            throw new BusinessRuleException("Эти ICCID уже на складе: " + String.join(", ", existing));
        }
        List<SimCard> batch = new ArrayList<>();
        for (String iccid : unique) {
            SimCard sim = new SimCard();
            sim.setOperator(request.operator());
            sim.setIccid(iccid);
            sim.setStatus(SimStatus.IN_STOCK);
            batch.add(sim);
        }
        return simCards.saveAll(batch).stream().map(SimCardResponse::from).toList();
    }

    @Transactional(readOnly = true)
    public Page<SimCardResponse> search(Operator operator, SimStatus status, Pageable pageable) {
        Specification<SimCard> spec = Specification.where(hasOperator(operator)).and(hasStatus(status));
        return simCards.findAll(spec, pageable).map(SimCardResponse::from);
    }

    /**
     * Сим-карты оператора на складе, старые партии первыми. Если задан хвост ICCID, ищет по нему.
     * Используется ботом: продавец выбирает карту кнопкой или присылает последние цифры.
     */
    @Transactional(readOnly = true)
    public List<SimCardResponse> findAvailable(Operator operator, String iccidSuffix,
                                               Collection<Long> excludeIds, int limit) {
        List<SimCard> found = iccidSuffix == null || iccidSuffix.isBlank()
                ? simCards.findTop20ByOperatorAndStatusOrderByReceivedAtAscIdAsc(operator, SimStatus.IN_STOCK)
                : simCards.findTop20ByOperatorAndStatusAndIccidEndingWithOrderByReceivedAtAscIdAsc(
                        operator, SimStatus.IN_STOCK, iccidSuffix);
        return found.stream()
                .filter(sim -> !excludeIds.contains(sim.getId()))
                .limit(limit)
                .map(SimCardResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public SimCardResponse get(Long id) {
        return SimCardResponse.from(find(id));
    }

    @Transactional
    public SimCardResponse markDefective(Long id) {
        SimCard sim = find(id);
        if (sim.getStatus() == SimStatus.SOLD) {
            throw new BusinessRuleException("Проданную сим-карту нельзя пометить как бракованную");
        }
        sim.setStatus(SimStatus.DEFECTIVE);
        return SimCardResponse.from(sim);
    }

    /** Непроданные карты оператора: сначала те, что на складе, потом брак. */
    @Transactional(readOnly = true)
    public Page<SimCardResponse> listUnsold(Operator operator, Pageable pageable) {
        return simCards.findByOperatorAndStatusInOrderByStatusDescReceivedAtAscIdAsc(
                        operator, EnumSet.of(SimStatus.IN_STOCK, SimStatus.DEFECTIVE), pageable)
                .map(SimCardResponse::from);
    }

    @Transactional
    public SimCardResponse returnToStock(Long id) {
        SimCard sim = find(id);
        if (sim.getStatus() != SimStatus.DEFECTIVE) {
            throw new BusinessRuleException("Вернуть на склад можно только карту с пометкой «брак»");
        }
        sim.setStatus(SimStatus.IN_STOCK);
        return SimCardResponse.from(sim);
    }

    /**
     * Удаляет ошибочно внесённую карту. Карты, которые хоть раз попадали в чек
     * (в том числе отменённый), не удаляются: история продаж должна сохраниться.
     */
    @Transactional
    public void delete(Long id) {
        SimCard sim = find(id);
        if (sim.getStatus() == SimStatus.SOLD || saleItems.existsBySimCard_Id(id)) {
            throw new BusinessRuleException("Сим-карта " + sim.getIccid()
                    + " есть в истории продаж, удалить её нельзя. Пометь её как брак.");
        }
        simCards.delete(sim);
    }

    @Transactional(readOnly = true)
    public List<StockSummaryResponse> stockSummary() {
        Map<Operator, long[]> counters = new EnumMap<>(Operator.class);
        for (Operator operator : Operator.values()) {
            counters.put(operator, new long[3]);
        }
        for (SimCardRepository.StockView row : simCards.stockByOperatorAndStatus()) {
            long[] c = counters.get(row.getOperator());
            switch (row.getStatus()) {
                case IN_STOCK -> c[0] = row.getQty();
                case SOLD -> c[1] = row.getQty();
                case DEFECTIVE -> c[2] = row.getQty();
            }
        }
        return counters.entrySet().stream()
                .map(e -> new StockSummaryResponse(
                        e.getKey(), e.getKey().getDisplayName(), e.getValue()[0], e.getValue()[1], e.getValue()[2]))
                .toList();
    }

    private SimCard find(Long id) {
        return simCards.findById(id).orElseThrow(() -> new NotFoundException("Сим-карта не найдена: " + id));
    }

    private static Specification<SimCard> hasOperator(Operator operator) {
        return (root, query, cb) -> operator == null ? null : cb.equal(root.get("operator"), operator);
    }

    private static Specification<SimCard> hasStatus(SimStatus status) {
        return (root, query, cb) -> status == null ? null : cb.equal(root.get("status"), status);
    }
}

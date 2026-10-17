package ru.simpoint.telegram;

import lombok.Getter;
import lombok.Setter;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.PaymentMethod;
import ru.simpoint.domain.ServiceType;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Состояние диалога с одним сотрудником: шаг мастера и черновик чека. */
@Getter
@Setter
public class ChatSession {

    public enum Step {
        IDLE,
        SALE_OPERATOR,
        SALE_SERVICE,
        SALE_SIM,
        SALE_TARIFF,
        SALE_PRICE,
        SALE_REVIEW,
        SALE_PERFORMER,
        SALE_PERFORMER_NAME,
        STOCK_OPERATOR,
        STOCK_ICCIDS
    }

    public record DraftItem(ServiceType serviceType, Operator operator, Long simCardId, String iccid,
                            Long tariffId, String tariffName, BigDecimal price) {
    }

    private Step step = Step.IDLE;

    private Operator operator;
    private ServiceType serviceType;
    private Long simCardId;
    private String iccid;
    private Long tariffId;
    private String tariffName;
    private BigDecimal tariffPrice;

    private PaymentMethod paymentMethod;

    private Operator stockOperator;

    private final List<DraftItem> items = new ArrayList<>();

    public void resetItem() {
        operator = null;
        serviceType = null;
        simCardId = null;
        iccid = null;
        tariffId = null;
        tariffName = null;
        tariffPrice = null;
    }

    public void resetAll() {
        resetItem();
        items.clear();
        paymentMethod = null;
        stockOperator = null;
        step = Step.IDLE;
    }

    public List<Long> draftSimIds() {
        return items.stream().map(DraftItem::simCardId).filter(Objects::nonNull).toList();
    }

    public BigDecimal draftTotal() {
        return items.stream().map(DraftItem::price).reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

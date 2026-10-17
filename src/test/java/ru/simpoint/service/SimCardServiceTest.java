package ru.simpoint.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SimCard;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.repository.SaleItemRepository;
import ru.simpoint.repository.SimCardRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SimCardServiceTest {

    @Mock
    private SimCardRepository simCards;
    @Mock
    private SaleItemRepository saleItems;

    @InjectMocks
    private SimCardService service;

    @Test
    void wronglyEnteredCardIsDeleted() {
        SimCard sim = sim(1L, SimStatus.IN_STOCK);
        when(simCards.findById(1L)).thenReturn(Optional.of(sim));
        when(saleItems.existsBySimCard_Id(1L)).thenReturn(false);

        service.delete(1L);

        verify(simCards).delete(sim);
    }

    @Test
    void cardFromCancelledSaleIsNotDeleted() {
        when(simCards.findById(2L)).thenReturn(Optional.of(sim(2L, SimStatus.IN_STOCK)));
        when(saleItems.existsBySimCard_Id(2L)).thenReturn(true);

        assertThatThrownBy(() -> service.delete(2L))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("истории продаж");
        verify(simCards, never()).delete(any());
    }

    @Test
    void soldCardIsNotDeleted() {
        when(simCards.findById(3L)).thenReturn(Optional.of(sim(3L, SimStatus.SOLD)));

        assertThatThrownBy(() -> service.delete(3L)).isInstanceOf(BusinessRuleException.class);
        verify(simCards, never()).delete(any());
    }

    @Test
    void defectiveCardReturnsToStock() {
        SimCard sim = sim(4L, SimStatus.DEFECTIVE);
        when(simCards.findById(4L)).thenReturn(Optional.of(sim));

        assertThat(service.returnToStock(4L).status()).isEqualTo(SimStatus.IN_STOCK);
    }

    @Test
    void onlyDefectiveCardCanBeReturned() {
        when(simCards.findById(5L)).thenReturn(Optional.of(sim(5L, SimStatus.IN_STOCK)));

        assertThatThrownBy(() -> service.returnToStock(5L)).isInstanceOf(BusinessRuleException.class);
    }

    private static SimCard sim(long id, SimStatus status) {
        SimCard sim = new SimCard();
        sim.setId(id);
        sim.setOperator(Operator.T2);
        sim.setIccid("897010000000000000" + id);
        sim.setStatus(status);
        return sim;
    }
}

package ru.simpoint.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.simpoint.config.AppProperties;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.PaymentMethod;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.Sale;
import ru.simpoint.domain.SaleStatus;
import ru.simpoint.domain.ServiceType;
import ru.simpoint.domain.SimCard;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.domain.Tariff;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.dto.CreateSaleRequest;
import ru.simpoint.dto.SaleItemRequest;
import ru.simpoint.dto.SaleResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.repository.CustomerRepository;
import ru.simpoint.repository.SaleRepository;
import ru.simpoint.repository.SimCardRepository;
import ru.simpoint.repository.TariffRepository;
import ru.simpoint.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleServiceTest {

    @Mock
    private SaleRepository sales;
    @Mock
    private SimCardRepository simCards;
    @Mock
    private TariffRepository tariffs;
    @Mock
    private CustomerRepository customers;
    @Mock
    private UserAccountRepository users;
    @Mock
    private CapabilityService capabilityService;

    private SaleService service;

    private UserAccount seller;

    @BeforeEach
    void setUp() {
        service = new SaleService(sales, simCards, tariffs, customers, users, capabilityService,
                new AppProperties("Europe/Moscow"));
        seller = new UserAccount();
        seller.setId(1L);
        seller.setUsername("seller");
        seller.setRole(Role.SELLER);
        when(users.findByUsername("seller")).thenReturn(Optional.of(seller));
    }

    @Test
    void simSaleMarksCardSoldAndSumsTotal() {
        SimCard sim = new SimCard();
        sim.setId(10L);
        sim.setOperator(Operator.T2);
        sim.setIccid("8970100000000000001");
        sim.setStatus(SimStatus.IN_STOCK);

        Tariff tariff = new Tariff();
        tariff.setId(5L);
        tariff.setOperator(Operator.T2);
        tariff.setName("Безлимит");
        tariff.setMonthlyPrice(new BigDecimal("500.00"));
        tariff.setActive(true);

        when(capabilityService.isSupported(Operator.T2, ServiceType.SIM_SALE)).thenReturn(true);
        when(simCards.findById(10L)).thenReturn(Optional.of(sim));
        when(tariffs.findById(5L)).thenReturn(Optional.of(tariff));
        when(sales.save(any(Sale.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CreateSaleRequest request = new CreateSaleRequest(null, PaymentMethod.CARD, List.of(
                new SaleItemRequest(ServiceType.SIM_SALE, Operator.T2, 10L, 5L, null, null),
                new SaleItemRequest(ServiceType.PHONE_SETUP, null, null, null, "Настройка", new BigDecimal("300.00"))
        ), "Тамерлан");

        SaleResponse response = service.create(request, "seller");

        assertThat(response.status()).isEqualTo(SaleStatus.COMPLETED);
        assertThat(response.total()).isEqualByComparingTo("800.00");
        assertThat(response.items()).hasSize(2);
        assertThat(response.items().get(0).price()).isEqualByComparingTo("500.00");
        assertThat(sim.getStatus()).isEqualTo(SimStatus.SOLD);
        assertThat(sim.getSoldAt()).isNotNull();
        assertThat(response.performerName()).isEqualTo("Тамерлан");
    }

    @Test
    void unsupportedOperatorServiceIsRejected() {
        when(capabilityService.isSupported(Operator.MTS, ServiceType.IMEI_REPLACEMENT)).thenReturn(false);

        CreateSaleRequest request = new CreateSaleRequest(null, PaymentMethod.CASH, List.of(
                new SaleItemRequest(ServiceType.IMEI_REPLACEMENT, Operator.MTS, null, null, null, new BigDecimal("200"))
        ), null);

        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("МТС");
        verify(sales, never()).save(any());
    }

    @Test
    void soldSimCannotBeSoldAgain() {
        SimCard sim = new SimCard();
        sim.setId(10L);
        sim.setOperator(Operator.T2);
        sim.setIccid("8970100000000000001");
        sim.setStatus(SimStatus.SOLD);

        when(capabilityService.isSupported(Operator.T2, ServiceType.SIM_SALE)).thenReturn(true);
        when(simCards.findById(10L)).thenReturn(Optional.of(sim));

        CreateSaleRequest request = new CreateSaleRequest(null, PaymentMethod.CASH, List.of(
                new SaleItemRequest(ServiceType.SIM_SALE, Operator.T2, 10L, null, null, new BigDecimal("100"))
        ), null);

        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("недоступна");
    }

    @Test
    void operatorBoundServiceRequiresOperator() {
        CreateSaleRequest request = new CreateSaleRequest(null, PaymentMethod.CASH, List.of(
                new SaleItemRequest(ServiceType.MNP, null, null, null, null, new BigDecimal("0"))
        ), null);

        assertThatThrownBy(() -> service.create(request, "seller"))
                .isInstanceOf(BusinessRuleException.class)
                .hasMessageContaining("оператора");
    }
}

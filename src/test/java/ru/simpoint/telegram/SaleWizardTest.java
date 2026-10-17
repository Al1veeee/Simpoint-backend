package ru.simpoint.telegram;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.PaymentMethod;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.SaleStatus;
import ru.simpoint.domain.ServiceType;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.dto.CapabilityResponse;
import ru.simpoint.dto.CreateSaleRequest;
import ru.simpoint.dto.PerformerResponse;
import ru.simpoint.dto.SaleItemRequest;
import ru.simpoint.dto.SaleResponse;
import ru.simpoint.dto.SimCardResponse;
import ru.simpoint.dto.TariffResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.service.CapabilityService;
import ru.simpoint.service.PerformerService;
import ru.simpoint.service.SaleService;
import ru.simpoint.service.SimCardService;
import ru.simpoint.service.TariffService;
import ru.simpoint.service.TelegramAccountService.BotUser;
import ru.simpoint.telegram.ChatSession.Step;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SaleWizardTest {

    private static final long CHAT = 1L;
    private static final long MSG = 10L;

    @Mock
    private TelegramApi api;
    @Mock
    private CapabilityService capabilityService;
    @Mock
    private SimCardService simCardService;
    @Mock
    private TariffService tariffService;
    @Mock
    private SaleService saleService;
    @Mock
    private PerformerService performerService;

    private SaleWizard wizard;
    private ChatSession session;
    private final BotUser seller = new BotUser("seller", "Продавец", Role.SELLER);

    private static final List<PerformerResponse> PERFORMERS = List.of(
            new PerformerResponse(1L, "Дени (младший)"),
            new PerformerResponse(2L, "Дени (старший)"),
            new PerformerResponse(3L, "Тамерлан"));

    @BeforeEach
    void setUp() {
        wizard = new SaleWizard(api, capabilityService, simCardService, tariffService, saleService, performerService);
        session = new ChatSession();
    }

    @Test
    void simSaleWithTariffEndToEnd() {
        SimCardResponse sim = new SimCardResponse(5L, Operator.T2, "Т2", "8970100000000000001", null,
                SimStatus.IN_STOCK, Instant.now(), null);
        TariffResponse tariff = new TariffResponse(7L, Operator.T2, "Т2", "Безлимит", new BigDecimal("500.00"),
                1000, null, true, true);

        when(capabilityService.forOperator(Operator.T2)).thenReturn(List.of(
                new CapabilityResponse(Operator.T2, "Т2", ServiceType.SIM_SALE, "Продажа сим-карты", true, null),
                new CapabilityResponse(Operator.T2, "Т2", ServiceType.IMEI_REPLACEMENT, "Замена IMEI", false, null)));
        when(simCardService.findAvailable(eq(Operator.T2), isNull(), anyCollection(), anyInt())).thenReturn(List.of(sim));
        when(simCardService.findAvailable(eq(Operator.T2), eq("0001"), anyCollection(), anyInt())).thenReturn(List.of(sim));
        when(tariffService.listActive(Operator.T2)).thenReturn(List.of(tariff));
        when(tariffService.get(7L)).thenReturn(tariff);
        when(saleService.create(any(CreateSaleRequest.class), eq("seller"))).thenReturn(sale(42L, "500.00"));
        when(performerService.listActive()).thenReturn(PERFORMERS);
        when(performerService.get(3L)).thenReturn(new PerformerResponse(3L, "Тамерлан"));

        wizard.start(CHAT, null, session);
        assertThat(session.getStep()).isEqualTo(Step.SALE_OPERATOR);

        wizard.onCallback(CHAT, MSG, session, seller, "op:T2");
        assertThat(session.getStep()).isEqualTo(Step.SALE_SERVICE);

        wizard.onCallback(CHAT, MSG, session, seller, "sv:SIM_SALE");
        assertThat(session.getStep()).isEqualTo(Step.SALE_SIM);

        wizard.onText(CHAT, session, seller, "0001");
        assertThat(session.getStep()).isEqualTo(Step.SALE_TARIFF);
        assertThat(session.getSimCardId()).isEqualTo(5L);

        wizard.onCallback(CHAT, MSG, session, seller, "tf:7");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PRICE);

        wizard.onCallback(CHAT, MSG, session, seller, "pr:tariff");
        assertThat(session.getStep()).isEqualTo(Step.SALE_REVIEW);
        assertThat(session.getItems()).hasSize(1);

        wizard.onCallback(CHAT, MSG, session, seller, "pay:CARD");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PERFORMER);
        verify(saleService, never()).create(any(), any());

        wizard.onCallback(CHAT, MSG, session, seller, "pf:3");

        ArgumentCaptor<CreateSaleRequest> captor = ArgumentCaptor.forClass(CreateSaleRequest.class);
        verify(saleService).create(captor.capture(), eq("seller"));
        CreateSaleRequest request = captor.getValue();
        assertThat(request.paymentMethod()).isEqualTo(PaymentMethod.CARD);
        assertThat(request.performerName()).isEqualTo("Тамерлан");
        assertThat(request.items()).hasSize(1);
        SaleItemRequest item = request.items().get(0);
        assertThat(item.serviceType()).isEqualTo(ServiceType.SIM_SALE);
        assertThat(item.operator()).isEqualTo(Operator.T2);
        assertThat(item.simCardId()).isEqualTo(5L);
        assertThat(item.tariffId()).isEqualTo(7L);
        assertThat(item.price()).isEqualByComparingTo("500");

        assertThat(session.getStep()).isEqualTo(Step.IDLE);
        assertThat(session.getItems()).isEmpty();
    }

    @Test
    void serviceNotAllowedByMatrixIsNotAccepted() {
        when(capabilityService.forOperator(Operator.MTS)).thenReturn(List.of(
                new CapabilityResponse(Operator.MTS, "МТС", ServiceType.SIM_SALE, "Продажа сим-карты", true, null),
                new CapabilityResponse(Operator.MTS, "МТС", ServiceType.IMEI_REPLACEMENT, "Замена IMEI", false, null)));

        wizard.start(CHAT, null, session);
        wizard.onCallback(CHAT, MSG, session, seller, "op:MTS");
        wizard.onCallback(CHAT, MSG, session, seller, "sv:IMEI_REPLACEMENT");

        assertThat(session.getStep()).isEqualTo(Step.SALE_SERVICE);
        assertThat(session.getServiceType()).isNull();
    }

    @Test
    void serviceWithoutOperatorAndTypedPrice() {
        wizard.start(CHAT, null, session);
        wizard.onCallback(CHAT, MSG, session, seller, "op:NONE");
        wizard.onCallback(CHAT, MSG, session, seller, "sv:PHONE_REPAIR");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PRICE);

        wizard.onText(CHAT, session, seller, "abc");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PRICE);
        assertThat(session.getItems()).isEmpty();
        verify(api).sendMessage(eq(CHAT), contains("Не понял"), isNull());

        wizard.onText(CHAT, session, seller, "1 500");
        assertThat(session.getStep()).isEqualTo(Step.SALE_REVIEW);
        assertThat(session.getItems()).hasSize(1);
        assertThat(session.getItems().get(0).price()).isEqualByComparingTo("1500");

        wizard.onCallback(CHAT, MSG, session, seller, "undo");
        assertThat(session.getStep()).isEqualTo(Step.SALE_OPERATOR);
        assertThat(session.getItems()).isEmpty();

        verifyNoInteractions(capabilityService, saleService);
    }

    @Test
    void businessErrorKeepsDraftForCorrection() {
        when(saleService.create(any(CreateSaleRequest.class), eq("seller")))
                .thenThrow(new BusinessRuleException("Сим-карта недоступна"));
        when(performerService.listActive()).thenReturn(PERFORMERS);

        wizard.start(CHAT, null, session);
        wizard.onCallback(CHAT, MSG, session, seller, "op:NONE");
        wizard.onCallback(CHAT, MSG, session, seller, "sv:PAYMENT");
        wizard.onText(CHAT, session, seller, "1000");
        wizard.onCallback(CHAT, MSG, session, seller, "pay:CASH");
        wizard.onCallback(CHAT, MSG, session, seller, "pf:other");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PERFORMER_NAME);
        wizard.onText(CHAT, session, seller, "Иван");

        assertThat(session.getStep()).isEqualTo(Step.SALE_REVIEW);
        assertThat(session.getItems()).hasSize(1);
    }

    @Test
    void otherPerformerNameIsTyped() {
        when(performerService.listActive()).thenReturn(PERFORMERS);
        when(saleService.create(any(CreateSaleRequest.class), eq("seller"))).thenReturn(sale(7L, "300.00"));

        wizard.start(CHAT, null, session);
        wizard.onCallback(CHAT, MSG, session, seller, "op:NONE");
        wizard.onCallback(CHAT, MSG, session, seller, "sv:PHONE_SETUP");
        wizard.onText(CHAT, session, seller, "300");
        wizard.onCallback(CHAT, MSG, session, seller, "pay:TRANSFER");
        wizard.onCallback(CHAT, MSG, session, seller, "pf:other");

        wizard.onText(CHAT, session, seller, "x");
        assertThat(session.getStep()).isEqualTo(Step.SALE_PERFORMER_NAME);

        wizard.onText(CHAT, session, seller, "  Магомед  ");

        ArgumentCaptor<CreateSaleRequest> captor = ArgumentCaptor.forClass(CreateSaleRequest.class);
        verify(saleService).create(captor.capture(), eq("seller"));
        assertThat(captor.getValue().performerName()).isEqualTo("Магомед");
        assertThat(captor.getValue().paymentMethod()).isEqualTo(PaymentMethod.TRANSFER);
        assertThat(session.getStep()).isEqualTo(Step.IDLE);
    }

    @Test
    void staleButtonIsIgnored() {
        wizard.start(CHAT, null, session);

        wizard.onCallback(CHAT, MSG, session, seller, "pay:CARD");

        assertThat(session.getStep()).isEqualTo(Step.SALE_OPERATOR);
        verify(saleService, never()).create(any(), any());
    }

    private static SaleResponse sale(long id, String total) {
        return new SaleResponse(id, Instant.now(), SaleStatus.COMPLETED, PaymentMethod.CARD, new BigDecimal(total),
                "seller", null, null, null, List.of());
    }
}

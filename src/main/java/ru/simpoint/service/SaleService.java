package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.config.AppProperties;
import ru.simpoint.domain.Customer;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.Sale;
import ru.simpoint.domain.SaleItem;
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
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.CustomerRepository;
import ru.simpoint.repository.SaleRepository;
import ru.simpoint.repository.SimCardRepository;
import ru.simpoint.repository.TariffRepository;
import ru.simpoint.repository.UserAccountRepository;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
@RequiredArgsConstructor
public class SaleService {

    private final SaleRepository sales;
    private final SimCardRepository simCards;
    private final TariffRepository tariffs;
    private final CustomerRepository customers;
    private final UserAccountRepository users;
    private final CapabilityService capabilityService;
    private final AppProperties appProperties;

    @Transactional
    public SaleResponse create(CreateSaleRequest request, String employeeUsername) {
        UserAccount employee = users.findByUsername(employeeUsername)
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден: " + employeeUsername));
        Customer customer = request.customerId() == null ? null : customers.findById(request.customerId())
                .orElseThrow(() -> new NotFoundException("Клиент не найден: " + request.customerId()));

        Sale sale = new Sale();
        sale.setEmployee(employee);
        sale.setCustomer(customer);
        sale.setPaymentMethod(request.paymentMethod());
        sale.setStatus(SaleStatus.COMPLETED);
        if (request.performerName() != null && !request.performerName().isBlank()) {
            sale.setPerformerName(request.performerName().trim());
        }

        BigDecimal total = BigDecimal.ZERO;
        for (SaleItemRequest itemRequest : request.items()) {
            SaleItem item = buildItem(itemRequest);
            sale.addItem(item);
            total = total.add(item.getPrice());
        }
        sale.setTotal(total);

        return SaleResponse.from(sales.save(sale));
    }

    @Transactional(readOnly = true)
    public SaleResponse get(Long id) {
        return SaleResponse.from(findWithItems(id));
    }

    @Transactional(readOnly = true)
    public Page<SaleResponse> list(LocalDate from, LocalDate to, Pageable pageable) {
        if (to.isBefore(from)) {
            throw new BusinessRuleException("Дата окончания раньше даты начала");
        }
        ZoneId zone = ZoneId.of(appProperties.timezone());
        Instant start = from.atStartOfDay(zone).toInstant();
        Instant end = to.plusDays(1).atStartOfDay(zone).toInstant();
        return sales.findByCreatedAtGreaterThanEqualAndCreatedAtLessThanOrderByCreatedAtDesc(start, end, pageable)
                .map(SaleResponse::summary);
    }

    @Transactional
    public SaleResponse cancel(Long id, String reason) {
        Sale sale = findWithItems(id);
        if (sale.getStatus() == SaleStatus.CANCELLED) {
            throw new BusinessRuleException("Продажа " + id + " уже отменена");
        }
        sale.setStatus(SaleStatus.CANCELLED);
        sale.setCancelledAt(Instant.now());
        sale.setCancelReason(reason);
        for (SaleItem item : sale.getItems()) {
            SimCard sim = item.getSimCard();
            if (sim != null) {
                sim.setStatus(SimStatus.IN_STOCK);
                sim.setSoldAt(null);
            }
        }
        return SaleResponse.from(sale);
    }

    private Sale findWithItems(Long id) {
        return sales.findWithItemsById(id).orElseThrow(() -> new NotFoundException("Продажа не найдена: " + id));
    }

    private SaleItem buildItem(SaleItemRequest request) {
        ServiceType type = request.serviceType();
        Operator operator = request.operator();

        if (type.isOperatorBound()) {
            if (operator == null) {
                throw new BusinessRuleException("Для услуги «" + type.getDisplayName() + "» нужно указать оператора");
            }
            if (!capabilityService.isSupported(operator, type)) {
                throw new BusinessRuleException("Услуга «" + type.getDisplayName() + "» не выполняется для оператора "
                        + operator.getDisplayName());
            }
        }

        SaleItem item = new SaleItem();
        item.setServiceType(type);
        item.setOperator(operator);
        item.setDescription(request.description());

        Tariff tariff = null;
        if (request.tariffId() != null) {
            tariff = tariffs.findById(request.tariffId())
                    .orElseThrow(() -> new NotFoundException("Тариф не найден: " + request.tariffId()));
            if (!tariff.isActive()) {
                throw new BusinessRuleException("Тариф «" + tariff.getName() + "» неактивен");
            }
            if (operator != null && tariff.getOperator() != operator) {
                throw new BusinessRuleException("Тариф «" + tariff.getName() + "» принадлежит оператору "
                        + tariff.getOperator().getDisplayName());
            }
            item.setTariff(tariff);
        }

        if (type == ServiceType.SIM_SALE) {
            if (request.simCardId() == null) {
                throw new BusinessRuleException("Для продажи сим-карты укажите simCardId");
            }
            SimCard sim = simCards.findById(request.simCardId())
                    .orElseThrow(() -> new NotFoundException("Сим-карта не найдена: " + request.simCardId()));
            if (sim.getStatus() != SimStatus.IN_STOCK) {
                throw new BusinessRuleException("Сим-карта " + sim.getIccid() + " недоступна для продажи, статус "
                        + sim.getStatus());
            }
            if (sim.getOperator() != operator) {
                throw new BusinessRuleException("Сим-карта " + sim.getIccid() + " принадлежит оператору "
                        + sim.getOperator().getDisplayName());
            }
            sim.setStatus(SimStatus.SOLD);
            sim.setSoldAt(Instant.now());
            item.setSimCard(sim);
        } else if (request.simCardId() != null) {
            throw new BusinessRuleException("simCardId указывается только для продажи сим-карты");
        }

        BigDecimal price = request.price();
        if (price == null && tariff != null) {
            price = tariff.getMonthlyPrice();
        }
        if (price == null) {
            throw new BusinessRuleException("Укажите цену позиции «" + type.getDisplayName() + "»");
        }
        item.setPrice(price);
        return item;
    }
}

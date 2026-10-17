package ru.simpoint.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.stereotype.Component;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.PaymentMethod;
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
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.service.CapabilityService;
import ru.simpoint.service.PerformerService;
import ru.simpoint.service.SaleService;
import ru.simpoint.service.SimCardService;
import ru.simpoint.service.TariffService;
import ru.simpoint.service.TelegramAccountService.BotUser;
import ru.simpoint.telegram.ChatSession.DraftItem;
import ru.simpoint.telegram.ChatSession.Step;
import ru.simpoint.telegram.TelegramModels.InlineButton;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import static ru.simpoint.telegram.BotFormat.esc;
import static ru.simpoint.telegram.BotFormat.iccidTail;
import static ru.simpoint.telegram.BotFormat.money;
import static ru.simpoint.telegram.BotKeyboards.btn;
import static ru.simpoint.telegram.BotKeyboards.chunk;
import static ru.simpoint.telegram.BotKeyboards.inline;

/**
 * Пошаговое оформление продажи кнопками:
 * оператор → услуга (только разрешённые матрицей) → сим-карта → тариф → цена → чек → способ оплаты
 * → кто оформил.
 * В чеке может быть несколько позиций. Данные callback-кнопок имеют префикс «s:», он отрезается в BotRouter.
 */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
public class SaleWizard {

    static final int SIM_LIST_LIMIT = 8;

    private static final Set<ServiceType> TARIFF_SERVICES =
            EnumSet.of(ServiceType.SIM_SALE, ServiceType.MNP, ServiceType.TARIFF_CHANGE);

    private final TelegramApi api;
    private final CapabilityService capabilityService;
    private final SimCardService simCardService;
    private final TariffService tariffService;
    private final SaleService saleService;
    private final PerformerService performerService;

    public void start(long chatId, Long messageId, ChatSession s) {
        s.resetAll();
        askOperator(chatId, messageId, s);
    }

    public void onCallback(long chatId, long messageId, ChatSession s, BotUser user, String data) {
        int colon = data.indexOf(':');
        String action = colon < 0 ? data : data.substring(0, colon);
        String arg = colon < 0 ? "" : data.substring(colon + 1);

        switch (action) {
            case "x" -> cancel(chatId, messageId, s);
            case "back" -> back(chatId, messageId, s);
            case "op" -> {
                if (s.getStep() == Step.SALE_OPERATOR) {
                    s.setOperator("NONE".equals(arg) ? null : Operator.valueOf(arg));
                    askService(chatId, messageId, s);
                }
            }
            case "sv" -> {
                if (s.getStep() == Step.SALE_SERVICE) {
                    onService(chatId, messageId, s, ServiceType.valueOf(arg));
                }
            }
            case "sim" -> {
                if (s.getStep() == Step.SALE_SIM) {
                    onSimChosen(chatId, messageId, s, Long.parseLong(arg));
                }
            }
            case "tf" -> {
                if (s.getStep() == Step.SALE_TARIFF) {
                    onTariff(chatId, messageId, s, Long.parseLong(arg));
                }
            }
            case "pr" -> {
                if (s.getStep() == Step.SALE_PRICE) {
                    useTariffPrice(chatId, messageId, s);
                }
            }
            case "more" -> {
                if (s.getStep() == Step.SALE_REVIEW) {
                    askOperator(chatId, messageId, s);
                }
            }
            case "undo" -> {
                if (s.getStep() == Step.SALE_REVIEW) {
                    undo(chatId, messageId, s);
                }
            }
            case "pay" -> {
                if (s.getStep() == Step.SALE_REVIEW && !s.getItems().isEmpty()) {
                    s.setPaymentMethod(PaymentMethod.valueOf(arg));
                    askPerformer(chatId, messageId, s);
                }
            }
            case "pf" -> {
                if (s.getStep() != Step.SALE_PERFORMER) {
                    return;
                }
                if ("other".equals(arg)) {
                    askPerformerName(chatId, messageId, s);
                } else {
                    finish(chatId, messageId, s, user, performerService.get(Long.parseLong(arg)).name());
                }
            }
            default -> {
                // устаревшая или чужая кнопка: игнорируем
            }
        }
    }

    /** Текстовый ввод внутри мастера: хвост ICCID, цена или имя исполнителя. */
    public void onText(long chatId, ChatSession s, BotUser user, String text) {
        switch (s.getStep()) {
            case SALE_SIM -> onSimText(chatId, s, text);
            case SALE_PRICE -> onPriceText(chatId, s, text);
            case SALE_PERFORMER_NAME -> onPerformerName(chatId, s, user, text);
            default -> {
            }
        }
    }

    // ---------- шаги ----------

    private void askOperator(long chatId, Long messageId, ChatSession s) {
        s.resetItem();
        s.setStep(Step.SALE_OPERATOR);

        String text = s.getItems().isEmpty()
                ? "🧾 <b>Новая продажа</b>\n\nВыбери оператора:"
                : draftText(s) + "\n\nПозиция №" + (s.getItems().size() + 1) + ". Выбери оператора:";

        List<List<InlineButton>> rows = new ArrayList<>(chunk(BotKeyboards.operatorButtons("s:op:"), 3));
        rows.add(List.of(btn("🔧 Без оператора: ремонт, платежи и др.", "s:op:NONE")));
        rows.add(s.getItems().isEmpty()
                ? List.of(btn("✖️ Отмена", "s:x"))
                : List.of(btn("⬅️ К чеку", "s:back"), btn("✖️ Отмена", "s:x")));
        api.show(chatId, messageId, text, inline(rows));
    }

    private void askService(long chatId, Long messageId, ChatSession s) {
        s.setServiceType(null);
        s.setStep(Step.SALE_SERVICE);

        List<ServiceType> services = availableServices(s.getOperator());
        String text = itemHeader(s) + "\n\n" + (services.isEmpty()
                ? "По этому оператору точка сейчас не выполняет ни одной услуги."
                : "Выбери услугу:");

        List<List<InlineButton>> rows = new ArrayList<>();
        for (ServiceType type : services) {
            rows.add(List.of(btn(type.getDisplayName(), "s:sv:" + type.name())));
        }
        rows.add(navRow());
        api.show(chatId, messageId, text, inline(rows));
    }

    private void onService(long chatId, long messageId, ChatSession s, ServiceType type) {
        if (!availableServices(s.getOperator()).contains(type)) {
            askService(chatId, messageId, s);
            return;
        }
        s.setServiceType(type);
        if (type == ServiceType.SIM_SALE) {
            askSim(chatId, messageId, s, null);
        } else if (TARIFF_SERVICES.contains(type)) {
            askTariff(chatId, messageId, s);
        } else {
            askPrice(chatId, messageId, s);
        }
    }

    private void askSim(long chatId, Long messageId, ChatSession s, String suffix) {
        s.setStep(Step.SALE_SIM);
        List<SimCardResponse> sims = simCardService.findAvailable(
                s.getOperator(), suffix, s.draftSimIds(), SIM_LIST_LIMIT);

        StringBuilder text = new StringBuilder(itemHeader(s)).append("\n\n");
        if (sims.isEmpty()) {
            text.append(suffix == null
                    ? "На складе нет сим-карт этого оператора. Прими партию: «" + BotKeyboards.STOCK + "»."
                    : "Сим-карт с ICCID на <b>" + esc(suffix) + "</b> на складе нет. Пришли другие цифры.");
        } else if (suffix == null) {
            text.append("Выбери сим-карту или пришли последние цифры ICCID:");
        } else {
            text.append("Подходит несколько, выбери:");
        }

        List<InlineButton> buttons = sims.stream()
                .map(sim -> btn(iccidTail(sim.iccid()), "s:sim:" + sim.id()))
                .toList();
        List<List<InlineButton>> rows = new ArrayList<>(chunk(buttons, 2));
        rows.add(navRow());
        api.show(chatId, messageId, text.toString(), inline(rows));
    }

    private void onSimText(long chatId, ChatSession s, String text) {
        String digits = text.replaceAll("\\D", "");
        if (digits.length() < 3) {
            api.sendMessage(chatId, "Пришли хотя бы 3 последние цифры ICCID.", null);
            return;
        }
        List<SimCardResponse> sims = simCardService.findAvailable(
                s.getOperator(), digits, s.draftSimIds(), SIM_LIST_LIMIT);
        if (sims.size() == 1) {
            chooseSim(chatId, null, s, sims.get(0));
        } else {
            askSim(chatId, null, s, digits);
        }
    }

    private void onSimChosen(long chatId, long messageId, ChatSession s, long simId) {
        SimCardResponse sim = simCardService.get(simId);
        boolean usable = sim.status() == SimStatus.IN_STOCK
                && sim.operator() == s.getOperator()
                && !s.draftSimIds().contains(simId);
        if (!usable) {
            askSim(chatId, messageId, s, null);
            return;
        }
        chooseSim(chatId, messageId, s, sim);
    }

    private void chooseSim(long chatId, Long messageId, ChatSession s, SimCardResponse sim) {
        s.setSimCardId(sim.id());
        s.setIccid(sim.iccid());
        askTariff(chatId, messageId, s);
    }

    private void askTariff(long chatId, Long messageId, ChatSession s) {
        s.setTariffId(null);
        s.setTariffName(null);
        s.setTariffPrice(null);
        s.setStep(Step.SALE_TARIFF);

        List<List<InlineButton>> rows = new ArrayList<>();
        for (TariffResponse tariff : tariffService.listActive(s.getOperator())) {
            String label = tariff.monthlyPrice() == null
                    ? tariff.name()
                    : tariff.name() + " · " + money(tariff.monthlyPrice());
            rows.add(List.of(btn(label, "s:tf:" + tariff.id())));
        }
        rows.add(List.of(btn("Без тарифа", "s:tf:0")));
        rows.add(navRow());
        api.show(chatId, messageId, itemHeader(s) + "\n\nВыбери тариф:", inline(rows));
    }

    private void onTariff(long chatId, long messageId, ChatSession s, long tariffId) {
        if (tariffId != 0) {
            TariffResponse tariff = tariffService.get(tariffId);
            if (!tariff.active() || tariff.operator() != s.getOperator()) {
                askTariff(chatId, messageId, s);
                return;
            }
            s.setTariffId(tariff.id());
            s.setTariffName(tariff.name());
            s.setTariffPrice(tariff.monthlyPrice());
        }
        askPrice(chatId, messageId, s);
    }

    private void askPrice(long chatId, Long messageId, ChatSession s) {
        s.setStep(Step.SALE_PRICE);
        StringBuilder text = new StringBuilder(itemHeader(s)).append("\n\nНапиши цену в рублях");
        List<List<InlineButton>> rows = new ArrayList<>();
        if (s.getTariffPrice() != null) {
            text.append(" или оставь цену тарифа");
            rows.add(List.of(btn("✅ " + money(s.getTariffPrice()), "s:pr:tariff")));
        }
        text.append(':');
        rows.add(navRow());
        api.show(chatId, messageId, text.toString(), inline(rows));
    }

    private void onPriceText(long chatId, ChatSession s, String text) {
        BigDecimal price = BotFormat.parsePrice(text);
        if (price == null) {
            api.sendMessage(chatId, "Не понял сумму. Пример: <code>500</code> или <code>1499,90</code>", null);
            return;
        }
        addItem(chatId, null, s, price);
    }

    private void useTariffPrice(long chatId, long messageId, ChatSession s) {
        if (s.getTariffPrice() == null) {
            askPrice(chatId, messageId, s);
            return;
        }
        addItem(chatId, messageId, s, s.getTariffPrice());
    }

    private void addItem(long chatId, Long messageId, ChatSession s, BigDecimal price) {
        s.getItems().add(new DraftItem(s.getServiceType(), s.getOperator(), s.getSimCardId(), s.getIccid(),
                s.getTariffId(), s.getTariffName(), price));
        s.resetItem();
        showReview(chatId, messageId, s, null);
    }

    private void showReview(long chatId, Long messageId, ChatSession s, String warning) {
        s.setStep(Step.SALE_REVIEW);
        StringBuilder text = new StringBuilder(draftText(s));
        if (warning != null) {
            text.append("\n\n⚠️ ").append(esc(warning));
        }
        text.append("\n\nКак оплатили? Нажми способ оплаты, и продажа оформится.");

        List<List<InlineButton>> rows = List.of(
                List.of(btn("💵 Наличные", "s:pay:CASH"), btn("💳 Карта", "s:pay:CARD"),
                        btn("📲 Перевод", "s:pay:TRANSFER")),
                List.of(btn("➕ Ещё позиция", "s:more")),
                List.of(btn("↩️ Убрать последнюю", "s:undo"), btn("✖️ Отмена", "s:x"))
        );
        api.show(chatId, messageId, text.toString(), inline(rows));
    }

    private void undo(long chatId, long messageId, ChatSession s) {
        if (!s.getItems().isEmpty()) {
            s.getItems().remove(s.getItems().size() - 1);
        }
        if (s.getItems().isEmpty()) {
            askOperator(chatId, messageId, s);
        } else {
            showReview(chatId, messageId, s, null);
        }
    }

    private void askPerformer(long chatId, Long messageId, ChatSession s) {
        s.setStep(Step.SALE_PERFORMER);
        List<InlineButton> buttons = new ArrayList<>();
        for (PerformerResponse performer : performerService.listActive()) {
            buttons.add(btn(performer.name(), "s:pf:" + performer.id()));
        }
        buttons.add(btn("Другой", "s:pf:other"));
        List<List<InlineButton>> rows = new ArrayList<>(chunk(buttons, 2));
        rows.add(List.of(btn("⬅️ К чеку", "s:back"), btn("✖️ Отмена", "s:x")));
        api.show(chatId, messageId, draftText(s) + "\nОплата: " + BotFormat.payment(s.getPaymentMethod())
                + "\n\n<b>Кто оформил?</b>", inline(rows));
    }

    private void askPerformerName(long chatId, long messageId, ChatSession s) {
        s.setStep(Step.SALE_PERFORMER_NAME);
        api.show(chatId, messageId, "Напиши, кто оформил продажу (имя или фамилия):",
                inline(List.of(navRow())));
    }

    private void onPerformerName(long chatId, ChatSession s, BotUser user, String text) {
        String name = text.trim().replaceAll("\\s+", " ");
        if (name.length() < 2 || name.length() > 80) {
            api.sendMessage(chatId, "Имя должно быть от 2 до 80 символов. Напиши ещё раз:", null);
            return;
        }
        finish(chatId, null, s, user, name);
    }

    private void finish(long chatId, Long messageId, ChatSession s, BotUser user, String performerName) {
        if (s.getItems().isEmpty() || s.getPaymentMethod() == null) {
            askOperator(chatId, messageId, s);
            return;
        }
        PaymentMethod method = s.getPaymentMethod();
        List<SaleItemRequest> items = s.getItems().stream()
                .map(i -> new SaleItemRequest(i.serviceType(), i.operator(), i.simCardId(), i.tariffId(), null, i.price()))
                .toList();
        try {
            SaleResponse sale = saleService.create(
                    new CreateSaleRequest(null, method, items, performerName), user.username());
            String text = "✅ <b>Продажа №" + sale.id() + " оформлена</b>\n\n"
                    + itemLines(s.getItems())
                    + "\n\n<b>Итого: " + money(sale.total()) + "</b> · " + BotFormat.payment(method)
                    + "\nОформил: " + esc(performerName);
            s.resetAll();
            api.show(chatId, messageId, text, null);
        } catch (BusinessRuleException | NotFoundException e) {
            showReview(chatId, messageId, s, e.getMessage());
        } catch (ObjectOptimisticLockingFailureException e) {
            showReview(chatId, messageId, s,
                    "Эту сим-карту только что продали в другом чеке. Убери позицию и выбери другую.");
        }
    }

    private void cancel(long chatId, long messageId, ChatSession s) {
        s.resetAll();
        api.show(chatId, messageId, "Оформление отменено.", null);
    }

    private void back(long chatId, long messageId, ChatSession s) {
        switch (s.getStep()) {
            case SALE_OPERATOR -> {
                if (s.getItems().isEmpty()) {
                    cancel(chatId, messageId, s);
                } else {
                    showReview(chatId, messageId, s, null);
                }
            }
            case SALE_SERVICE -> askOperator(chatId, messageId, s);
            case SALE_SIM -> askService(chatId, messageId, s);
            case SALE_TARIFF -> {
                if (s.getServiceType() == ServiceType.SIM_SALE) {
                    s.setSimCardId(null);
                    s.setIccid(null);
                    askSim(chatId, messageId, s, null);
                } else {
                    askService(chatId, messageId, s);
                }
            }
            case SALE_PRICE -> {
                if (TARIFF_SERVICES.contains(s.getServiceType())) {
                    askTariff(chatId, messageId, s);
                } else {
                    askService(chatId, messageId, s);
                }
            }
            case SALE_PERFORMER -> showReview(chatId, messageId, s, null);
            case SALE_PERFORMER_NAME -> askPerformer(chatId, messageId, s);
            default -> {
            }
        }
    }

    // ---------- вспомогательное ----------

    private List<ServiceType> availableServices(Operator operator) {
        if (operator == null) {
            return Arrays.stream(ServiceType.values()).filter(t -> !t.isOperatorBound()).toList();
        }
        return capabilityService.forOperator(operator).stream()
                .filter(CapabilityResponse::supported)
                .map(CapabilityResponse::serviceType)
                .toList();
    }

    private static List<InlineButton> navRow() {
        return List.of(btn("⬅️ Назад", "s:back"), btn("✖️ Отмена", "s:x"));
    }

    private static String itemHeader(ChatSession s) {
        StringBuilder sb = new StringBuilder();
        if (!s.getItems().isEmpty()) {
            sb.append("Позиция №").append(s.getItems().size() + 1).append('\n');
        }
        sb.append("Оператор: <b>").append(esc(BotFormat.operator(s.getOperator()))).append("</b>");
        if (s.getServiceType() != null) {
            sb.append("\nУслуга: <b>").append(esc(s.getServiceType().getDisplayName())).append("</b>");
        }
        if (s.getIccid() != null) {
            sb.append("\nСим-карта: <b>").append(iccidTail(s.getIccid())).append("</b>");
        }
        if (s.getTariffName() != null) {
            sb.append("\nТариф: <b>").append(esc(s.getTariffName())).append("</b>");
        }
        return sb.toString();
    }

    private static String draftText(ChatSession s) {
        return "🧾 <b>Чек</b>\n\n" + itemLines(s.getItems()) + "\n\n<b>Итого: " + money(s.draftTotal()) + "</b>";
    }

    static String itemLines(List<DraftItem> items) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < items.size(); i++) {
            DraftItem item = items.get(i);
            if (i > 0) {
                sb.append('\n');
            }
            sb.append(i + 1).append(". ").append(esc(item.serviceType().getDisplayName()));
            if (item.operator() != null) {
                sb.append(" · ").append(esc(item.operator().getDisplayName()));
            }
            if (item.iccid() != null) {
                sb.append(" · ").append(iccidTail(item.iccid()));
            }
            if (item.tariffName() != null) {
                sb.append(" · ").append(esc(item.tariffName()));
            }
            sb.append(" — ").append(money(item.price()));
        }
        return sb.toString();
    }
}

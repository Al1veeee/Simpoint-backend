package ru.simpoint.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.simpoint.config.AppProperties;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SaleStatus;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.dto.ReceiveSimBatchRequest;
import ru.simpoint.dto.SaleResponse;
import ru.simpoint.dto.SalesReportResponse;
import ru.simpoint.dto.SimCardResponse;
import ru.simpoint.dto.StockSummaryResponse;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.service.PasswordService;
import ru.simpoint.service.ReportService;
import ru.simpoint.service.SaleService;
import ru.simpoint.service.SimCardService;
import ru.simpoint.service.TelegramAccountService;
import ru.simpoint.service.TelegramAccountService.BotUser;
import ru.simpoint.telegram.ChatSession.Step;
import ru.simpoint.telegram.TelegramModels.BotCommand;
import ru.simpoint.telegram.TelegramModels.CallbackQuery;
import ru.simpoint.telegram.TelegramModels.InlineButton;
import ru.simpoint.telegram.TelegramModels.Message;
import ru.simpoint.telegram.TelegramModels.Update;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import static ru.simpoint.telegram.BotFormat.esc;
import static ru.simpoint.telegram.BotFormat.money;
import static ru.simpoint.telegram.BotKeyboards.btn;
import static ru.simpoint.telegram.BotKeyboards.chunk;
import static ru.simpoint.telegram.BotKeyboards.inline;

/** Точка входа для всех событий бота: вход сотрудника, меню и разделы. */
@Component
@ConditionalOnProperty(prefix = "app.telegram", name = "enabled", havingValue = "true")
@RequiredArgsConstructor
@Slf4j
public class BotRouter {

    public static final List<BotCommand> COMMANDS = List.of(
            new BotCommand("sale", "Новая продажа"),
            new BotCommand("today", "Сводка за сегодня"),
            new BotCommand("stock", "Склад сим-карт"),
            new BotCommand("recent", "Последние продажи"),
            new BotCommand("link", "Войти: /link логин пароль"),
            new BotCommand("password", "Сменить пароль: /password новый_пароль"),
            new BotCommand("logout", "Отвязать аккаунт")
    );

    private static final String NOT_LINKED = """
            Это служебный бот SimPoint для сотрудников.

            Чтобы войти, отправь:
            <code>/link логин пароль</code>

            Сообщение с паролем я сразу удалю.""";

    private static final int MAX_LINK_FAILURES = 5;
    private static final Duration LINK_LOCK = Duration.ofMinutes(15);
    private static final int RECENT_LIMIT = 5;
    private static final DateTimeFormatter DAY = DateTimeFormatter.ofPattern("dd.MM");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final TelegramApi api;
    private final BotSessions sessions;
    private final SaleWizard saleWizard;
    private final TelegramAccountService accounts;
    private final ReportService reportService;
    private final SimCardService simCardService;
    private final SaleService saleService;
    private final PasswordService passwordService;
    private final AppProperties appProperties;

    private record LinkFailures(int count, Instant lockedUntil) {
    }

    private final Map<Long, LinkFailures> linkFailures = new ConcurrentHashMap<>();

    public void handle(Update update) {
        if (update.callbackQuery() != null) {
            handleCallback(update.callbackQuery());
        } else if (update.message() != null) {
            handleMessage(update.message());
        }
    }

    // ---------- сообщения ----------

    private void handleMessage(Message msg) {
        if (msg.chat() == null || msg.from() == null || !"private".equals(msg.chat().type())) {
            return;
        }
        long chatId = msg.chat().id();
        String text = msg.text() == null ? "" : msg.text().trim();

        if (text.startsWith("/link")) {
            link(chatId, msg, text);
            return;
        }

        Optional<BotUser> found = accounts.findByTelegramId(msg.from().id());
        if (found.isEmpty()) {
            api.sendMessage(chatId, NOT_LINKED, null);
            return;
        }
        BotUser user = found.get();
        ChatSession s = sessions.get(chatId);

        try {
            if (text.equals("/password") || text.startsWith("/password ")) {
                changePassword(chatId, msg, user, text);
                return;
            }
            switch (text) {
                case "/start", "/help", "/menu" -> api.sendMessage(chatId,
                        "Привет, " + esc(user.fullName()) + "! Выбери действие в меню 👇", BotKeyboards.mainMenu());
                case BotKeyboards.SALE, "/sale" -> saleWizard.start(chatId, null, s);
                case BotKeyboards.TODAY, "/today" -> report(chatId, null, false);
                case BotKeyboards.STOCK, "/stock" -> stock(chatId, null);
                case BotKeyboards.RECENT, "/recent" -> recent(chatId, null, user);
                case "/logout" -> {
                    accounts.unlink(msg.from().id());
                    sessions.remove(chatId);
                    api.sendMessage(chatId, "Аккаунт отвязан. Чтобы снова войти: <code>/link логин пароль</code>",
                            BotKeyboards.removeMenu());
                }
                default -> onFreeText(chatId, s, user, text);
            }
        } catch (BusinessRuleException | NotFoundException e) {
            api.sendMessage(chatId, "⚠️ " + esc(e.getMessage()), null);
        } catch (RuntimeException e) {
            log.error("Ошибка обработки сообщения в чате {}", chatId, e);
            api.sendMessage(chatId, "⚠️ Что-то пошло не так. Попробуй ещё раз.", null);
        }
    }

    private void onFreeText(long chatId, ChatSession s, BotUser user, String text) {
        switch (s.getStep()) {
            case SALE_SIM, SALE_PRICE, SALE_PERFORMER_NAME -> saleWizard.onText(chatId, s, user, text);
            case STOCK_ICCIDS -> receiveIccids(chatId, s, text);
            default -> api.sendMessage(chatId, "Выбери действие в меню 👇", BotKeyboards.mainMenu());
        }
    }

    private void link(long chatId, Message msg, String text) {
        api.deleteMessage(chatId, msg.messageId());

        LinkFailures failures = linkFailures.get(chatId);
        if (failures != null && failures.lockedUntil() != null && failures.lockedUntil().isAfter(Instant.now())) {
            api.sendMessage(chatId, "Слишком много неудачных попыток. Попробуй через 15 минут.", null);
            return;
        }

        String[] parts = text.split("\\s+");
        if (parts.length != 3) {
            api.sendMessage(chatId, "Формат: <code>/link логин пароль</code>", null);
            return;
        }

        Optional<BotUser> user = accounts.link(msg.from().id(), parts[1], parts[2]);
        if (user.isEmpty()) {
            int count = failures == null ? 1 : failures.count() + 1;
            linkFailures.put(chatId, count >= MAX_LINK_FAILURES
                    ? new LinkFailures(0, Instant.now().plus(LINK_LOCK))
                    : new LinkFailures(count, null));
            api.sendMessage(chatId, "Неверный логин или пароль.", null);
            return;
        }

        linkFailures.remove(chatId);
        sessions.get(chatId).resetAll();
        String role = user.get().isAdmin() ? "администратор" : "продавец";
        api.sendMessage(chatId, "✅ Готово, " + esc(user.get().fullName()) + ". Ты вошёл как " + role + ".",
                BotKeyboards.mainMenu());
    }

    /** /password новый — свой пароль; администратор: /password логин новый — пароль сотрудника. */
    private void changePassword(long chatId, Message msg, BotUser user, String text) {
        api.deleteMessage(chatId, msg.messageId());
        String[] parts = text.split("\\s+");
        if (parts.length == 2) {
            passwordService.change(user.username(), parts[1]);
            api.sendMessage(chatId, "✅ Пароль изменён. Сообщение с ним я удалил.", null);
        } else if (parts.length == 3 && user.isAdmin()) {
            passwordService.change(parts[1], parts[2]);
            api.sendMessage(chatId, "✅ Пароль для <code>" + esc(parts[1]) + "</code> изменён. Сообщение с ним я удалил.",
                    null);
        } else {
            api.sendMessage(chatId, user.isAdmin()
                    ? "Формат: <code>/password новый_пароль</code>\nили <code>/password логин новый_пароль</code>"
                    : "Формат: <code>/password новый_пароль</code>", null);
        }
    }

    // ---------- кнопки ----------

    private void handleCallback(CallbackQuery cq) {
        api.answerCallback(cq.id());
        if (cq.message() == null || cq.message().chat() == null || cq.from() == null) {
            return;
        }
        long chatId = cq.message().chat().id();
        long messageId = cq.message().messageId();
        String data = cq.data() == null ? "" : cq.data();

        Optional<BotUser> found = accounts.findByTelegramId(cq.from().id());
        if (found.isEmpty()) {
            api.show(chatId, messageId, NOT_LINKED, null);
            return;
        }
        BotUser user = found.get();
        ChatSession s = sessions.get(chatId);

        try {
            if (data.startsWith("s:")) {
                saleWizard.onCallback(chatId, messageId, s, user, data.substring(2));
            } else if (data.startsWith("rp:")) {
                report(chatId, messageId, "rp:month".equals(data));
            } else if (data.startsWith("st:")) {
                stockCallback(chatId, messageId, s, user, data.substring(3));
            } else if (data.startsWith("rc:")) {
                recentCallback(chatId, messageId, user, data.substring(3));
            }
        } catch (BusinessRuleException | NotFoundException e) {
            api.show(chatId, messageId, "⚠️ " + esc(e.getMessage()), null);
        } catch (RuntimeException e) {
            log.error("Ошибка обработки кнопки {} в чате {}", data, chatId, e);
            api.sendMessage(chatId, "⚠️ Что-то пошло не так. Попробуй ещё раз.", null);
        }
    }

    // ---------- сводка ----------

    private void report(long chatId, Long messageId, boolean monthly) {
        LocalDate today = LocalDate.now(zone());
        LocalDate from = monthly ? today.withDayOfMonth(1) : today;
        SalesReportResponse r = reportService.salesSummary(from, today);

        StringBuilder sb = new StringBuilder(monthly
                ? "📅 <b>С начала месяца</b> (" + from.format(DAY) + "–" + today.format(DAY) + ")"
                : "📊 <b>Сегодня</b>, " + today.format(DAY));
        sb.append("\n\nПродаж: <b>").append(r.salesCount()).append("</b> · Выручка: <b>")
                .append(money(r.totalRevenue())).append("</b>");

        if (!r.byOperator().isEmpty()) {
            sb.append("\n\n<b>По операторам</b>");
            r.byOperator().forEach(o -> sb.append("\n").append(esc(o.operatorName())).append(" — ")
                    .append(money(o.revenue())).append(" (").append(o.items()).append(")"));
        }
        if (!r.byServiceType().isEmpty()) {
            sb.append("\n\n<b>По услугам</b>");
            r.byServiceType().forEach(v -> sb.append("\n").append(esc(v.serviceName())).append(" — ")
                    .append(money(v.revenue())).append(" (").append(v.items()).append(")"));
        }
        if (!r.byPerformer().isEmpty()) {
            sb.append("\n\n<b>Кто оформлял</b>");
            r.byPerformer().forEach(e -> sb.append("\n").append(esc(e.name())).append(" — ")
                    .append(money(e.revenue())).append(" (").append(e.sales()).append(")"));
        }

        InlineButton toggle = monthly ? btn("📊 Сегодня", "rp:day") : btn("📅 С начала месяца", "rp:month");
        api.show(chatId, messageId, sb.toString(), inline(List.of(List.of(toggle))));
    }

    // ---------- склад ----------

    private static final int STOCK_PAGE_SIZE = 10;
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    private void stock(long chatId, Long messageId) {
        StringBuilder sb = new StringBuilder("📦 <b>Склад сим-карт</b>\n");
        for (StockSummaryResponse row : simCardService.stockSummary()) {
            sb.append("\n").append(esc(row.operatorName())).append(": <b>").append(row.inStock()).append("</b>");
            if (row.defective() > 0) {
                sb.append(", брак ").append(row.defective());
            }
        }
        api.show(chatId, messageId, sb.toString(), inline(List.of(
                List.of(btn("📥 Принять партию", "st:recv"), btn("📋 Список карт", "st:list")))));
    }

    private void stockCallback(long chatId, long messageId, ChatSession s, BotUser user, String data) {
        String[] p = data.split(":");
        switch (p[0]) {
            case "home" -> stock(chatId, messageId);
            case "recv" -> {
                s.resetAll();
                s.setStep(Step.STOCK_OPERATOR);
                List<List<InlineButton>> rows = new ArrayList<>(chunk(BotKeyboards.operatorButtons("st:op:"), 3));
                rows.add(List.of(btn("✖️ Отмена", "st:x")));
                api.show(chatId, messageId, "📥 <b>Приём партии</b>\n\nЧьи сим-карты пришли?", inline(rows));
            }
            case "op" -> {
                if (s.getStep() != Step.STOCK_OPERATOR) {
                    return;
                }
                Operator operator = Operator.valueOf(p[1]);
                s.setStockOperator(operator);
                s.setStep(Step.STOCK_ICCIDS);
                api.show(chatId, messageId, "📥 Пришли ICCID сим-карт <b>" + esc(operator.getDisplayName())
                                + "</b>, каждый с новой строки.\n\nУдобно вводить сканером штрихкодов в Telegram на компьютере.",
                        inline(List.of(List.of(btn("✖️ Отмена", "st:x")))));
            }
            case "x" -> {
                s.resetAll();
                api.show(chatId, messageId, "Приём партии отменён.", null);
            }
            case "list" -> {
                List<List<InlineButton>> rows = new ArrayList<>(chunk(
                        Arrays.stream(Operator.values())
                                .map(op -> btn(op.getDisplayName(), "st:ls:" + op.name() + ":0"))
                                .toList(), 3));
                rows.add(List.of(btn("⬅️ К складу", "st:home")));
                api.show(chatId, messageId, "📋 Чьи сим-карты показать?", inline(rows));
            }
            case "ls" -> simList(chatId, messageId, Operator.valueOf(p[1]), Integer.parseInt(p[2]), null);
            case "c" -> simCard(chatId, messageId, user, Long.parseLong(p[1]), Integer.parseInt(p[2]), null);
            case "def" -> {
                simCardService.markDefective(Long.parseLong(p[1]));
                simCard(chatId, messageId, user, Long.parseLong(p[1]), Integer.parseInt(p[2]), "Помечена как брак.");
            }
            case "ret" -> {
                simCardService.returnToStock(Long.parseLong(p[1]));
                simCard(chatId, messageId, user, Long.parseLong(p[1]), Integer.parseInt(p[2]), "Возвращена на склад.");
            }
            case "del" -> {
                if (!user.isAdmin()) {
                    return;
                }
                SimCardResponse sim = simCardService.get(Long.parseLong(p[1]));
                api.show(chatId, messageId, "Удалить сим-карту <code>" + esc(sim.iccid()) + "</code>?\n\n"
                                + "Удаляй только ошибочно внесённые карты. Если карта испорчена, лучше пометить её как брак.",
                        inline(List.of(List.of(
                                btn("🗑 Да, удалить", "st:dely:" + sim.id() + ":" + p[2]),
                                btn("Нет", "st:c:" + sim.id() + ":" + p[2])))));
            }
            case "dely" -> {
                if (!user.isAdmin()) {
                    return;
                }
                SimCardResponse sim = simCardService.get(Long.parseLong(p[1]));
                simCardService.delete(sim.id());
                simList(chatId, messageId, sim.operator(), Integer.parseInt(p[2]),
                        "🗑 Карта " + BotFormat.iccidTail(sim.iccid()) + " удалена.");
            }
            default -> {
            }
        }
    }

    private void simList(long chatId, long messageId, Operator operator, int page, String notice) {
        Page<SimCardResponse> cards = simCardService.listUnsold(operator, PageRequest.of(page, STOCK_PAGE_SIZE));
        if (cards.isEmpty() && page > 0) {
            simList(chatId, messageId, operator, page - 1, notice);
            return;
        }

        StringBuilder sb = new StringBuilder();
        if (notice != null) {
            sb.append(notice).append("\n\n");
        }
        sb.append("📋 <b>").append(esc(operator.getDisplayName())).append("</b>: не проданных карт ")
                .append(cards.getTotalElements());
        if (cards.isEmpty()) {
            sb.append("\n\nСписок пуст.");
        } else {
            sb.append("\n⚠️ отмечены карты с пометкой «брак». Выбери карту:");
        }

        List<InlineButton> buttons = cards.getContent().stream()
                .map(sim -> btn((sim.status() == SimStatus.DEFECTIVE ? "⚠️ " : "") + BotFormat.iccidTail(sim.iccid()),
                        "st:c:" + sim.id() + ":" + page))
                .toList();
        List<List<InlineButton>> rows = new ArrayList<>(chunk(buttons, 2));

        List<InlineButton> nav = new ArrayList<>();
        if (cards.hasPrevious()) {
            nav.add(btn("◀️", "st:ls:" + operator.name() + ":" + (page - 1)));
        }
        if (cards.getTotalPages() > 1) {
            nav.add(btn((page + 1) + " / " + cards.getTotalPages(), "st:ls:" + operator.name() + ":" + page));
        }
        if (cards.hasNext()) {
            nav.add(btn("▶️", "st:ls:" + operator.name() + ":" + (page + 1)));
        }
        if (!nav.isEmpty()) {
            rows.add(nav);
        }
        rows.add(List.of(btn("⬅️ Операторы", "st:list"), btn("📦 Склад", "st:home")));
        api.show(chatId, messageId, sb.toString(), inline(rows));
    }

    private void simCard(long chatId, long messageId, BotUser user, long simId, int page, String notice) {
        SimCardResponse sim = simCardService.get(simId);
        StringBuilder sb = new StringBuilder();
        if (notice != null) {
            sb.append("✅ ").append(notice).append("\n\n");
        }
        sb.append("Сим-карта <code>").append(esc(sim.iccid())).append("</code>")
                .append("\nОператор: <b>").append(esc(sim.operatorName())).append("</b>")
                .append("\nСтатус: <b>").append(statusName(sim.status())).append("</b>")
                .append("\nПринята: ").append(sim.receivedAt().atZone(zone()).format(DATE));

        List<List<InlineButton>> rows = new ArrayList<>();
        List<InlineButton> actions = new ArrayList<>();
        if (sim.status() == SimStatus.IN_STOCK) {
            actions.add(btn("⚠️ Брак", "st:def:" + simId + ":" + page));
        } else if (sim.status() == SimStatus.DEFECTIVE) {
            actions.add(btn("↩️ Вернуть на склад", "st:ret:" + simId + ":" + page));
        }
        if (user.isAdmin() && sim.status() != SimStatus.SOLD) {
            actions.add(btn("🗑 Удалить", "st:del:" + simId + ":" + page));
        }
        if (!actions.isEmpty()) {
            rows.add(actions);
        }
        rows.add(List.of(btn("⬅️ К списку", "st:ls:" + sim.operator().name() + ":" + page)));
        api.show(chatId, messageId, sb.toString(), inline(rows));
    }

    private static String statusName(SimStatus status) {
        return switch (status) {
            case IN_STOCK -> "на складе";
            case SOLD -> "продана";
            case DEFECTIVE -> "брак";
        };
    }

    private void receiveIccids(long chatId, ChatSession s, String text) {
        List<String> codes = Arrays.stream(text.split("[\\s,;]+"))
                .map(code -> code.replaceAll("\\D", ""))
                .filter(code -> !code.isEmpty())
                .distinct()
                .toList();
        List<String> invalid = codes.stream().filter(code -> code.length() < 18 || code.length() > 22).toList();
        if (codes.isEmpty() || !invalid.isEmpty()) {
            api.sendMessage(chatId, "Это не похоже на ICCID (нужно 18–22 цифры)"
                    + (invalid.isEmpty() ? "" : ": " + esc(String.join(", ", invalid)))
                    + ".\nПришли список ещё раз.", null);
            return;
        }
        Operator operator = s.getStockOperator();
        List<SimCardResponse> received = simCardService.receiveBatch(new ReceiveSimBatchRequest(operator, codes));
        s.resetAll();
        api.sendMessage(chatId, "✅ Принято сим-карт " + esc(operator.getDisplayName()) + ": <b>"
                + received.size() + "</b>", BotKeyboards.mainMenu());
    }

    // ---------- последние продажи и отмена ----------

    private void recent(long chatId, Long messageId, BotUser user) {
        LocalDate today = LocalDate.now(zone());
        List<SaleResponse> sales = saleService.list(today, today, PageRequest.of(0, RECENT_LIMIT)).getContent();
        if (sales.isEmpty()) {
            api.show(chatId, messageId, "🕘 Сегодня продаж ещё нет.", null);
            return;
        }

        StringBuilder sb = new StringBuilder("🕘 <b>Последние продажи за сегодня</b>\n");
        List<InlineButton> buttons = new ArrayList<>();
        for (SaleResponse sale : sales) {
            sb.append("\n№").append(sale.id()).append(" · ")
                    .append(sale.createdAt().atZone(zone()).format(TIME)).append(" · ")
                    .append(money(sale.total())).append(" · ")
                    .append(BotFormat.payment(sale.paymentMethod())).append(" · ")
                    .append(esc(sale.performerName() != null ? sale.performerName() : sale.employee()));
            if (sale.status() == SaleStatus.CANCELLED) {
                sb.append(" · ❌ отменена");
            } else if (canCancel(user, sale)) {
                buttons.add(btn("Отменить №" + sale.id(), "rc:ask:" + sale.id()));
            }
        }
        api.show(chatId, messageId, sb.toString(), buttons.isEmpty() ? null : inline(chunk(buttons, 2)));
    }

    private void recentCallback(long chatId, long messageId, BotUser user, String data) {
        if ("no".equals(data)) {
            recent(chatId, messageId, user);
            return;
        }
        boolean confirmed = data.startsWith("yes:");
        long saleId = Long.parseLong(data.substring(data.indexOf(':') + 1));
        SaleResponse sale = saleService.get(saleId);

        if (sale.status() == SaleStatus.CANCELLED) {
            api.show(chatId, messageId, "Продажа №" + saleId + " уже отменена.", null);
            return;
        }
        if (!canCancel(user, sale)) {
            api.show(chatId, messageId, "Отменить можно только свою продажу. Обратись к администратору.", null);
            return;
        }

        if (!confirmed) {
            String lines = sale.items().stream()
                    .map(i -> "• " + esc(i.serviceName()) + " — " + money(i.price()))
                    .reduce((a, b) -> a + "\n" + b)
                    .orElse("");
            api.show(chatId, messageId, "Отменить продажу <b>№" + saleId + "</b> на " + money(sale.total()) + "?\n\n"
                            + lines + "\n\nПроданные сим-карты вернутся на склад.",
                    inline(List.of(List.of(btn("Да, отменить", "rc:yes:" + saleId), btn("Нет", "rc:no")))));
            return;
        }

        saleService.cancel(saleId, "Отменено в Telegram: " + user.username());
        api.show(chatId, messageId, "↩️ Продажа №" + saleId + " отменена. Сим-карты вернулись на склад.", null);
    }

    private static boolean canCancel(BotUser user, SaleResponse sale) {
        return user.isAdmin() || user.username().equals(sale.employee());
    }

    private ZoneId zone() {
        return ZoneId.of(appProperties.timezone());
    }
}

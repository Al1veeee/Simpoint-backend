package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.repository.UserAccountRepository;

import java.util.Optional;

/** Привязка сотрудника к Telegram-аккаунту для служебного бота. */
@Service
@RequiredArgsConstructor
public class TelegramAccountService {

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    public record BotUser(String username, String fullName, Role role) {

        public boolean isAdmin() {
            return role == Role.ADMIN;
        }
    }

    @Transactional(readOnly = true)
    public Optional<BotUser> findByTelegramId(long telegramId) {
        return users.findByTelegramId(telegramId)
                .filter(UserAccount::isEnabled)
                .map(TelegramAccountService::toBotUser);
    }

    @Transactional
    public Optional<BotUser> link(long telegramId, String username, String rawPassword) {
        Optional<UserAccount> account = users.findByUsername(username)
                .filter(UserAccount::isEnabled)
                .filter(UserAccount::hasUsablePassword)
                .filter(u -> passwordEncoder.matches(rawPassword, u.getPasswordHash()));
        if (account.isEmpty()) {
            return Optional.empty();
        }
        UserAccount user = account.get();
        users.findByTelegramId(telegramId)
                .filter(other -> !other.getId().equals(user.getId()))
                .ifPresent(other -> {
                    other.setTelegramId(null);
                    users.saveAndFlush(other);
                });
        user.setTelegramId(telegramId);
        return Optional.of(toBotUser(user));
    }

    @Transactional
    public void unlink(long telegramId) {
        users.findByTelegramId(telegramId).ifPresent(u -> u.setTelegramId(null));
    }

    private static BotUser toBotUser(UserAccount u) {
        return new BotUser(u.getUsername(), u.getFullName(), u.getRole());
    }
}

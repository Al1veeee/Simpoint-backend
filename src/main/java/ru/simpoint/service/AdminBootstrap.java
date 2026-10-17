package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.config.BootstrapProperties;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.repository.UserAccountRepository;

import java.util.Optional;

/**
 * Готовит администратора при запуске, чтобы в репозитории не было общих паролей.
 * <p>
 * Если задан ADMIN_PASSWORD: создаёт администратора, когда его нет, или задаёт пароль,
 * когда текущий отключён. Рабочий пароль, сменённый через бота, не перезаписывается.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AdminBootstrap implements ApplicationRunner {

    private final BootstrapProperties properties;
    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String username = properties.adminUsername();
        String password = properties.adminPassword();

        if (password == null || password.isBlank()) {
            if (!users.existsByRoleAndEnabledTrueAndPasswordHashStartingWith(Role.ADMIN, "$2")) {
                log.warn("Нет администратора с рабочим паролем. Задайте ADMIN_PASSWORD при запуске "
                        + "или смените пароль командой /password в боте.");
            }
            return;
        }

        try {
            PasswordService.validate(password);
        } catch (BusinessRuleException e) {
            throw new IllegalStateException("ADMIN_PASSWORD не подходит: " + e.getMessage(), e);
        }

        Optional<UserAccount> existing = users.findByUsername(username);
        if (existing.isEmpty()) {
            UserAccount admin = new UserAccount();
            admin.setUsername(username);
            admin.setFullName("Администратор");
            admin.setRole(Role.ADMIN);
            admin.setPasswordHash(passwordEncoder.encode(password));
            users.save(admin);
            log.info("Создан администратор {}", username);
        } else if (!existing.get().hasUsablePassword()) {
            existing.get().setPasswordHash(passwordEncoder.encode(password));
            log.info("Администратору {} задан пароль из ADMIN_PASSWORD", username);
        } else {
            log.info("ADMIN_PASSWORD не применён: у {} уже есть пароль", username);
        }
    }
}

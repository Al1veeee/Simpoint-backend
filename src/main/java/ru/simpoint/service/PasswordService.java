package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.UserAccountRepository;

import java.nio.charset.StandardCharsets;

/** Смена паролей сотрудников и общие правила для пароля. */
@Service
@RequiredArgsConstructor
public class PasswordService {

    public static final int MIN_LENGTH = 8;
    /** BCrypt учитывает только первые 72 байта, Spring Security отклоняет более длинные пароли. */
    private static final int MAX_BYTES = 72;

    private final UserAccountRepository users;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void change(String username, String rawPassword) {
        validate(rawPassword);
        UserAccount user = users.findByUsername(username)
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + username));
        user.setPasswordHash(passwordEncoder.encode(rawPassword));
    }

    public static void validate(String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MIN_LENGTH) {
            throw new BusinessRuleException("Пароль должен быть не короче " + MIN_LENGTH + " символов");
        }
        if (rawPassword.chars().anyMatch(Character::isWhitespace)) {
            throw new BusinessRuleException("Пароль не должен содержать пробелов");
        }
        if (rawPassword.getBytes(StandardCharsets.UTF_8).length > MAX_BYTES) {
            throw new BusinessRuleException("Пароль слишком длинный");
        }
    }
}

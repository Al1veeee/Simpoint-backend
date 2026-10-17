package ru.simpoint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.UserAccount;

import java.util.Optional;

public interface UserAccountRepository extends JpaRepository<UserAccount, Long> {

    Optional<UserAccount> findByUsername(String username);

    Optional<UserAccount> findByTelegramId(Long telegramId);

    boolean existsByRoleAndEnabledTrueAndPasswordHashStartingWith(Role role, String hashPrefix);
}

package ru.simpoint.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.simpoint.config.BootstrapProperties;
import ru.simpoint.domain.Role;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.repository.UserAccountRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminBootstrapTest {

    @Mock
    private UserAccountRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    private AdminBootstrap bootstrap(String password) {
        return new AdminBootstrap(new BootstrapProperties(null, password), users, encoder);
    }

    @Test
    void createsAdminWhenMissing() {
        when(users.findByUsername("admin")).thenReturn(Optional.empty());

        bootstrap("first-admin-pass").run(null);

        ArgumentCaptor<UserAccount> saved = ArgumentCaptor.forClass(UserAccount.class);
        verify(users).save(saved.capture());
        assertThat(saved.getValue().getRole()).isEqualTo(Role.ADMIN);
        assertThat(encoder.matches("first-admin-pass", saved.getValue().getPasswordHash())).isTrue();
    }

    @Test
    void setsPasswordWhenCurrentOneIsDisabled() {
        UserAccount admin = account("!disabled");
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        bootstrap("first-admin-pass").run(null);

        assertThat(encoder.matches("first-admin-pass", admin.getPasswordHash())).isTrue();
    }

    @Test
    void doesNotOverwriteWorkingPassword() {
        String hash = encoder.encode("changed-in-bot");
        UserAccount admin = account(hash);
        when(users.findByUsername("admin")).thenReturn(Optional.of(admin));

        bootstrap("first-admin-pass").run(null);

        assertThat(admin.getPasswordHash()).isEqualTo(hash);
        verify(users, never()).save(any());
    }

    @Test
    void weakAdminPasswordStopsStartup() {
        assertThatThrownBy(() -> bootstrap("123").run(null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ADMIN_PASSWORD");
    }

    @Test
    void withoutPasswordNothingIsChanged() {
        when(users.existsByRoleAndEnabledTrueAndPasswordHashStartingWith(Role.ADMIN, "$2")).thenReturn(false);

        bootstrap("").run(null);

        verify(users, never()).findByUsername(any());
        verify(users, never()).save(any());
    }

    private static UserAccount account(String hash) {
        UserAccount admin = new UserAccount();
        admin.setUsername("admin");
        admin.setRole(Role.ADMIN);
        admin.setPasswordHash(hash);
        return admin;
    }
}

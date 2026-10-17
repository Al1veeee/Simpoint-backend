package ru.simpoint.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.UserAccountRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PasswordServiceTest {

    @Mock
    private UserAccountRepository users;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);

    @Test
    void changeStoresBcryptHash() {
        UserAccount user = new UserAccount();
        user.setUsername("seller");
        user.setPasswordHash("!disabled");
        when(users.findByUsername("seller")).thenReturn(Optional.of(user));

        new PasswordService(users, encoder).change("seller", "new-secret-1");

        assertThat(user.hasUsablePassword()).isTrue();
        assertThat(encoder.matches("new-secret-1", user.getPasswordHash())).isTrue();
    }

    @Test
    void unknownUserIsReported() {
        when(users.findByUsername("ghost")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> new PasswordService(users, encoder).change("ghost", "new-secret-1"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void weakPasswordsAreRejected() {
        assertThatThrownBy(() -> PasswordService.validate("short")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordService.validate("with space")).isInstanceOf(BusinessRuleException.class);
        assertThatThrownBy(() -> PasswordService.validate("я".repeat(40))).isInstanceOf(BusinessRuleException.class);
        assertThatCode(() -> PasswordService.validate("Str0ng-pass")).doesNotThrowAnyException();
    }
}

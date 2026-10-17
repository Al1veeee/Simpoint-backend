package ru.simpoint.security;

import org.junit.jupiter.api.Test;
import ru.simpoint.config.JwtProperties;
import ru.simpoint.domain.Role;

import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    private final JwtService jwtService = new JwtService(
            new JwtProperties("test-secret-test-secret-test-secret-1234", 60));

    @Test
    void issuedTokenCanBeParsedBack() {
        JwtService.IssuedToken issued = jwtService.issue("seller", Role.SELLER);

        assertThat(issued.expiresAt()).isAfter(Instant.now());
        assertThat(jwtService.extractUsername(issued.token())).contains("seller");
    }

    @Test
    void tamperedTokenIsRejected() {
        String token = jwtService.issue("seller", Role.SELLER).token();
        String tampered = token.substring(0, token.length() - 3) + "abc";

        assertThat(jwtService.extractUsername(tampered)).isEmpty();
    }

    @Test
    void tokenSignedWithAnotherSecretIsRejected() {
        JwtService other = new JwtService(new JwtProperties("another-secret-another-secret-another-1", 60));
        String token = other.issue("admin", Role.ADMIN).token();

        assertThat(jwtService.extractUsername(token)).isEmpty();
    }
}

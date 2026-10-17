package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.UserAccount;
import ru.simpoint.dto.LoginRequest;
import ru.simpoint.dto.LoginResponse;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.UserAccountRepository;
import ru.simpoint.security.JwtService;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final UserAccountRepository users;
    private final JwtService jwtService;

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        UserAccount user = users.findByUsername(request.username())
                .orElseThrow(() -> new NotFoundException("Пользователь не найден: " + request.username()));
        JwtService.IssuedToken token = jwtService.issue(user.getUsername(), user.getRole());
        return new LoginResponse(token.token(), token.expiresAt(), user.getUsername(), user.getFullName(), user.getRole());
    }
}

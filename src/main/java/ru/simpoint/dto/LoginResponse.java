package ru.simpoint.dto;

import ru.simpoint.domain.Role;

import java.time.Instant;

public record LoginResponse(
        String token,
        Instant expiresAt,
        String username,
        String fullName,
        Role role
) {
}

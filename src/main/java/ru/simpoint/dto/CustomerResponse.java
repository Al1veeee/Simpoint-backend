package ru.simpoint.dto;

import ru.simpoint.domain.Customer;
import ru.simpoint.domain.Language;

import java.time.Instant;

public record CustomerResponse(
        Long id,
        String fullName,
        String phone,
        Language preferredLanguage,
        Instant createdAt
) {
    public static CustomerResponse from(Customer c) {
        return new CustomerResponse(c.getId(), c.getFullName(), c.getPhone(), c.getPreferredLanguage(), c.getCreatedAt());
    }
}

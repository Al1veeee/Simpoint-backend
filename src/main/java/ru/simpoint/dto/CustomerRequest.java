package ru.simpoint.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.simpoint.domain.Language;

public record CustomerRequest(
        @NotBlank @Size(max = 120) String fullName,
        @Pattern(regexp = "\\+?\\d{10,15}", message = "Телефон: от 10 до 15 цифр, можно с +") String phone,
        Language preferredLanguage
) {
}

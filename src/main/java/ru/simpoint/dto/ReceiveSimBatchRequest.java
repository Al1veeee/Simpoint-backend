package ru.simpoint.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import ru.simpoint.domain.Operator;

import java.util.List;

public record ReceiveSimBatchRequest(
        @NotNull Operator operator,
        @NotEmpty List<@Pattern(regexp = "\\d{18,22}", message = "ICCID: от 18 до 22 цифр") String> iccids
) {
}

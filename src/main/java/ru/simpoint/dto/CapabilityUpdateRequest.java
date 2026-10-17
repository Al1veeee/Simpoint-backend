package ru.simpoint.dto;

import jakarta.validation.constraints.Size;

public record CapabilityUpdateRequest(
        boolean supported,
        @Size(max = 255) String note
) {
}

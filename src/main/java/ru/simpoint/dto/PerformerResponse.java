package ru.simpoint.dto;

import ru.simpoint.domain.Performer;

public record PerformerResponse(Long id, String name) {

    public static PerformerResponse from(Performer p) {
        return new PerformerResponse(p.getId(), p.getName());
    }
}

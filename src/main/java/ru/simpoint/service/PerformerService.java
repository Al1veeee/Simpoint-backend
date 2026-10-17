package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.dto.PerformerResponse;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.PerformerRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PerformerService {

    private final PerformerRepository performers;

    @Transactional(readOnly = true)
    public List<PerformerResponse> listActive() {
        return performers.findByActiveTrueOrderBySortOrderAscIdAsc().stream()
                .map(PerformerResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public PerformerResponse get(Long id) {
        return performers.findById(id)
                .map(PerformerResponse::from)
                .orElseThrow(() -> new NotFoundException("Сотрудник не найден: " + id));
    }
}

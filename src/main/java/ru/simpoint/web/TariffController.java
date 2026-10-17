package ru.simpoint.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.simpoint.domain.Operator;
import ru.simpoint.dto.TariffRequest;
import ru.simpoint.dto.TariffResponse;
import ru.simpoint.service.TariffService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/tariffs")
@RequiredArgsConstructor
@Tag(name = "Tariffs", description = "Тарифы операторов")
public class TariffController {

    private final TariffService tariffService;

    @GetMapping
    @Operation(summary = "Активные тарифы, опционально по оператору")
    public List<TariffResponse> list(@RequestParam(required = false) Operator operator) {
        return tariffService.listActive(operator);
    }

    @GetMapping("/{id}")
    public TariffResponse get(@PathVariable Long id) {
        return tariffService.get(id);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ADMIN')")
    public TariffResponse create(@Valid @RequestBody TariffRequest request) {
        return tariffService.create(request);
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public TariffResponse update(@PathVariable Long id, @Valid @RequestBody TariffRequest request) {
        return tariffService.update(id, request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Деактивировать тариф (история продаж сохраняется)")
    public void deactivate(@PathVariable Long id) {
        tariffService.deactivate(id);
    }
}

package ru.simpoint.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.simpoint.dto.CancelSaleRequest;
import ru.simpoint.dto.CreateSaleRequest;
import ru.simpoint.dto.SaleResponse;
import ru.simpoint.service.SaleService;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/sales")
@RequiredArgsConstructor
@Tag(name = "Sales", description = "Продажи и услуги")
public class SaleController {

    private final SaleService saleService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Оформить продажу: позиции проверяются по матрице услуг, сим-карты списываются со склада")
    public SaleResponse create(@Valid @RequestBody CreateSaleRequest request, Authentication authentication) {
        return saleService.create(request, authentication.getName());
    }

    @GetMapping("/{id}")
    public SaleResponse get(@PathVariable Long id) {
        return saleService.get(id);
    }

    @GetMapping
    @Operation(summary = "Продажи за период (даты включительно)")
    public Page<SaleResponse> list(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
                                   @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
                                   @PageableDefault(size = 20) Pageable pageable) {
        return saleService.list(from, to, pageable);
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Отменить продажу, сим-карты возвращаются на склад")
    public SaleResponse cancel(@PathVariable Long id, @Valid @RequestBody CancelSaleRequest request) {
        return saleService.cancel(id, request.reason());
    }
}

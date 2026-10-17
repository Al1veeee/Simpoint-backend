package ru.simpoint.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.SimStatus;
import ru.simpoint.dto.ReceiveSimBatchRequest;
import ru.simpoint.dto.SimCardResponse;
import ru.simpoint.dto.StockSummaryResponse;
import ru.simpoint.service.SimCardService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/sim-cards")
@RequiredArgsConstructor
@Tag(name = "SIM cards", description = "Склад сим-карт")
public class SimCardController {

    private final SimCardService simCardService;

    @PostMapping("/batch")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Оприходовать партию сим-карт")
    public List<SimCardResponse> receiveBatch(@Valid @RequestBody ReceiveSimBatchRequest request) {
        return simCardService.receiveBatch(request);
    }

    @GetMapping
    @Operation(summary = "Поиск по оператору и статусу с пагинацией")
    public Page<SimCardResponse> search(@RequestParam(required = false) Operator operator,
                                        @RequestParam(required = false) SimStatus status,
                                        @PageableDefault(size = 20) Pageable pageable) {
        return simCardService.search(operator, status, pageable);
    }

    @GetMapping("/stock")
    @Operation(summary = "Остатки по операторам")
    public List<StockSummaryResponse> stock() {
        return simCardService.stockSummary();
    }

    @GetMapping("/{id}")
    public SimCardResponse get(@PathVariable Long id) {
        return simCardService.get(id);
    }

    @PatchMapping("/{id}/defective")
    @Operation(summary = "Пометить сим-карту как бракованную")
    public SimCardResponse markDefective(@PathVariable Long id) {
        return simCardService.markDefective(id);
    }
}

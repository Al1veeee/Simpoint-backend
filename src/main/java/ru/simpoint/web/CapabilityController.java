package ru.simpoint.web;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.ServiceType;
import ru.simpoint.dto.CapabilityResponse;
import ru.simpoint.dto.CapabilityUpdateRequest;
import ru.simpoint.service.CapabilityService;

import java.util.List;

@RestController
@RequestMapping("/api/v1/capabilities")
@RequiredArgsConstructor
@Tag(name = "Capabilities", description = "Какие услуги точка выполняет по каждому оператору")
public class CapabilityController {

    private final CapabilityService capabilityService;

    @GetMapping
    @Operation(summary = "Полная матрица оператор × услуга")
    public List<CapabilityResponse> all() {
        return capabilityService.all();
    }

    @GetMapping("/{operator}")
    public List<CapabilityResponse> forOperator(@PathVariable Operator operator) {
        return capabilityService.forOperator(operator);
    }

    @PutMapping("/{operator}/{serviceType}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Включить или выключить услугу для оператора")
    public CapabilityResponse update(@PathVariable Operator operator,
                                     @PathVariable ServiceType serviceType,
                                     @Valid @RequestBody CapabilityUpdateRequest request) {
        return capabilityService.update(operator, serviceType, request);
    }
}

package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.OperatorCapability;
import ru.simpoint.domain.ServiceType;
import ru.simpoint.dto.CapabilityResponse;
import ru.simpoint.dto.CapabilityUpdateRequest;
import ru.simpoint.exception.BusinessRuleException;
import ru.simpoint.repository.OperatorCapabilityRepository;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CapabilityService {

    private final OperatorCapabilityRepository capabilities;

    @Transactional(readOnly = true)
    public boolean isSupported(Operator operator, ServiceType serviceType) {
        if (!serviceType.isOperatorBound()) {
            return true;
        }
        return capabilities.findByOperatorAndServiceType(operator, serviceType)
                .map(OperatorCapability::isSupported)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public List<CapabilityResponse> all() {
        return capabilities.findAllByOrderByOperatorAscServiceTypeAsc().stream()
                .map(CapabilityResponse::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CapabilityResponse> forOperator(Operator operator) {
        return capabilities.findByOperatorOrderByServiceTypeAsc(operator).stream()
                .map(CapabilityResponse::from)
                .toList();
    }

    @Transactional
    public CapabilityResponse update(Operator operator, ServiceType serviceType, CapabilityUpdateRequest request) {
        if (!serviceType.isOperatorBound()) {
            throw new BusinessRuleException("Услуга «" + serviceType.getDisplayName() + "» не привязана к оператору");
        }
        OperatorCapability capability = capabilities.findByOperatorAndServiceType(operator, serviceType)
                .orElseGet(() -> new OperatorCapability(operator, serviceType, false, null));
        capability.setSupported(request.supported());
        capability.setNote(request.note());
        return CapabilityResponse.from(capabilities.save(capability));
    }
}

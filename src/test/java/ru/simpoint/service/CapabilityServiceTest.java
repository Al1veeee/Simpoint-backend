package ru.simpoint.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.OperatorCapability;
import ru.simpoint.domain.ServiceType;
import ru.simpoint.repository.OperatorCapabilityRepository;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CapabilityServiceTest {

    @Mock
    private OperatorCapabilityRepository repository;

    @InjectMocks
    private CapabilityService service;

    @Test
    void operatorIndependentServiceIsAlwaysSupported() {
        assertThat(service.isSupported(Operator.MTS, ServiceType.PHONE_REPAIR)).isTrue();
        verify(repository, never()).findByOperatorAndServiceType(any(), any());
    }

    @Test
    void supportedFlagComesFromMatrix() {
        when(repository.findByOperatorAndServiceType(Operator.MEGAFON, ServiceType.IMEI_REPLACEMENT))
                .thenReturn(Optional.of(new OperatorCapability(Operator.MEGAFON, ServiceType.IMEI_REPLACEMENT, true, null)));
        when(repository.findByOperatorAndServiceType(Operator.MTS, ServiceType.IMEI_REPLACEMENT))
                .thenReturn(Optional.of(new OperatorCapability(Operator.MTS, ServiceType.IMEI_REPLACEMENT, false, null)));

        assertThat(service.isSupported(Operator.MEGAFON, ServiceType.IMEI_REPLACEMENT)).isTrue();
        assertThat(service.isSupported(Operator.MTS, ServiceType.IMEI_REPLACEMENT)).isFalse();
    }

    @Test
    void missingMatrixRowMeansNotSupported() {
        when(repository.findByOperatorAndServiceType(Operator.T2, ServiceType.TARIFF_CHANGE))
                .thenReturn(Optional.empty());

        assertThat(service.isSupported(Operator.T2, ServiceType.TARIFF_CHANGE)).isFalse();
    }
}

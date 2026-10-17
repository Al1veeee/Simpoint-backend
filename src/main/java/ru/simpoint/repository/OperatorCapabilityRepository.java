package ru.simpoint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.OperatorCapability;
import ru.simpoint.domain.ServiceType;

import java.util.List;
import java.util.Optional;

public interface OperatorCapabilityRepository extends JpaRepository<OperatorCapability, Long> {

    Optional<OperatorCapability> findByOperatorAndServiceType(Operator operator, ServiceType serviceType);

    List<OperatorCapability> findByOperatorOrderByServiceTypeAsc(Operator operator);

    List<OperatorCapability> findAllByOrderByOperatorAscServiceTypeAsc();
}

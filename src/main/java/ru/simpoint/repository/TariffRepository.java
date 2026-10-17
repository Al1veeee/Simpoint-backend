package ru.simpoint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.simpoint.domain.Operator;
import ru.simpoint.domain.Tariff;

import java.util.List;

public interface TariffRepository extends JpaRepository<Tariff, Long> {

    List<Tariff> findByActiveTrueOrderByOperatorAscMonthlyPriceAsc();

    List<Tariff> findByOperatorAndActiveTrueOrderByMonthlyPriceAsc(Operator operator);

    boolean existsByOperatorAndNameIgnoreCase(Operator operator, String name);
}

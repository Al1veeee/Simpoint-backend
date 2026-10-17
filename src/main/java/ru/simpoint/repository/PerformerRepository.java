package ru.simpoint.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import ru.simpoint.domain.Performer;

import java.util.List;

public interface PerformerRepository extends JpaRepository<Performer, Long> {

    List<Performer> findByActiveTrueOrderBySortOrderAscIdAsc();
}

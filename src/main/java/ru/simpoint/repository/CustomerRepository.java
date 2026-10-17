package ru.simpoint.repository;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import ru.simpoint.domain.Customer;

public interface CustomerRepository extends JpaRepository<Customer, Long> {

    Page<Customer> findByFullNameContainingIgnoreCaseOrPhoneContaining(String name, String phone, Pageable pageable);
}

package ru.simpoint.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.simpoint.domain.Customer;
import ru.simpoint.domain.Language;
import ru.simpoint.dto.CustomerRequest;
import ru.simpoint.dto.CustomerResponse;
import ru.simpoint.exception.NotFoundException;
import ru.simpoint.repository.CustomerRepository;

@Service
@RequiredArgsConstructor
public class CustomerService {

    private final CustomerRepository customers;

    @Transactional
    public CustomerResponse create(CustomerRequest request) {
        Customer customer = new Customer();
        apply(customer, request);
        return CustomerResponse.from(customers.save(customer));
    }

    @Transactional
    public CustomerResponse update(Long id, CustomerRequest request) {
        Customer customer = find(id);
        apply(customer, request);
        return CustomerResponse.from(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse get(Long id) {
        return CustomerResponse.from(find(id));
    }

    @Transactional(readOnly = true)
    public Page<CustomerResponse> search(String query, Pageable pageable) {
        String q = query == null ? "" : query.trim();
        return customers.findByFullNameContainingIgnoreCaseOrPhoneContaining(q, q, pageable)
                .map(CustomerResponse::from);
    }

    private Customer find(Long id) {
        return customers.findById(id).orElseThrow(() -> new NotFoundException("Клиент не найден: " + id));
    }

    private void apply(Customer customer, CustomerRequest request) {
        customer.setFullName(request.fullName().trim());
        customer.setPhone(request.phone());
        customer.setPreferredLanguage(request.preferredLanguage() != null ? request.preferredLanguage() : Language.RU);
    }
}

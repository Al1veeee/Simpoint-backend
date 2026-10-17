package ru.simpoint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "operator_capability")
@Getter
@Setter
@NoArgsConstructor
public class OperatorCapability {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Operator operator;

    @Enumerated(EnumType.STRING)
    @Column(name = "service_type", nullable = false, length = 32)
    private ServiceType serviceType;

    @Column(nullable = false)
    private boolean supported;

    @Column(length = 255)
    private String note;

    public OperatorCapability(Operator operator, ServiceType serviceType, boolean supported, String note) {
        this.operator = operator;
        this.serviceType = serviceType;
        this.supported = supported;
        this.note = note;
    }
}

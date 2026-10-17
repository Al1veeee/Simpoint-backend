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

import java.math.BigDecimal;

@Entity
@Table(name = "tariff")
@Getter
@Setter
@NoArgsConstructor
public class Tariff {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Operator operator;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "monthly_price", precision = 10, scale = 2)
    private BigDecimal monthlyPrice;

    @Column
    private Integer minutes;

    @Column(name = "internet_gb")
    private Integer internetGb;

    @Column(name = "unlimited_internet", nullable = false)
    private boolean unlimitedInternet;

    @Column(nullable = false)
    private boolean active = true;
}

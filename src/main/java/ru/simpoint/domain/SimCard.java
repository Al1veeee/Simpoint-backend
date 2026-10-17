package ru.simpoint.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(name = "sim_card")
@Getter
@Setter
@NoArgsConstructor
public class SimCard {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Operator operator;

    @Column(nullable = false, unique = true, length = 22)
    private String iccid;

    @Column(length = 15)
    private String msisdn;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private SimStatus status = SimStatus.IN_STOCK;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt = Instant.now();

    @Column(name = "sold_at")
    private Instant soldAt;

    @Version
    @Column(nullable = false)
    private long version;
}

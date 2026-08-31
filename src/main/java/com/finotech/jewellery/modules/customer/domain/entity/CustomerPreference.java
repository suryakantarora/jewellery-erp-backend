package com.finotech.jewellery.modules.customer.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A free-form preference, e.g. {@code PREFERRED_METAL=GOLD} or
 * {@code RING_SIZE=14}. Kept as key/value so sales staff can capture what
 * matters without a schema change.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "customer_preference", schema = "customer",
        uniqueConstraints = @UniqueConstraint(name = "uq_customer_preference",
                columnNames = {"customer_id", "preference_key"}))
public class CustomerPreference extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "customer_id", nullable = false)
    private Customer customer;

    @Column(name = "preference_key", nullable = false, length = 50)
    private String preferenceKey;

    @Column(name = "preference_value", length = 255)
    private String preferenceValue;
}

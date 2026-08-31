package com.finotech.jewellery.modules.loyalty.domain.entity;

import com.finotech.jewellery.shared.common.BaseEntity;
import com.finotech.jewellery.shared.exception.ValidationException;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * One customer's standing in the loyalty program.
 *
 * <p>The balance is a running total kept alongside the transaction history: the
 * history is the truth, and the balance exists so a POS lookup does not have to
 * sum years of transactions. The two are only ever changed together, inside one
 * transaction, and the row is locked while it happens.
 *
 * <p>{@code lifetimePoints} only ever grows — it drives tier, so spending points
 * must not demote a customer.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "loyalty_account", schema = "crm")
public class LoyaltyAccount extends BaseEntity {

    @Column(name = "customer_id", nullable = false, unique = true)
    private UUID customerId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "program_id", nullable = false)
    private LoyaltyProgram program;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tier_id")
    private LoyaltyTier tier;

    /** Points available to spend now. */
    @Column(name = "points_balance", nullable = false)
    private long pointsBalance;

    /** Every point ever earned; never decreases, and determines tier. */
    @Column(name = "lifetime_points", nullable = false)
    private long lifetimePoints;

    @Column(name = "points_redeemed", nullable = false)
    private long pointsRedeemed;

    @Column(name = "points_expired", nullable = false)
    private long pointsExpired;

    @Column(name = "enrolled_at", nullable = false)
    private Instant enrolledAt;

    @Column(name = "last_activity_at")
    private Instant lastActivityAt;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    public void earn(long points) {
        if (points <= 0) {
            throw new ValidationException("Points earned must be positive");
        }
        this.pointsBalance += points;
        this.lifetimePoints += points;
        this.lastActivityAt = Instant.now();
    }

    /**
     * @throws ValidationException if the balance is insufficient — the platform
     *         never lets a loyalty balance go negative
     */
    public void redeem(long points) {
        if (points <= 0) {
            throw new ValidationException("Points redeemed must be positive");
        }
        if (points > pointsBalance) {
            throw new ValidationException(
                    "Insufficient points: balance is " + pointsBalance + ", requested " + points);
        }
        this.pointsBalance -= points;
        this.pointsRedeemed += points;
        this.lastActivityAt = Instant.now();
    }

    /**
     * Reverses an earlier award, as when the purchase behind it is returned.
     * Lifetime points fall too, because those points were never truly earned.
     */
    public void reverse(long points) {
        long reversible = Math.min(points, pointsBalance);
        this.pointsBalance -= reversible;
        this.lifetimePoints = Math.max(0, lifetimePoints - points);
        this.lastActivityAt = Instant.now();
    }

    /**
     * Returns points that were spent but whose purchase did not complete.
     *
     * <p>Deliberately not {@link #adjust}: lifetime points must not move, because
     * these points were already counted when they were earned. Adding them again
     * would promote the customer on the strength of a purchase that never
     * happened.
     */
    public void returnRedeemed(long points) {
        if (points <= 0) {
            throw new ValidationException("Points returned must be positive");
        }
        this.pointsBalance += points;
        this.pointsRedeemed = Math.max(0, pointsRedeemed - points);
        this.lastActivityAt = Instant.now();
    }

    public void expire(long points) {
        long expiring = Math.min(points, pointsBalance);
        this.pointsBalance -= expiring;
        this.pointsExpired += expiring;
        this.lastActivityAt = Instant.now();
    }

    public void adjust(long delta) {
        long updated = pointsBalance + delta;
        if (updated < 0) {
            throw new ValidationException("An adjustment cannot take the balance below zero");
        }
        this.pointsBalance = updated;
        if (delta > 0) {
            this.lifetimePoints += delta;
        }
        this.lastActivityAt = Instant.now();
    }
}

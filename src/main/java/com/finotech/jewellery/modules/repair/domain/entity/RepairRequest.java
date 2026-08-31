package com.finotech.jewellery.modules.repair.domain.entity;

import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import com.finotech.jewellery.shared.common.BaseEntity;
import com.finotech.jewellery.shared.exception.ConflictException;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A repair job.
 *
 * <p>The piece may be one the business sold — in which case
 * {@code jewelleryItemId} links it and the item is moved to
 * {@code UNDER_REPAIR} — or a customer's own piece, described free-form.
 *
 * <p>Condition on arrival is recorded before any work starts, which is what
 * protects both sides in a dispute about damage.
 */
@Entity
@Getter
@Setter
@NoArgsConstructor
@Table(name = "repair_request", schema = "sales")
public class RepairRequest extends BaseEntity {

    @Column(name = "request_number", nullable = false, unique = true, length = 50)
    private String requestNumber;

    @Column(name = "customer_id", nullable = false)
    private UUID customerId;

    @Column(name = "branch_id", nullable = false)
    private UUID branchId;

    /** Set when the piece is one the business sold and still tracks. */
    @Column(name = "jewellery_item_id")
    private UUID jewelleryItemId;

    @Column(name = "item_description", nullable = false, length = 500)
    private String itemDescription;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private RepairStatus status = RepairStatus.RECEIVED;

    @Column(name = "received_date", nullable = false)
    private LocalDate receivedDate;

    @Column(name = "promised_date")
    private LocalDate promisedDate;

    // ---------- condition on arrival ----------

    @Column(name = "reported_problem", nullable = false, length = 1000)
    private String reportedProblem;

    @Column(name = "condition_on_arrival", length = 1000)
    private String conditionOnArrival;

    @Column(name = "received_weight", precision = 12, scale = 3)
    private BigDecimal receivedWeight;

    /** Storage keys of photographs taken at intake, comma separated. */
    @Column(name = "condition_photo_keys", length = 1000)
    private String conditionPhotoKeys;

    // ---------- estimate ----------

    @Column(name = "estimated_cost", precision = 19, scale = 4)
    private BigDecimal estimatedCost;

    @Column(name = "estimated_days")
    private Integer estimatedDays;

    @Column(name = "estimate_notes", length = 1000)
    private String estimateNotes;

    @Column(name = "customer_approved", nullable = false)
    private boolean customerApproved;

    @Column(name = "customer_response_at")
    private Instant customerResponseAt;

    @Column(name = "decline_reason", length = 500)
    private String declineReason;

    // ---------- execution ----------

    @Column(name = "assigned_to", length = 100)
    private String assignedTo;

    @Column(name = "assigned_at")
    private Instant assignedAt;

    @Column(name = "final_cost", precision = 19, scale = 4)
    private BigDecimal finalCost;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "LAK";

    @Column(name = "delivered_weight", precision = 12, scale = 3)
    private BigDecimal deliveredWeight;

    @Column(name = "ready_at")
    private Instant readyAt;

    @Column(name = "delivered_at")
    private Instant deliveredAt;

    @Column(name = "delivered_to", length = 150)
    private String deliveredTo;

    @Column(name = "notes", length = 500)
    private String notes;

    @OrderBy("occurredAt asc")
    @OneToMany(mappedBy = "repairRequest", cascade = CascadeType.ALL, orphanRemoval = true,
            fetch = FetchType.LAZY)
    private List<RepairStatusHistory> history = new ArrayList<>();

    public void addHistory(RepairStatusHistory entry) {
        entry.setRepairRequest(this);
        history.add(entry);
    }

    /**
     * @throws ConflictException if the workflow step is out of order
     */
    public void transitionTo(RepairStatus target) {
        if (!status.canTransitionTo(target)) {
            throw new ConflictException("Repair " + requestNumber + " cannot move from "
                    + status + " to " + target);
        }
        this.status = target;
    }
}

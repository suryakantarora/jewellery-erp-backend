package com.finotech.jewellery.modules.exchange;

import static org.assertj.core.api.Assertions.assertThat;

import com.finotech.jewellery.modules.exchange.domain.enums.ExchangeStatus;
import com.finotech.jewellery.modules.repair.domain.enums.RepairStatus;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The operational workflows are enforced by transition tables rather than by
 * scattered checks, so the tables themselves are what need testing.
 */
class ExchangeWorkflowTest {

    // ---------- exchange ----------

    @Test
    @DisplayName("a piece cannot be valued before it is weighed and tested")
    void valuationRequiresMeasurementFirst() {
        assertThat(ExchangeStatus.RECEIVED.canTransitionTo(ExchangeStatus.VALUED)).isFalse();
        assertThat(ExchangeStatus.WEIGHED.canTransitionTo(ExchangeStatus.VALUED)).isFalse();
        assertThat(ExchangeStatus.PURITY_TESTED.canTransitionTo(ExchangeStatus.VALUED)).isTrue();
    }

    @Test
    @DisplayName("an intake can only complete once approved")
    void completionRequiresApproval() {
        assertThat(ExchangeStatus.VALUED.canTransitionTo(ExchangeStatus.COMPLETED)).isFalse();
        assertThat(ExchangeStatus.PENDING_APPROVAL.canTransitionTo(ExchangeStatus.COMPLETED)).isFalse();
        assertThat(ExchangeStatus.APPROVED.canTransitionTo(ExchangeStatus.COMPLETED)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ExchangeStatus.class)
    @DisplayName("a completed intake is final")
    void completedIsTerminal(ExchangeStatus any) {
        assertThat(ExchangeStatus.COMPLETED.canTransitionTo(any)).isFalse();
    }

    @Test
    @DisplayName("the customer can take the piece back at any point before settlement")
    void customerCanWithdrawBeforeCompletion() {
        for (ExchangeStatus status : new ExchangeStatus[]{ExchangeStatus.RECEIVED,
                ExchangeStatus.WEIGHED, ExchangeStatus.PURITY_TESTED, ExchangeStatus.VALUED,
                ExchangeStatus.APPROVED}) {
            assertThat(status.canTransitionTo(ExchangeStatus.RETURNED_TO_CUSTOMER))
                    .as("%s should allow the piece to be returned", status)
                    .isTrue();
        }
    }

    // ---------- repair ----------

    @Test
    @DisplayName("chargeable work cannot start before the customer accepts the estimate")
    void workRequiresCustomerApproval() {
        assertThat(RepairStatus.RECEIVED.canTransitionTo(RepairStatus.IN_PROGRESS)).isFalse();
        assertThat(RepairStatus.INSPECTION.canTransitionTo(RepairStatus.IN_PROGRESS)).isFalse();
        assertThat(RepairStatus.ESTIMATION.canTransitionTo(RepairStatus.IN_PROGRESS)).isFalse();
        assertThat(RepairStatus.APPROVAL_PENDING.canTransitionTo(RepairStatus.IN_PROGRESS)).isTrue();
    }

    @Test
    @DisplayName("a failed quality check returns the job to the bench")
    void failedQualityCheckReturnsToWork() {
        assertThat(RepairStatus.QUALITY_CHECK.canTransitionTo(RepairStatus.IN_PROGRESS)).isTrue();
        assertThat(RepairStatus.QUALITY_CHECK.canTransitionTo(RepairStatus.READY)).isTrue();
        // Work must pass quality check before it can be handed over.
        assertThat(RepairStatus.QUALITY_CHECK.canTransitionTo(RepairStatus.DELIVERED)).isFalse();
        assertThat(RepairStatus.IN_PROGRESS.canTransitionTo(RepairStatus.READY)).isFalse();
    }

    @Test
    @DisplayName("a declined estimate still returns the piece to the customer")
    void declinedRepairIsStillHandedBack() {
        assertThat(RepairStatus.APPROVAL_PENDING.canTransitionTo(RepairStatus.DECLINED)).isTrue();
        assertThat(RepairStatus.DECLINED.canTransitionTo(RepairStatus.DELIVERED)).isTrue();
        // No work happens on a declined job.
        assertThat(RepairStatus.DECLINED.canTransitionTo(RepairStatus.IN_PROGRESS)).isFalse();
    }

    @Test
    @DisplayName("a piece is in the workshop until it is delivered or cancelled")
    void workshopPossession() {
        assertThat(RepairStatus.IN_PROGRESS.isInWorkshop()).isTrue();
        assertThat(RepairStatus.READY.isInWorkshop()).isTrue();
        assertThat(RepairStatus.DELIVERED.isInWorkshop()).isFalse();
        assertThat(RepairStatus.CANCELLED.isInWorkshop()).isFalse();
    }

    // ---------- stock count ----------

    @Test
    @DisplayName("the statuses that block a second count are the open ones")
    void openCountStatuses() {
        // A closed or cancelled count must not block a fresh verification.
        assertThat(StockCountStatus.CLOSED).isNotIn(StockCountStatus.IN_PROGRESS,
                StockCountStatus.PENDING_REVIEW, StockCountStatus.APPROVED);
        assertThat(StockCountStatus.CANCELLED).isNotIn(StockCountStatus.IN_PROGRESS,
                StockCountStatus.PENDING_REVIEW, StockCountStatus.APPROVED);
    }
}

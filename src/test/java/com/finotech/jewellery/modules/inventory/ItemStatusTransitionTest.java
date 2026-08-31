package com.finotech.jewellery.modules.inventory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.finotech.jewellery.modules.inventory.domain.entity.JewelleryItem;
import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import com.finotech.jewellery.shared.exception.ConflictException;
import java.math.BigDecimal;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

/**
 * The transition table is the guard that keeps sold, in-transit and scrapped
 * items out of the sale and transfer paths.
 */
class ItemStatusTransitionTest {

    @Test
    @DisplayName("a sold item cannot be transferred or resold")
    void soldItemCannotMoveOrSellAgain() {
        assertThat(ItemStatus.SOLD.canTransitionTo(ItemStatus.IN_TRANSIT)).isFalse();
        assertThat(ItemStatus.SOLD.canTransitionTo(ItemStatus.SOLD)).isFalse();
        assertThat(ItemStatus.SOLD.canTransitionTo(ItemStatus.RESERVED)).isFalse();
    }

    @Test
    @DisplayName("an item under repair cannot be transferred")
    void repairItemCannotBeTransferred() {
        assertThat(ItemStatus.UNDER_REPAIR.canTransitionTo(ItemStatus.IN_TRANSIT)).isFalse();
        assertThat(ItemStatus.UNDER_REPAIR.isInStock()).isFalse();
    }

    @Test
    @DisplayName("a reserved item is still in stock and may be sold")
    void reservedItemIsSellable() {
        assertThat(ItemStatus.RESERVED.isInStock()).isTrue();
        assertThat(ItemStatus.RESERVED.canTransitionTo(ItemStatus.SOLD)).isTrue();
    }

    @ParameterizedTest
    @EnumSource(ItemStatus.class)
    @DisplayName("scrapped is terminal from every state")
    void scrappedIsTerminal(ItemStatus any) {
        assertThat(ItemStatus.SCRAPPED.canTransitionTo(any)).isFalse();
    }

    @Test
    @DisplayName("an illegal transition is rejected with a conflict")
    void illegalTransitionThrows() {
        JewelleryItem item = itemWith(ItemStatus.SOLD);
        assertThatThrownBy(() -> item.transitionTo(ItemStatus.IN_TRANSIT))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("cannot move from SOLD to IN_TRANSIT");
    }

    @Test
    @DisplayName("a legal transition is applied")
    void legalTransitionApplies() {
        JewelleryItem item = itemWith(ItemStatus.AVAILABLE);
        item.transitionTo(ItemStatus.RESERVED);
        assertThat(item.getStatus()).isEqualTo(ItemStatus.RESERVED);
    }

    @Test
    @DisplayName("net metal weight is gross minus stone weight")
    void netWeightIsDerived() {
        JewelleryItem item = itemWith(ItemStatus.DRAFT);
        item.setGrossWeight(new BigDecimal("12.500"));
        item.setStoneWeight(new BigDecimal("0.150"));
        item.recalculateNetMetalWeight();
        assertThat(item.getNetMetalWeight()).isEqualByComparingTo("12.350");
    }

    private JewelleryItem itemWith(ItemStatus status) {
        JewelleryItem item = new JewelleryItem();
        item.setItemCode("ITM-TEST-0001");
        item.setStatus(status);
        return item;
    }
}

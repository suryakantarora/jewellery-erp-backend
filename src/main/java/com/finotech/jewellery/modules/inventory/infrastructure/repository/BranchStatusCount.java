package com.finotech.jewellery.modules.inventory.infrastructure.repository;

import com.finotech.jewellery.modules.inventory.domain.enums.ItemStatus;
import java.util.UUID;

/** One row of the grouped availability count: a branch, a status, how many. */
public record BranchStatusCount(UUID branchId, ItemStatus status, Long count) {
}

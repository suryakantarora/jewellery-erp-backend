package com.finotech.jewellery.modules.warehouse.api.response;

import com.finotech.jewellery.modules.warehouse.domain.entity.StockCount;
import com.finotech.jewellery.modules.warehouse.domain.entity.StorageBin;
import com.finotech.jewellery.modules.warehouse.domain.enums.BinType;
import com.finotech.jewellery.modules.warehouse.domain.enums.StockCountStatus;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class WarehouseResponses {

    private WarehouseResponses() {
    }

    public record BinResponse(UUID id, UUID locationId, UUID parentId, String code, String name,
                              BinType binType, Integer capacity, String description,
                              boolean active) {

        public static BinResponse from(StorageBin b) {
            return new BinResponse(b.getId(), b.getLocationId(),
                    b.getParent() == null ? null : b.getParent().getId(), b.getCode(), b.getName(),
                    b.getBinType(), b.getCapacity(), b.getDescription(), b.isActive());
        }
    }

    public record StockCountResponse(UUID id, String referenceNumber, UUID locationId,
                                     UUID branchId, StockCountStatus status, LocalDate countDate,
                                     boolean dualAuthorization, int expectedCount,
                                     int countedCount, int missingCount, int unexpectedCount,
                                     boolean hasVariance, String countedBy, Instant countedAt,
                                     String approvedBy, Instant approvedAt,
                                     String secondApprovedBy, Instant secondApprovedAt,
                                     Instant closedAt, String notes,
                                     List<StockCountLineResponse> lines) {

        public record StockCountLineResponse(UUID id, UUID jewelleryItemId, String itemCode,
                                             boolean expected, boolean counted, boolean missing,
                                             boolean unexpected, UUID binId, String varianceNote) {
        }

        public static StockCountResponse from(StockCount c) {
            return build(c, false);
        }

        /** Full sheet including every line; used on the detail endpoint. */
        public static StockCountResponse withLines(StockCount c) {
            return build(c, true);
        }

        private static StockCountResponse build(StockCount c, boolean includeLines) {
            List<StockCountLineResponse> lines = includeLines
                    ? c.getLines().stream()
                            .map(l -> new StockCountLineResponse(l.getId(), l.getJewelleryItemId(),
                                    l.getItemCode(), l.isExpected(), l.isCounted(), l.isMissing(),
                                    l.isUnexpected(), l.getBinId(), l.getVarianceNote()))
                            .toList()
                    : null;
            return new StockCountResponse(c.getId(), c.getReferenceNumber(), c.getLocationId(),
                    c.getBranchId(), c.getStatus(), c.getCountDate(), c.isDualAuthorization(),
                    c.getExpectedCount(), c.getCountedCount(), c.getMissingCount(),
                    c.getUnexpectedCount(), c.hasVariance(), c.getCountedBy(), c.getCountedAt(),
                    c.getApprovedBy(), c.getApprovedAt(), c.getSecondApprovedBy(),
                    c.getSecondApprovedAt(), c.getClosedAt(), c.getNotes(), lines);
        }
    }
}

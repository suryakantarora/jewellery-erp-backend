package com.finotech.jewellery.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * The catalogue of business events (section 21). Kept together so the set of
 * facts the platform emits is visible in one place.
 */
public final class DomainEvents {

    private DomainEvents() {
    }

    /** Base implementation carrying the common fields. */
    private abstract static class BaseEvent implements DomainEvent {
        private final Instant occurredAt = Instant.now();
        private final UUID branchId;

        protected BaseEvent(UUID branchId) {
            this.branchId = branchId;
        }

        @Override
        public Instant occurredAt() {
            return occurredAt;
        }

        @Override
        public UUID branchId() {
            return branchId;
        }
    }

    public static final class SaleCompleted extends BaseEvent {
        private final UUID saleId;
        private final UUID customerId;
        private final String invoiceNumber;
        private final BigDecimal totalAmount;
        private final int itemCount;

        public SaleCompleted(UUID saleId, UUID customerId, UUID branchId, String invoiceNumber,
                             BigDecimal totalAmount, int itemCount) {
            super(branchId);
            this.saleId = saleId;
            this.customerId = customerId;
            this.invoiceNumber = invoiceNumber;
            this.totalAmount = totalAmount;
            this.itemCount = itemCount;
        }

        public UUID saleId() {
            return saleId;
        }

        public UUID customerId() {
            return customerId;
        }

        @Override
        public String eventType() {
            return "SALE_COMPLETED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("saleId", saleId, "customerId", customerId,
                    "invoiceNumber", invoiceNumber, "totalAmount", totalAmount,
                    "itemCount", itemCount);
        }
    }

    public static final class PaymentReceived extends BaseEvent {
        private final UUID paymentId;
        private final UUID saleId;
        private final UUID customerId;
        private final BigDecimal amount;
        private final String method;

        public PaymentReceived(UUID paymentId, UUID saleId, UUID customerId, UUID branchId,
                               BigDecimal amount, String method) {
            super(branchId);
            this.paymentId = paymentId;
            this.saleId = saleId;
            this.customerId = customerId;
            this.amount = amount;
            this.method = method;
        }

        public UUID customerId() {
            return customerId;
        }

        public UUID saleId() {
            return saleId;
        }

        @Override
        public String eventType() {
            return "PAYMENT_RECEIVED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("paymentId", paymentId, "saleId", saleId,
                    "amount", amount, "method", method);
        }
    }

    public static final class ItemTransferred extends BaseEvent {
        private final UUID movementId;
        private final String referenceNumber;
        private final UUID toLocationId;
        private final int itemCount;

        public ItemTransferred(UUID movementId, String referenceNumber, UUID branchId,
                               UUID toLocationId, int itemCount) {
            super(branchId);
            this.movementId = movementId;
            this.referenceNumber = referenceNumber;
            this.toLocationId = toLocationId;
            this.itemCount = itemCount;
        }

        @Override
        public String eventType() {
            return "ITEM_TRANSFERRED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("movementId", movementId, "referenceNumber", referenceNumber,
                    "toLocationId", toLocationId, "itemCount", itemCount);
        }
    }

    public static final class RepairReady extends BaseEvent {
        private final UUID repairRequestId;
        private final UUID customerId;
        private final String requestNumber;

        public RepairReady(UUID repairRequestId, UUID customerId, UUID branchId,
                           String requestNumber) {
            super(branchId);
            this.repairRequestId = repairRequestId;
            this.customerId = customerId;
            this.requestNumber = requestNumber;
        }

        public UUID customerId() {
            return customerId;
        }

        @Override
        public String eventType() {
            return "REPAIR_READY";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("repairRequestId", repairRequestId, "requestNumber", requestNumber);
        }
    }

    public static final class ExchangeCompleted extends BaseEvent {
        private final UUID exchangeId;
        private final UUID customerId;
        private final String referenceNumber;
        private final BigDecimal valuation;

        public ExchangeCompleted(UUID exchangeId, UUID customerId, UUID branchId,
                                 String referenceNumber, BigDecimal valuation) {
            super(branchId);
            this.exchangeId = exchangeId;
            this.customerId = customerId;
            this.referenceNumber = referenceNumber;
            this.valuation = valuation;
        }

        public UUID customerId() {
            return customerId;
        }

        @Override
        public String eventType() {
            return "EXCHANGE_COMPLETED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("exchangeId", exchangeId, "referenceNumber", referenceNumber,
                    "valuation", valuation);
        }
    }

    /** One customer selected by a campaign; notification turns this into a message. */
    public static final class CampaignTargeted extends BaseEvent {
        private final UUID campaignId;
        private final UUID customerId;
        private final String campaignCode;
        private final String campaignName;
        private final String templateCode;

        public CampaignTargeted(UUID campaignId, UUID customerId, UUID branchId,
                                String campaignCode, String campaignName, String templateCode) {
            super(branchId);
            this.campaignId = campaignId;
            this.customerId = customerId;
            this.campaignCode = campaignCode;
            this.campaignName = campaignName;
            this.templateCode = templateCode;
        }

        public UUID customerId() {
            return customerId;
        }

        public UUID campaignId() {
            return campaignId;
        }

        @Override
        public String eventType() {
            return "CAMPAIGN_TARGETED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("campaignId", campaignId, "campaignCode", campaignCode,
                    "campaignName", campaignName, "templateCode", String.valueOf(templateCode));
        }
    }

    /** Points awarded, with the money value the business now owes. */
    public static final class LoyaltyPointsEarned extends BaseEvent {
        private final UUID customerId;
        private final UUID saleId;
        private final long points;
        private final BigDecimal value;

        public LoyaltyPointsEarned(UUID customerId, UUID saleId, UUID branchId, long points,
                                   BigDecimal value) {
            super(branchId);
            this.customerId = customerId;
            this.saleId = saleId;
            this.points = points;
            this.value = value;
        }

        public UUID customerId() {
            return customerId;
        }

        @Override
        public String eventType() {
            return "LOYALTY_POINTS_ADDED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("customerId", customerId, "saleId", saleId,
                    "points", points, "value", value);
        }
    }

    /** Stock accepted into inventory, and what the business owes for it. */
    public static final class GoodsReceived extends BaseEvent {
        private final UUID goodsReceiptId;
        private final UUID supplierId;
        private final String receiptNumber;
        private final BigDecimal totalCost;
        private final int itemCount;

        public GoodsReceived(UUID goodsReceiptId, UUID supplierId, UUID branchId,
                             String receiptNumber, BigDecimal totalCost, int itemCount) {
            super(branchId);
            this.goodsReceiptId = goodsReceiptId;
            this.supplierId = supplierId;
            this.receiptNumber = receiptNumber;
            this.totalCost = totalCost;
            this.itemCount = itemCount;
        }

        public UUID goodsReceiptId() {
            return goodsReceiptId;
        }

        public UUID supplierId() {
            return supplierId;
        }

        @Override
        public String eventType() {
            return "GOODS_RECEIVED";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("goodsReceiptId", goodsReceiptId, "receiptNumber", receiptNumber,
                    "totalCost", totalCost, "itemCount", itemCount);
        }
    }

    public static final class LowStock extends BaseEvent {
        private final UUID locationId;
        private final long availableCount;
        private final int threshold;

        public LowStock(UUID branchId, UUID locationId, long availableCount, int threshold) {
            super(branchId);
            this.locationId = locationId;
            this.availableCount = availableCount;
            this.threshold = threshold;
        }

        @Override
        public String eventType() {
            return "LOW_STOCK";
        }

        @Override
        public Map<String, Object> payload() {
            return Map.of("locationId", locationId, "availableCount", availableCount,
                    "threshold", threshold);
        }
    }
}

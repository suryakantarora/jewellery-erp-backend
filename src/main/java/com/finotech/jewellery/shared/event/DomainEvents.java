package com.finotech.jewellery.shared.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
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

    // ---------- staff-directed events ----------
    //
    // The events above are broadcast to a branch or sent to a customer. These
    // carry enough to resolve *which member of staff* should be told: the
    // notification module turns a username into a user id and queues one row
    // per person. Payload values are never null (Map.of would reject them), so
    // a missing value renders as an empty string in a template.

    /** A movement is waiting for someone with approval rights to act. */
    public static final class TransferAwaitingApproval extends BaseEvent {
        private final UUID movementId;
        private final String referenceNumber;
        private final UUID fromBranchId;
        private final UUID toBranchId;
        private final String fromLocation;
        private final String toLocation;
        private final int itemCount;
        private final boolean requiresSecondApproval;
        private final String lastApprover;

        /**
         * @param lastApprover username of the first approver when a second
         *                     signature is still needed, so they are not asked
         *                     to approve their own approval; null on creation
         */
        public TransferAwaitingApproval(UUID movementId, String referenceNumber,
                                        UUID fromBranchId, UUID toBranchId,
                                        String fromLocation, String toLocation, int itemCount,
                                        boolean requiresSecondApproval, String lastApprover) {
            super(toBranchId);
            this.movementId = movementId;
            this.referenceNumber = referenceNumber;
            this.fromBranchId = fromBranchId;
            this.toBranchId = toBranchId;
            this.fromLocation = fromLocation;
            this.toLocation = toLocation;
            this.itemCount = itemCount;
            this.requiresSecondApproval = requiresSecondApproval;
            this.lastApprover = lastApprover;
        }

        public UUID movementId() {
            return movementId;
        }

        public UUID fromBranchId() {
            return fromBranchId;
        }

        public UUID toBranchId() {
            return toBranchId;
        }

        public boolean requiresSecondApproval() {
            return requiresSecondApproval;
        }

        public String lastApprover() {
            return lastApprover;
        }

        @Override
        public String eventType() {
            return "TRANSFER_AWAITING_APPROVAL";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> values = new HashMap<>();
            values.put("movementId", movementId);
            values.put("referenceNumber", referenceNumber);
            values.put("fromLocation", fromLocation == null ? "" : fromLocation);
            values.put("toLocation", toLocation == null ? "" : toLocation);
            values.put("itemCount", itemCount);
            values.put("requiresSecondApproval", requiresSecondApproval);
            return values;
        }
    }

    /** A movement was approved or rejected; the person who raised it is told. */
    public static final class TransferDecided extends BaseEvent {
        private final UUID movementId;
        private final String referenceNumber;
        private final String createdBy;
        private final boolean approved;
        private final String rejectionReason;

        public TransferDecided(UUID movementId, String referenceNumber, UUID branchId,
                               String createdBy, boolean approved, String rejectionReason) {
            super(branchId);
            this.movementId = movementId;
            this.referenceNumber = referenceNumber;
            this.createdBy = createdBy;
            this.approved = approved;
            this.rejectionReason = rejectionReason;
        }

        public UUID movementId() {
            return movementId;
        }

        public String createdBy() {
            return createdBy;
        }

        public boolean approved() {
            return approved;
        }

        @Override
        public String eventType() {
            return approved ? "TRANSFER_APPROVED" : "TRANSFER_REJECTED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> values = new HashMap<>();
            values.put("movementId", movementId);
            values.put("referenceNumber", referenceNumber);
            values.put("rejectionReason", rejectionReason == null ? "" : rejectionReason);
            return values;
        }
    }

    /** A purchase order was approved or rejected; the person who raised it is told. */
    public static final class PurchaseOrderDecided extends BaseEvent {
        private final UUID purchaseOrderId;
        private final String orderNumber;
        private final String createdBy;
        private final boolean approved;
        private final String rejectionReason;

        public PurchaseOrderDecided(UUID purchaseOrderId, String orderNumber, UUID branchId,
                                    String createdBy, boolean approved, String rejectionReason) {
            super(branchId);
            this.purchaseOrderId = purchaseOrderId;
            this.orderNumber = orderNumber;
            this.createdBy = createdBy;
            this.approved = approved;
            this.rejectionReason = rejectionReason;
        }

        public UUID purchaseOrderId() {
            return purchaseOrderId;
        }

        public String createdBy() {
            return createdBy;
        }

        public boolean approved() {
            return approved;
        }

        @Override
        public String eventType() {
            return approved ? "PURCHASE_ORDER_APPROVED" : "PURCHASE_ORDER_REJECTED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> values = new HashMap<>();
            values.put("purchaseOrderId", purchaseOrderId);
            values.put("orderNumber", orderNumber);
            values.put("rejectionReason", rejectionReason == null ? "" : rejectionReason);
            return values;
        }
    }

    /**
     * A repair passed quality check. The customer-facing {@link RepairReady}
     * still goes out; this one tells the technician and whoever logged the job.
     */
    public static final class RepairReadyStaff extends BaseEvent {
        private final UUID repairRequestId;
        private final String requestNumber;
        private final String assignedTo;
        private final String createdBy;

        public RepairReadyStaff(UUID repairRequestId, String requestNumber, UUID branchId,
                                String assignedTo, String createdBy) {
            super(branchId);
            this.repairRequestId = repairRequestId;
            this.requestNumber = requestNumber;
            this.assignedTo = assignedTo;
            this.createdBy = createdBy;
        }

        public UUID repairRequestId() {
            return repairRequestId;
        }

        public String assignedTo() {
            return assignedTo;
        }

        public String createdBy() {
            return createdBy;
        }

        @Override
        public String eventType() {
            return "REPAIR_READY_STAFF";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> values = new HashMap<>();
            values.put("repairRequestId", repairRequestId);
            values.put("requestNumber", requestNumber);
            values.put("assignedTo", assignedTo == null ? "" : assignedTo);
            return values;
        }
    }

    /**
     * A sale at or above the compliance threshold. Derived from
     * {@link SaleCompleted} by the notification module rather than raised by
     * sales: the threshold is a reporting concern, not a sales one.
     */
    public static final class HighValueSale extends BaseEvent {
        private final UUID saleId;
        private final String invoiceNumber;
        private final BigDecimal totalAmount;
        private final String currency;

        public HighValueSale(UUID saleId, UUID branchId, String invoiceNumber,
                             BigDecimal totalAmount, String currency) {
            super(branchId);
            this.saleId = saleId;
            this.invoiceNumber = invoiceNumber;
            this.totalAmount = totalAmount;
            this.currency = currency;
        }

        public UUID saleId() {
            return saleId;
        }

        @Override
        public String eventType() {
            return "HIGH_VALUE_SALE";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> values = new HashMap<>();
            values.put("saleId", saleId);
            values.put("invoiceNumber", invoiceNumber == null ? "" : invoiceNumber);
            values.put("totalAmount", totalAmount);
            values.put("currency", currency == null ? "" : currency);
            return values;
        }
    }

    // =====================================================================
    // Approvals and discount workflow (V29). Kept in one block at the end
    // of the catalogue so it can be merged independently of other additions.
    // =====================================================================

    /** An approver asked the requester for more information before deciding. */
    public static final class ApprovalInformationRequested extends BaseEvent {
        private final String approvalType;
        private final UUID referenceId;
        private final String reference;
        private final String message;
        private final String requestedBy;
        private final String creatorUsername;

        public ApprovalInformationRequested(UUID branchId, String approvalType, UUID referenceId,
                                            String reference, String message, String requestedBy,
                                            String creatorUsername) {
            super(branchId);
            this.approvalType = approvalType;
            this.referenceId = referenceId;
            this.reference = reference;
            this.message = message;
            this.requestedBy = requestedBy;
            this.creatorUsername = creatorUsername;
        }

        /** Username of whoever raised the underlying record; the natural recipient. */
        public String creatorUsername() {
            return creatorUsername;
        }

        @Override
        public String eventType() {
            return "APPROVAL_INFO_REQUESTED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("approvalType", approvalType);
            payload.put("referenceId", referenceId);
            payload.put("reference", String.valueOf(reference));
            payload.put("message", message);
            payload.put("requestedBy", requestedBy);
            payload.put("creatorUsername", String.valueOf(creatorUsername));
            return payload;
        }
    }

    /** The requester answered an approver's information request. */
    public static final class ApprovalInformationAnswered extends BaseEvent {
        private final String approvalType;
        private final UUID referenceId;
        private final String reference;
        private final String answer;
        private final String answeredBy;
        private final String requestedBy;

        public ApprovalInformationAnswered(UUID branchId, String approvalType, UUID referenceId,
                                           String reference, String answer, String answeredBy,
                                           String requestedBy) {
            super(branchId);
            this.approvalType = approvalType;
            this.referenceId = referenceId;
            this.reference = reference;
            this.answer = answer;
            this.answeredBy = answeredBy;
            this.requestedBy = requestedBy;
        }

        /** Username of the approver who asked; the natural recipient. */
        public String requestedBy() {
            return requestedBy;
        }

        @Override
        public String eventType() {
            return "APPROVAL_INFO_ANSWERED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("approvalType", approvalType);
            payload.put("referenceId", referenceId);
            payload.put("reference", String.valueOf(reference));
            payload.put("answer", answer);
            payload.put("answeredBy", answeredBy);
            payload.put("requestedBy", String.valueOf(requestedBy));
            return payload;
        }
    }

    /** A salesperson asked for a discount beyond what the branch policy allows. */
    public static final class DiscountRequested extends BaseEvent {
        private final UUID requestId;
        private final String requestedBy;
        private final BigDecimal percentage;
        private final BigDecimal amount;
        private final UUID itemId;
        private final UUID customerId;

        public DiscountRequested(UUID requestId, UUID branchId, String requestedBy,
                                 BigDecimal percentage, BigDecimal amount, UUID itemId,
                                 UUID customerId) {
            super(branchId);
            this.requestId = requestId;
            this.requestedBy = requestedBy;
            this.percentage = percentage;
            this.amount = amount;
            this.itemId = itemId;
            this.customerId = customerId;
        }

        public UUID requestId() {
            return requestId;
        }

        @Override
        public String eventType() {
            return "DISCOUNT_REQUESTED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("requestId", requestId);
            payload.put("branchId", branchId());
            payload.put("requestedBy", requestedBy);
            payload.put("percentage", percentage);
            payload.put("amount", amount);
            payload.put("itemId", itemId);
            payload.put("customerId", customerId);
            return payload;
        }
    }

    /** A discount request was approved or rejected. */
    public static final class DiscountDecided extends BaseEvent {
        private final UUID requestId;
        private final boolean approved;
        private final String decidedBy;
        private final String note;
        private final String requesterUsername;

        public DiscountDecided(UUID requestId, UUID branchId, boolean approved, String decidedBy,
                               String note, String requesterUsername) {
            super(branchId);
            this.requestId = requestId;
            this.approved = approved;
            this.decidedBy = decidedBy;
            this.note = note;
            this.requesterUsername = requesterUsername;
        }

        public UUID requestId() {
            return requestId;
        }

        public boolean approved() {
            return approved;
        }

        /** Username of the salesperson who asked; the natural recipient. */
        public String requesterUsername() {
            return requesterUsername;
        }

        @Override
        public String eventType() {
            return "DISCOUNT_DECIDED";
        }

        @Override
        public Map<String, Object> payload() {
            Map<String, Object> payload = new java.util.HashMap<>();
            payload.put("requestId", requestId);
            payload.put("approved", approved);
            payload.put("decidedBy", decidedBy);
            payload.put("note", note);
            payload.put("requesterUsername", String.valueOf(requesterUsername));
            return payload;
        }
    }
}

# Jewellery ERP & Retail Platform Backend Architecture

> **Project Goal:** Build a scalable backend for a multi-branch
> jewellery retailer in Laos.
>
> **Recommended Architecture:** Java + Spring Boot **Modular Monolith**
> with clear business-domain boundaries.

------------------------------------------------------------------------

## 1. Core Architecture Principles

Start with a Modular Monolith rather than many microservices.

Benefits:

-   Faster initial development
-   Easier deployment and debugging
-   Single codebase
-   Clear domain boundaries
-   Lower operational complexity
-   Modules can later be extracted into microservices

Rules:

1.  Each module owns its business logic.
2.  Each module owns its database tables.
3.  Controllers contain no business logic.
4.  APIs use DTOs; JPA entities are not exposed directly.
5.  Cross-module access happens through explicit services/interfaces.
6.  Sensitive actions are audited.
7.  Critical business operations are transactional.

------------------------------------------------------------------------

## 2. Recommended Technology Stack

  Layer                 Technology
  --------------------- ---------------------------------
  Backend               Java + Spring Boot
  Architecture          Modular Monolith
  API                   REST
  Database              PostgreSQL
  Cache                 Redis
  File Storage          S3-compatible storage / MinIO
  Authentication        OAuth2 / JWT
  Database Migration    Flyway
  Validation            Jakarta Validation
  API Documentation     OpenAPI / Swagger
  Messaging later       Kafka or RabbitMQ
  Monitoring            Actuator + Prometheus + Grafana
  Containerization      Docker
  Orchestration later   Kubernetes

------------------------------------------------------------------------

## 3. High-Level Architecture

``` text
CLIENT APPLICATIONS
├── Admin ERP Portal
├── Retail POS
├── Customer Portal
├── Customer Mobile App
└── Employee Mobile App
        |
        v
API GATEWAY / LOAD BALANCER
        |
        v
JEWELLERY BACKEND PLATFORM
        |
        v
SPRING BOOT MODULAR MONOLITH
        |
        +-- Business Modules
        |
        v
PostgreSQL + Redis + S3/MinIO
```

------------------------------------------------------------------------

## 4. Root Project Structure

``` text
jewellery-platform
├── src/main/java/com/company/jewellery
│   ├── shared
│   │   ├── config
│   │   ├── common
│   │   ├── exception
│   │   ├── security
│   │   ├── audit
│   │   └── utils
│   │
│   ├── modules
│   │   ├── identity
│   │   ├── organization
│   │   ├── customer
│   │   ├── product
│   │   ├── metal
│   │   ├── gemstone
│   │   ├── inventory
│   │   ├── supplier
│   │   ├── procurement
│   │   ├── pricing
│   │   ├── sales
│   │   ├── payment
│   │   ├── exchange
│   │   ├── repair
│   │   ├── crm
│   │   ├── loyalty
│   │   ├── warehouse
│   │   ├── finance
│   │   ├── notification
│   │   ├── reporting
│   │   └── compliance
│   │
│   └── JewelleryApplication.java
│
├── src/main/resources
│   ├── application.yml
│   └── db/migration
│
└── pom.xml
```

------------------------------------------------------------------------

## 5. Standard Internal Module Structure

Every module should follow a similar structure:

``` text
inventory
├── api
│   ├── controller
│   ├── request
│   └── response
├── application
│   ├── service
│   ├── command
│   └── query
├── domain
│   ├── entity
│   ├── enum
│   └── model
└── infrastructure
    ├── repository
    └── persistence
```

------------------------------------------------------------------------

# 6. Complete Backend Module List

## Module 1: Shared / Platform

Contains:

-   Common API structures
-   Global exception handling
-   Security helpers
-   Configuration
-   Auditing helpers
-   Utility classes

------------------------------------------------------------------------

## Module 2: Identity & Access

**Build this first.**

Responsibilities:

-   Users
-   Login/logout
-   Roles
-   Permissions
-   Password management
-   JWT
-   Refresh tokens
-   Branch access
-   Session management

Main entities:

``` text
User
Role
Permission
UserRole
RolePermission
UserBranch
RefreshToken
```

Example permissions:

``` text
PRODUCT_VIEW
PRODUCT_CREATE
INVENTORY_VIEW
INVENTORY_TRANSFER
PRICE_CHANGE
SALE_CREATE
DISCOUNT_REQUEST
DISCOUNT_APPROVE
```

------------------------------------------------------------------------

## Module 3: Organization

Manages business structure.

Entities:

``` text
Company
Branch
Location
Showroom
Warehouse
Vault
Counter
```

Hierarchy:

``` text
Company
├── Head Office
├── Branch
│   ├── Showroom
│   ├── Counter
│   ├── Vault
│   └── Store Room
└── Central Warehouse
```

------------------------------------------------------------------------

## Module 4: Customer

Responsibilities:

-   Customer registration
-   Profile
-   Contact details
-   Addresses
-   Preferences
-   Documents
-   KYC data where required

Entities:

``` text
Customer
CustomerAddress
CustomerDocument
CustomerPreference
```

------------------------------------------------------------------------

## Module 5: Product & Jewellery Master

Defines reusable product information.

Entities:

``` text
ProductCategory
ProductType
Collection
Brand
JewelleryDesign
Product
ProductImage
Size
```

Examples:

``` text
Ring
Necklace
Bracelet
Earring
Pendant
Bangle
Chain
```

Important:

``` text
Product Master = reusable design definition
Jewellery Item = actual unique physical item
```

------------------------------------------------------------------------

## Module 6: Metal Management

Responsibilities:

-   Metal master
-   Purity
-   Daily rates
-   Wastage
-   Conversion
-   Scrap

Entities:

``` text
Metal
Purity
MetalRate
MetalConversion
ScrapMetal
```

Examples:

``` text
Gold
Silver
Platinum
White Gold
```

Purity:

``` text
18K
22K
24K
```

------------------------------------------------------------------------

## Module 7: Diamond & Gemstone

Responsibilities:

-   Diamond details
-   Gemstone details
-   Certificates
-   Stone assignment

Entities:

``` text
Gemstone
Diamond
StoneCertificate
JewelleryStone
```

Attributes can include:

``` text
Carat
Cut
Colour
Clarity
Shape
Certificate Number
Certificate File
```

------------------------------------------------------------------------

## Module 8: Jewellery Item & Serialized Inventory

**This is the core module of the entire platform.**

Core entity:

``` text
JewelleryItem
```

Important attributes:

``` text
Item ID
Product / Design
Metal
Purity
Gross Weight
Net Metal Weight
Stone Weight
RFID
QR Code
Barcode
Cost
Current Price
Current Location
Current Status
Lifecycle History
```

Statuses:

``` text
AVAILABLE
RESERVED
IN_TRANSIT
SOLD
UNDER_REPAIR
RETURNED
EXCHANGED
BUYBACK
SCRAPPED
```

------------------------------------------------------------------------

## Module 9: Inventory Movement

Responsibilities:

-   Transfers
-   Issue
-   Receive
-   Return
-   Goods in transit
-   Approval

Core entity:

``` text
InventoryMovement
```

Each movement records:

``` text
Jewellery Item
From Location
To Location
Movement Type
Reference Number
Created By
Approved By
Created Date
Completed Date
Status
```

Example:

``` text
Central Warehouse
        |
        v
Branch Warehouse
        |
        v
Showroom
        |
        v
Counter
        |
        v
Customer
```

------------------------------------------------------------------------

## Module 10: Supplier

Entities:

``` text
Supplier
SupplierContact
SupplierDocument
SupplierBankAccount
```

Responsibilities:

-   Vendor master
-   Contacts
-   Documents
-   Bank details
-   Supplier references

------------------------------------------------------------------------

## Module 11: Procurement

Responsibilities:

-   Purchase requisition
-   Purchase order
-   Goods receipt
-   Supplier invoice
-   Purchase return
-   Approval

Workflow:

``` text
Purchase Requisition
        |
        v
Approval
        |
        v
Purchase Order
        |
        v
Goods Receipt
        |
        v
Quality Check
        |
        v
Inventory
```

------------------------------------------------------------------------

## Module 12: Pricing

Keep pricing independent from Sales.

Responsibilities:

-   Metal pricing
-   Making charges
-   Wastage
-   Stone pricing
-   Discounts
-   Promotions

Calculation:

``` text
Metal Value
+ Making Charges
+ Wastage
+ Stone Value
+ Tax
- Discount
----------------
Final Price
```

Recommended internal API:

``` text
calculatePrice(item, customer, branch)
```

Return a detailed price breakdown.

------------------------------------------------------------------------

## Module 13: Sales & POS

Responsibilities:

-   Quotation
-   Cart
-   Sales
-   Invoice
-   Return
-   Delivery
-   Daily closing

Workflow:

``` text
Customer
   |
Quotation
   |
Cart
   |
Payment
   |
Sale
   |
Invoice
   |
Jewellery Item = SOLD
```

------------------------------------------------------------------------

## Module 14: Payment

Responsibilities:

-   Payment transactions
-   Payment methods
-   Split payments
-   Refunds
-   Reconciliation

Methods:

``` text
Cash
Card
Bank Transfer
QR Payment
Gift Voucher
Store Credit
```

------------------------------------------------------------------------

## Module 15: Exchange & Buyback

Responsibilities:

-   Old jewellery intake
-   Weight check
-   Purity testing
-   Valuation
-   Approval
-   Exchange
-   Buyback

Workflow:

``` text
Customer Old Jewellery
        |
Weight Check
        |
Purity Check
        |
Valuation
        |
Approval
        |
Exchange / Buyback
```

------------------------------------------------------------------------

## Module 16: Repair

Responsibilities:

-   Repair request
-   Condition recording
-   Inspection
-   Estimate
-   Customer approval
-   Repair tracking
-   Delivery

Entities:

``` text
RepairRequest
RepairInspection
RepairJobCard
RepairAssignment
RepairStatusHistory
```

Statuses:

``` text
RECEIVED
INSPECTION
ESTIMATION
APPROVAL_PENDING
IN_PROGRESS
QUALITY_CHECK
READY
DELIVERED
```

------------------------------------------------------------------------

## Module 17: CRM

Responsibilities:

-   Customer 360
-   Follow-up
-   Activities
-   Campaigns
-   Segmentation

Keep CRM behavior separate from the Customer master module.

------------------------------------------------------------------------

## Module 18: Loyalty

Responsibilities:

-   Loyalty programs
-   Tiers
-   Points
-   Transactions
-   Rewards

Example:

``` text
Silver
  |
Gold
  |
Platinum
```

------------------------------------------------------------------------

## Module 19: Warehouse & Vault

Responsibilities:

-   Vault
-   Tray
-   Bin
-   Issue/return
-   Stock verification
-   Dual authorization

Hierarchy:

``` text
Warehouse
├── Vault
├── Zone
├── Shelf
└── Tray
```

------------------------------------------------------------------------

## Module 20: Finance

Start with:

``` text
Cash
Bank
Receivables
Payables
Daily Closing
```

Later:

``` text
General Ledger
Journal Entries
COGS
Bank Reconciliation
Profit & Loss
Balance Sheet
```

------------------------------------------------------------------------

## Module 21: Notification

Responsibilities:

-   Email
-   SMS
-   Push notifications
-   Templates
-   Events

Examples:

``` text
SALE_COMPLETED
REPAIR_READY
PAYMENT_RECEIVED
ITEM_TRANSFERRED
LOW_STOCK
LOYALTY_POINTS_ADDED
```

Business modules should publish events instead of embedding notification
logic everywhere.

------------------------------------------------------------------------

## Module 22: Reporting

Reports:

-   Sales
-   Inventory
-   Customer
-   Branch
-   Gold
-   Finance

Initially query PostgreSQL.

Later:

``` text
Operational Database
        |
ETL / Events
        |
Data Warehouse
        |
BI Dashboard
```

------------------------------------------------------------------------

## Module 23: Audit & Compliance

Responsibilities:

-   Audit logs
-   Approval workflows
-   KYC
-   Transaction monitoring
-   Regulatory reports

Sensitive audit data should include:

``` text
User
Action
Entity Type
Entity ID
Old Value
New Value
Date
Time
Branch
```

------------------------------------------------------------------------

# 7. Module Dependency Architecture

``` text
Identity
   |
   +--------------------------+
   |                          |
   v                          v
Organization                Audit
   |
   v
Product -----+
Metal -------+
Gemstone ----+
             |
             v
       Jewellery Item
             |
             v
        Inventory
             |
    +--------+---------+
    |        |         |
    v        v         v
Procurement Pricing  Movement
                      |
                      v
                    Sales
                      |
             +--------+--------+
             |        |        |
             v        v        v
          Payment   CRM    Loyalty
             |
             v
          Finance

All business modules ---> Notification
All sensitive operations ---> Audit & Compliance
```

Dependency rules:

1.  Product does not depend on Sales.
2.  Inventory is the source of truth for physical item status and
    location.
3.  Sales changes inventory through controlled Inventory services.
4.  Payment does not directly change inventory.
5.  Pricing is independent from Sales.
6.  Finance consumes finalized business events.
7.  Notification reacts to business events.

------------------------------------------------------------------------

# 8. Digital Jewellery Passport

Every physical item should have a permanent digital identity.

``` text
UNIQUE JEWELLERY ITEM
        |
        v
DIGITAL ID
        |
  +-----+-----+
  |     |     |
 RFID   QR  Barcode
        |
        v
DIGITAL PASSPORT
        |
  +-----+-----------------------+
  |                             |
  v                             v
Product Details              Physical Details
                             Metal
                             Purity
                             Weight
                             Stones
                             Certificate
        |
        v
LIFECYCLE HISTORY
        |
Purchase / Manufacture
        |
Quality Check
        |
RFID Tagging
        |
Warehouse
        |
Showroom
        |
Customer Sale
        |
Repair / Exchange / Buyback
        |
Complete History
```

The Inventory module should own the physical item identity and current
state.

------------------------------------------------------------------------

# 9. Database Architecture

Start with one PostgreSQL database.

Recommended logical schemas:

``` text
identity
organization
product
inventory
procurement
sales
payment
customer
crm
finance
audit
```

Important rule:

> Each module owns its tables. Another module must not directly write
> into those tables.

------------------------------------------------------------------------

# 10. Cross-Cutting Architecture

## Global Exceptions

Create centralized exceptions:

``` text
BusinessException
NotFoundException
ValidationException
UnauthorizedException
ForbiddenException
ConflictException
```

Use a consistent API error response.

## Validation

Use Jakarta Validation:

``` text
@NotNull
@NotBlank
@Positive
@Size
```

## Auditing

Automatically capture:

``` text
createdAt
createdBy
updatedAt
updatedBy
```

## File Management

Store files in S3-compatible storage, not inside PostgreSQL:

``` text
Jewellery Images
Certificates
Customer Documents
Repair Photos
Invoices
Reports
```

Store file metadata and references in the database.

------------------------------------------------------------------------

# 11. API Design Guidelines

Use versioned REST APIs:

``` text
POST   /api/v1/products
GET    /api/v1/products/{id}
PUT    /api/v1/products/{id}
DELETE /api/v1/products/{id}
```

Inventory:

``` text
GET  /api/v1/inventory/items/{id}
POST /api/v1/inventory/transfers
POST /api/v1/inventory/reservations
POST /api/v1/inventory/stock-counts
```

Pricing:

``` text
POST /api/v1/pricing/calculate
```

Sales:

``` text
POST /api/v1/quotations
POST /api/v1/sales
POST /api/v1/sales/{id}/payments
```

Support:

-   Pagination
-   Filtering
-   Sorting
-   API versioning
-   Idempotency for critical create/payment operations
-   OpenAPI documentation

------------------------------------------------------------------------

# 12. Transaction and Concurrency Rules

Critical scenarios:

-   Two users must not sell the same item.
-   An item under repair cannot be transferred.
-   A reserved item cannot be sold to another customer.
-   Duplicate payment callbacks must not create duplicate payments.

Use:

``` text
Database Transactions
Optimistic Locking
Pessimistic Locking where justified
Unique Constraints
Idempotency Keys
Redis Locks only for distributed coordination
```

Use `BigDecimal` for money and precise weight calculations. Never use
floating point for money.

------------------------------------------------------------------------

# 13. Event Architecture

Initially use internal domain/application events.

Example:

``` text
SaleCompleted
    |
    +--> Update Inventory
    +--> Add Loyalty Points
    +--> Create Finance Entry
    +--> Send Notification
    +--> Create Audit Record
```

Later the same event model can be connected to Kafka or RabbitMQ.

------------------------------------------------------------------------

# 14. Recommended Development Roadmap

## Phase 1: Foundation

``` text
1. Project Setup
2. Shared/Common
3. Database Migration
4. Global Exception Handling
5. API Standards
6. Identity
7. Roles and Permissions
8. Organization
9. Audit
10. File Management
```

Completion criteria:

-   Authentication works.
-   Role and permission checks work.
-   Branch access works.
-   Organization structure exists.
-   Sensitive operations are audited.
-   External file storage works.

------------------------------------------------------------------------

## Phase 2: Core Jewellery Domain

``` text
11. Product
12. Metal
13. Purity
14. Gemstone
15. Jewellery Item
16. RFID / QR / Barcode
17. Inventory
18. Inventory Movement
```

Completion criteria:

-   Product designs can be created.
-   Metals and purities can be managed.
-   Stones and certificates can be recorded.
-   Unique jewellery items can be created.
-   Items can be tagged.
-   Items can be moved.
-   Current location and status are visible.
-   Full movement history is available.

------------------------------------------------------------------------

## Phase 3: Procurement & Commercial Operations

``` text
19. Supplier
20. Procurement
21. Pricing
22. Customer
23. Sales / POS APIs
24. Payment
```

------------------------------------------------------------------------

## Phase 4: Operational Workflows

``` text
25. Exchange
26. Buyback
27. Repair
28. Warehouse / Vault
29. Notification
```

------------------------------------------------------------------------

## Phase 5: Customer Growth

``` text
30. CRM
31. Loyalty
32. Promotion
33. Customer-facing APIs
```

------------------------------------------------------------------------

## Phase 6: Enterprise

``` text
34. Finance
35. Compliance
36. Reporting
37. BI
38. AI Features
```

------------------------------------------------------------------------

# 15. Recommended First Milestone

Build these modules first:

``` text
Identity
    |
Organization
    |
Product
    |
Metal
    |
Gemstone
    |
Jewellery Item
    |
Inventory
    |
Inventory Movement
```

This creates the foundation of the complete platform.

The central entity is:

``` text
JEWELLERY ITEM
```

It should connect to:

``` text
Product
Metal
Purity
Weight
Gemstone
Certificate
RFID
QR Code
Barcode
Location
Status
Movement History
Customer Ownership
Repair History
Exchange History
Buyback History
```

------------------------------------------------------------------------

# 16. Step-by-Step First Implementation

## Step 1: Create Spring Boot Project

Include:

-   Spring Web
-   Spring Data JPA
-   Spring Security
-   Validation
-   PostgreSQL Driver
-   Flyway
-   Actuator
-   OpenAPI/Swagger

## Step 2: Configure Environments

Create:

``` text
application.yml
application-local.yml
application-dev.yml
application-prod.yml
```

Never hardcode secrets.

## Step 3: Configure PostgreSQL and Flyway

Example migrations:

``` text
V1__create_identity_tables.sql
V2__create_organization_tables.sql
V3__create_product_tables.sql
```

Never manually change production schema outside migrations.

## Step 4: Build Shared Infrastructure

Implement:

-   Exception handling
-   Standard responses
-   Security foundation
-   Auditing
-   Logging and correlation IDs

## Step 5: Build Identity

Implement:

``` text
User
Role
Permission
Authentication
JWT
Refresh Token
```

## Step 6: Build Organization

Implement:

``` text
Company
Branch
Showroom
Warehouse
Vault
Counter
```

## Step 7: Build Product, Metal and Gemstone Masters

Create reusable master data before creating physical items.

## Step 8: Finalize JewelleryItem Design

Before coding, finalize:

-   Required weights
-   Cost structure
-   Serial strategy
-   RFID strategy
-   Stone assignment
-   Location model
-   Status transitions

## Step 9: Build Inventory

Inventory must be the source of truth for:

``` text
Current Location
Current Status
Reservation
Physical Availability
Movement History
```

## Step 10: Add Tests

Prioritize:

-   Pricing calculations
-   Status transitions
-   Inventory movement
-   Permission checks
-   Duplicate sale prevention
-   Concurrency tests

------------------------------------------------------------------------

# 17. Important Design Decisions

## Product vs Physical Item

Do not use a simple SKU + quantity inventory model.

Correct model:

``` text
Product Master
      |
      v
Jewellery Design
      |
      v
Unique Jewellery Item
      |
      v
Serialized Inventory
```

## Current State vs History

Store both:

``` text
Current State
```

for fast operational queries, and:

``` text
Movement / Event History
```

for traceability.

## Pricing vs Cost

Keep separate:

``` text
Purchase Cost
Manufacturing Cost
Metal Value
Making Charge
Stone Value
Tax
Selling Price
Discount
Final Sale Price
```

Do not overwrite historical sale prices when daily metal rates change.

------------------------------------------------------------------------

# 18. Definition of Done for Every Module

A module is not complete merely because CRUD APIs exist.

Each completed module should include:

-   Domain model
-   Database migration
-   Request validation
-   Business rules
-   Authorization
-   Audit behavior
-   Error handling
-   Unit tests
-   Integration tests where required
-   API documentation
-   Clear module boundaries

------------------------------------------------------------------------

# 19. Quality Standards

Use:

-   Constructor injection
-   DTOs at API boundaries
-   Explicit transactions
-   Flyway migrations
-   Meaningful domain exceptions
-   Pagination for list APIs
-   Database indexes for high-volume searches
-   UTC for backend timestamps
-   BigDecimal for money and weight where precision is required
-   Optimistic locking for critical mutable entities where appropriate

------------------------------------------------------------------------

# 20. Final Recommendation

Start with the foundation and core jewellery domain before building POS,
CRM or finance.

The recommended implementation path is:

``` text
Identity
    ->
Organization
    ->
Product / Metal / Gemstone
    ->
Jewellery Item
    ->
Inventory
    ->
Inventory Movement
```

The most important concept in the entire platform is the **unique
serialized Jewellery Item with complete lifecycle traceability**. Design
this carefully before expanding into procurement, pricing, sales,
repairs, exchange, CRM and finance.

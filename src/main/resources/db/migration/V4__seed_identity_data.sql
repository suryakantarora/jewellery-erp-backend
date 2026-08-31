-- Seed the permission catalogue and the baseline roles.
INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'USER_VIEW', 'IDENTITY', 'View users', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'USER_MANAGE', 'IDENTITY', 'Create, update and deactivate users', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'ROLE_VIEW', 'IDENTITY', 'View roles and permissions', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'ROLE_MANAGE', 'IDENTITY', 'Create and update roles', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'ORGANIZATION_VIEW', 'ORGANIZATION', 'View company, branch and location structure', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'ORGANIZATION_MANAGE', 'ORGANIZATION', 'Manage company, branch and location structure', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PRODUCT_VIEW', 'PRODUCT', 'View product master data', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PRODUCT_CREATE', 'PRODUCT', 'Create products and designs', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PRODUCT_UPDATE', 'PRODUCT', 'Update products and designs', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'METAL_VIEW', 'METAL', 'View metals, purities and rates', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'METAL_MANAGE', 'METAL', 'Manage metals and purities', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'METAL_RATE_PUBLISH', 'METAL', 'Publish daily metal rates', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'GEMSTONE_VIEW', 'GEMSTONE', 'View gemstones and certificates', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'GEMSTONE_MANAGE', 'GEMSTONE', 'Manage gemstones and certificates', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_VIEW', 'INVENTORY', 'View jewellery items and stock', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_CREATE', 'INVENTORY', 'Create serialized jewellery items', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_TRANSFER', 'INVENTORY', 'Raise inventory movements', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_TRANSFER_APPROVE', 'INVENTORY', 'Approve inventory movements', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_RESERVE', 'INVENTORY', 'Reserve and release items', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_ADJUST', 'INVENTORY', 'Adjust item status outside normal flow', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PRICE_CHANGE', 'PRICING', 'Change pricing parameters', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'SALE_CREATE', 'SALES', 'Create sales and invoices', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'DISCOUNT_REQUEST', 'SALES', 'Request a discount', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'DISCOUNT_APPROVE', 'SALES', 'Approve a discount', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'AUDIT_VIEW', 'AUDIT', 'View audit trail', now(), 'system', now(), 'system');

INSERT INTO identity.role (id, version, code, name, description, system_role, super_admin, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'SUPER_ADMIN', 'Super Administrator', 'Full, unrestricted access', true, true, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'BRANCH_MANAGER', 'Branch Manager', 'Runs a branch: stock, staff and approvals', true, false, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'SALES_EXECUTIVE', 'Sales Executive', 'Counter sales and customer handling', true, false, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'INVENTORY_OFFICER', 'Inventory Officer', 'Stock custody and movements', true, false, now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'AUDITOR', 'Auditor', 'Read-only access with full audit visibility', true, false, now(), 'system', now(), 'system');

-- SUPER_ADMIN holds every permission.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r CROSS JOIN identity.permission p WHERE r.code = 'SUPER_ADMIN';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'USER_VIEW','ROLE_VIEW','ORGANIZATION_VIEW','PRODUCT_VIEW','METAL_VIEW','GEMSTONE_VIEW',
    'INVENTORY_VIEW','INVENTORY_CREATE','INVENTORY_TRANSFER','INVENTORY_TRANSFER_APPROVE',
    'INVENTORY_RESERVE','SALE_CREATE','DISCOUNT_APPROVE','AUDIT_VIEW')
WHERE r.code = 'BRANCH_MANAGER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'PRODUCT_VIEW','METAL_VIEW','GEMSTONE_VIEW','INVENTORY_VIEW','INVENTORY_RESERVE',
    'SALE_CREATE','DISCOUNT_REQUEST')
WHERE r.code = 'SALES_EXECUTIVE';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'PRODUCT_VIEW','METAL_VIEW','GEMSTONE_VIEW','INVENTORY_VIEW','INVENTORY_CREATE',
    'INVENTORY_TRANSFER','INVENTORY_RESERVE','ORGANIZATION_VIEW')
WHERE r.code = 'INVENTORY_OFFICER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'USER_VIEW','ROLE_VIEW','ORGANIZATION_VIEW','PRODUCT_VIEW','METAL_VIEW','GEMSTONE_VIEW',
    'INVENTORY_VIEW','AUDIT_VIEW')
WHERE r.code = 'AUDITOR';

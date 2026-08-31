-- Permissions introduced by the commercial modules.
INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'SUPPLIER_VIEW', 'SUPPLIER', 'View suppliers', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'SUPPLIER_MANAGE', 'SUPPLIER', 'Create and update suppliers', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'CUSTOMER_VIEW', 'CUSTOMER', 'View customers', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'CUSTOMER_MANAGE', 'CUSTOMER', 'Register and update customers', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'CUSTOMER_KYC_VERIFY', 'CUSTOMER', 'Record KYC decisions', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PROCUREMENT_VIEW', 'PROCUREMENT', 'View requisitions, orders and receipts', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PROCUREMENT_CREATE', 'PROCUREMENT', 'Raise requisitions, orders and invoices', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PROCUREMENT_APPROVE', 'PROCUREMENT', 'Approve requisitions and purchase orders', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PROCUREMENT_RECEIVE', 'PROCUREMENT', 'Record and quality-check deliveries', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'SALE_VIEW', 'SALES', 'View quotations and sales', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'SALE_RETURN', 'SALES', 'Return sold items to stock', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PAYMENT_VIEW', 'PAYMENT', 'View payments', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PAYMENT_COLLECT', 'PAYMENT', 'Take payments against a sale', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PAYMENT_REFUND', 'PAYMENT', 'Refund a captured payment', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'PAYMENT_RECONCILE', 'PAYMENT', 'Reconcile payments against statements', now(), 'system', now(), 'system');

-- SUPER_ADMIN keeps every permission.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'SUPPLIER_VIEW','SUPPLIER_MANAGE','CUSTOMER_VIEW','CUSTOMER_MANAGE','CUSTOMER_KYC_VERIFY',
    'PROCUREMENT_VIEW','PROCUREMENT_CREATE','PROCUREMENT_APPROVE','PROCUREMENT_RECEIVE',
    'SALE_VIEW','SALE_RETURN','PAYMENT_VIEW','PAYMENT_COLLECT','PAYMENT_REFUND','PAYMENT_RECONCILE',
    'PRICE_CHANGE')
WHERE r.code = 'BRANCH_MANAGER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'CUSTOMER_VIEW','CUSTOMER_MANAGE','SALE_VIEW','PAYMENT_VIEW','PAYMENT_COLLECT')
WHERE r.code = 'SALES_EXECUTIVE';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'SUPPLIER_VIEW','PROCUREMENT_VIEW','PROCUREMENT_RECEIVE')
WHERE r.code = 'INVENTORY_OFFICER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'SUPPLIER_VIEW','CUSTOMER_VIEW','PROCUREMENT_VIEW','SALE_VIEW','PAYMENT_VIEW')
WHERE r.code = 'AUDITOR';

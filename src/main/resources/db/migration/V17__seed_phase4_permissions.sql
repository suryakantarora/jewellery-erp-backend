-- Permissions introduced by the operational modules.
INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'EXCHANGE_VIEW', 'EXCHANGE', 'View exchange and buyback intakes', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'EXCHANGE_PROCESS', 'EXCHANGE', 'Take in, weigh, test and settle old jewellery', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'EXCHANGE_VALUE', 'EXCHANGE', 'Value old jewellery at the buying rate', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'EXCHANGE_APPROVE', 'EXCHANGE', 'Approve or reject a valuation', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'REPAIR_VIEW', 'REPAIR', 'View repair jobs', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'REPAIR_PROCESS', 'REPAIR', 'Take in, work on and deliver repairs', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'REPAIR_ESTIMATE', 'REPAIR', 'Give a repair estimate', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'WAREHOUSE_VIEW', 'WAREHOUSE', 'View storage bins and stock counts', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'WAREHOUSE_MANAGE', 'WAREHOUSE', 'Manage storage bins', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'STOCK_COUNT_PERFORM', 'WAREHOUSE', 'Open and submit stock verifications', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'STOCK_COUNT_APPROVE', 'WAREHOUSE', 'Review and close stock verifications', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'NOTIFICATION_VIEW', 'NOTIFICATION', 'View notifications and templates', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'NOTIFICATION_MANAGE', 'NOTIFICATION', 'Manage notification templates', now(), 'system', now(), 'system');

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
    'EXCHANGE_VIEW','EXCHANGE_PROCESS','EXCHANGE_VALUE','EXCHANGE_APPROVE',
    'REPAIR_VIEW','REPAIR_PROCESS','REPAIR_ESTIMATE',
    'WAREHOUSE_VIEW','WAREHOUSE_MANAGE','STOCK_COUNT_PERFORM','STOCK_COUNT_APPROVE',
    'NOTIFICATION_VIEW','NOTIFICATION_MANAGE')
WHERE r.code = 'BRANCH_MANAGER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'EXCHANGE_VIEW','EXCHANGE_PROCESS','REPAIR_VIEW','REPAIR_PROCESS','NOTIFICATION_VIEW')
WHERE r.code = 'SALES_EXECUTIVE';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'WAREHOUSE_VIEW','WAREHOUSE_MANAGE','STOCK_COUNT_PERFORM','EXCHANGE_VIEW','REPAIR_VIEW')
WHERE r.code = 'INVENTORY_OFFICER';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'EXCHANGE_VIEW','REPAIR_VIEW','WAREHOUSE_VIEW','NOTIFICATION_VIEW')
WHERE r.code = 'AUDITOR';

INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'CRM_VIEW', 'CRM', 'View customer 360, activities and follow-ups', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'CRM_MANAGE', 'CRM', 'Log activities, raise and close follow-ups, manage segments', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'CAMPAIGN_MANAGE', 'CRM', 'Create and launch campaigns', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'LOYALTY_VIEW', 'LOYALTY', 'View loyalty programs, accounts and statements', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'LOYALTY_MANAGE', 'LOYALTY', 'Manage programs and enrol customers', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'LOYALTY_REDEEM', 'LOYALTY', 'Redeem points on behalf of a customer', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'LOYALTY_ADJUST', 'LOYALTY', 'Manually adjust a point balance', now(), 'system', now(), 'system');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'CRM_VIEW','CRM_MANAGE','CAMPAIGN_MANAGE',
    'LOYALTY_VIEW','LOYALTY_MANAGE','LOYALTY_REDEEM','LOYALTY_ADJUST')
WHERE r.code = 'BRANCH_MANAGER';

-- Counter staff can see and serve customers, but not adjust point balances.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'CRM_VIEW','CRM_MANAGE','LOYALTY_VIEW','LOYALTY_MANAGE','LOYALTY_REDEEM')
WHERE r.code = 'SALES_EXECUTIVE';

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code IN (
    'CRM_VIEW','LOYALTY_VIEW')
WHERE r.code = 'AUDITOR';

-- A default program so points work out of the box: 1 point per 10,000 spent,
-- each point worth 100 on redemption, valid for two years.
INSERT INTO crm.loyalty_program
    (id, version, code, name, points_per_currency_unit, currency_value_per_point,
     points_validity_months, minimum_redeemable_points, earn_on_making_charge_only,
     effective_from, active, created_at, created_by, updated_at, updated_by)
VALUES
    ('11111111-1111-1111-1111-111111111111', 0, 'STANDARD', 'Standard Loyalty Programme',
     0.0001, 100.0000, 24, 100, false, DATE '2000-01-01', true,
     now(), 'system', now(), 'system');

INSERT INTO crm.loyalty_tier
    (id, version, program_id, code, name, minimum_points, earn_multiplier, discount_percentage,
     display_order, benefits, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, '11111111-1111-1111-1111-111111111111', 'SILVER', 'Silver',
     0, 1.0000, NULL, 1, 'Standard earn rate', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '11111111-1111-1111-1111-111111111111', 'GOLD', 'Gold',
     5000, 1.2500, 2.0000, 2, '25% bonus points and 2% standing discount',
     now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '11111111-1111-1111-1111-111111111111', 'PLATINUM', 'Platinum',
     20000, 1.5000, 5.0000, 3, '50% bonus points and 5% standing discount',
     now(), 'system', now(), 'system');

-- Templates for the events the CRM and loyalty modules raise.
INSERT INTO public.notification_template
    (id, version, code, event_type, channel, locale, subject, body, active,
     created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'CAMPAIGN_TARGETED_SMS', 'CAMPAIGN_TARGETED', 'SMS', 'en', NULL,
     'Dear {{customerName}}, {{campaignName}} is on now. Visit us to find out more.',
     true, now(), 'system', now(), 'system');

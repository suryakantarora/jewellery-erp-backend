INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'REPORT_VIEW', 'REPORTING', 'View operational reports', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'COMPLIANCE_REPORT', 'COMPLIANCE', 'Run regulatory and control reports', now(), 'system', now(), 'system');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'REPORT_VIEW'
WHERE r.code = 'BRANCH_MANAGER';

-- Compliance reporting is an audit function, so the auditor gets both.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p
    ON p.code IN ('REPORT_VIEW', 'COMPLIANCE_REPORT')
WHERE r.code = 'AUDITOR';

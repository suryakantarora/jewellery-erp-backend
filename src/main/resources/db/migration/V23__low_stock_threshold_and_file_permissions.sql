-- A location is only monitored for low stock once a threshold is set; null
-- means the location is deliberately not watched.
ALTER TABLE organization.location
    ADD COLUMN low_stock_threshold integer
        CHECK (low_stock_threshold IS NULL OR low_stock_threshold >= 0);

CREATE INDEX ix_location_monitored ON organization.location (low_stock_threshold)
    WHERE low_stock_threshold IS NOT NULL;

INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'FILE_UPLOAD', 'PLATFORM', 'Upload documents and images', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'FILE_DOWNLOAD', 'PLATFORM', 'Download stored files', now(), 'system', now(), 'system');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

-- Anyone who records a document or photograph needs to be able to upload one.
INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p
    ON p.code IN ('FILE_UPLOAD', 'FILE_DOWNLOAD')
WHERE r.code IN ('BRANCH_MANAGER', 'SALES_EXECUTIVE', 'INVENTORY_OFFICER');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'FILE_DOWNLOAD'
WHERE r.code = 'AUDITOR';

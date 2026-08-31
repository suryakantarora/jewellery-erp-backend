CREATE TABLE finance.account (
    id             uuid PRIMARY KEY,
    version        bigint      NOT NULL DEFAULT 0,
    code           varchar(20)  NOT NULL,
    name           varchar(150) NOT NULL,
    account_type   varchar(20)  NOT NULL,
    parent_id      uuid        REFERENCES finance.account (id),
    postable       boolean     NOT NULL DEFAULT true,
    system_account boolean     NOT NULL DEFAULT false,
    company_id     uuid        REFERENCES organization.company (id),
    currency       varchar(3)   NOT NULL DEFAULT 'LAK',
    description    varchar(500),
    active         boolean     NOT NULL DEFAULT true,
    created_at     timestamptz NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT uq_account_code UNIQUE (code)
);

CREATE INDEX ix_account_type ON finance.account (account_type, code);

CREATE TABLE finance.journal_entry (
    id             uuid PRIMARY KEY,
    version        bigint      NOT NULL DEFAULT 0,
    entry_number   varchar(50)  NOT NULL,
    entry_date     date        NOT NULL,
    source         varchar(30)  NOT NULL,
    status         varchar(20)  NOT NULL,
    description    varchar(500) NOT NULL,
    reference_type varchar(50),
    reference_id   varchar(100),
    branch_id      uuid        REFERENCES organization.branch (id),
    currency       varchar(3)   NOT NULL DEFAULT 'LAK',
    total_debit    numeric(19,4) NOT NULL DEFAULT 0,
    total_credit   numeric(19,4) NOT NULL DEFAULT 0,
    posted_at      timestamptz,
    posted_by      varchar(100),
    reversal_of_id uuid        REFERENCES finance.journal_entry (id),
    reversed_by_id uuid        REFERENCES finance.journal_entry (id),
    created_at     timestamptz NOT NULL,
    created_by     varchar(100),
    updated_at     timestamptz NOT NULL,
    updated_by     varchar(100),
    CONSTRAINT uq_journal_entry_number UNIQUE (entry_number),
    -- An entry that does not balance is not an entry.
    CONSTRAINT ck_journal_balanced CHECK (total_debit = total_credit)
);

-- One posted entry per business document per source: the database guarantee
-- behind "a redelivered event must not double the ledger".
CREATE UNIQUE INDEX uq_journal_posted_reference
    ON finance.journal_entry (reference_type, reference_id, source)
    WHERE status = 'POSTED' AND reference_id IS NOT NULL;
CREATE INDEX ix_journal_date ON finance.journal_entry (entry_date DESC, status);
CREATE INDEX ix_journal_source ON finance.journal_entry (source, entry_date DESC);

CREATE TABLE finance.journal_entry_line (
    id               uuid PRIMARY KEY,
    version          bigint      NOT NULL DEFAULT 0,
    journal_entry_id uuid        NOT NULL REFERENCES finance.journal_entry (id) ON DELETE CASCADE,
    account_id       uuid        NOT NULL REFERENCES finance.account (id),
    debit            numeric(19,4) NOT NULL DEFAULT 0,
    credit           numeric(19,4) NOT NULL DEFAULT 0,
    description      varchar(500),
    party_type       varchar(20),
    party_id         uuid,
    line_order       integer     NOT NULL DEFAULT 0,
    created_at       timestamptz NOT NULL,
    created_by       varchar(100),
    updated_at       timestamptz NOT NULL,
    updated_by       varchar(100),
    CONSTRAINT ck_line_not_negative CHECK (debit >= 0 AND credit >= 0),
    -- A line is a debit or a credit, never both and never neither.
    CONSTRAINT ck_line_one_side CHECK ((debit > 0 AND credit = 0) OR (credit > 0 AND debit = 0))
);

CREATE INDEX ix_journal_line_entry ON finance.journal_entry_line (journal_entry_id);
CREATE INDEX ix_journal_line_account ON finance.journal_entry_line (account_id);
CREATE INDEX ix_journal_line_party ON finance.journal_entry_line (party_type, party_id)
    WHERE party_id IS NOT NULL;

-- The chart of accounts the automatic postings depend on. Marked as system
-- accounts so they cannot be deactivated out from under the posting rules.
INSERT INTO finance.account
    (id, version, code, name, account_type, postable, system_account, currency,
     created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, '1010', 'Cash on hand',            'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '1020', 'Bank',                    'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '1030', 'Card clearing',           'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '1100', 'Accounts receivable',     'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '1200', 'Jewellery inventory',     'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '1210', 'Scrap metal',             'ASSET',     true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '2010', 'Accounts payable',        'LIABILITY', true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '2020', 'Tax payable',             'LIABILITY', true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '2100', 'Customer deposits',       'LIABILITY', true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '2110', 'Loyalty points liability','LIABILITY', true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '2120', 'Gift voucher liability',  'LIABILITY', true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '3010', 'Share capital',           'EQUITY',    true, false,'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '4010', 'Sales revenue',           'INCOME',    true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '5010', 'Cost of goods sold',      'EXPENSE',   true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '5200', 'Loyalty programme cost',  'EXPENSE',   true, true, 'LAK', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, '5300', 'Inventory adjustment',    'EXPENSE',   true, true, 'LAK', now(), 'system', now(), 'system');

INSERT INTO identity.permission (id, version, code, module, description, created_at, created_by, updated_at, updated_by)
VALUES
    (gen_random_uuid(), 0, 'FINANCE_VIEW', 'FINANCE', 'View the ledger and financial statements', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'FINANCE_MANAGE', 'FINANCE', 'Manage the chart of accounts', now(), 'system', now(), 'system'),
    (gen_random_uuid(), 0, 'FINANCE_POST', 'FINANCE', 'Post and reverse manual journal entries', now(), 'system', now(), 'system');

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM identity.role r
CROSS JOIN identity.permission p
WHERE r.code = 'SUPER_ADMIN'
  AND NOT EXISTS (SELECT 1 FROM identity.role_permission rp
                  WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO identity.role_permission (role_id, permission_id)
SELECT r.id, p.id FROM identity.role r JOIN identity.permission p ON p.code = 'FINANCE_VIEW'
WHERE r.code IN ('BRANCH_MANAGER', 'AUDITOR');

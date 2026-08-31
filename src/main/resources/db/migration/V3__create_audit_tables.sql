CREATE TABLE audit.audit_log (
    id             uuid PRIMARY KEY,
    user_id        uuid,
    username       varchar(100),
    action         varchar(100) NOT NULL,
    entity_type    varchar(100) NOT NULL,
    entity_id      varchar(100),
    old_value      text,
    new_value      text,
    branch_id      uuid,
    correlation_id varchar(64),
    ip_address     varchar(64),
    occurred_at    timestamptz NOT NULL
);

CREATE INDEX ix_audit_log_entity ON audit.audit_log (entity_type, entity_id);
CREATE INDEX ix_audit_log_user ON audit.audit_log (user_id, occurred_at DESC);
CREATE INDEX ix_audit_log_occurred ON audit.audit_log (occurred_at DESC);

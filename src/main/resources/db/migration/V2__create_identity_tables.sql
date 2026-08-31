CREATE TABLE identity.permission (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    code            varchar(100) NOT NULL,
    module          varchar(50)  NOT NULL,
    description     varchar(255),
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100),
    CONSTRAINT uq_permission_code UNIQUE (code)
);

CREATE TABLE identity.role (
    id              uuid PRIMARY KEY,
    version         bigint      NOT NULL DEFAULT 0,
    code            varchar(50)  NOT NULL,
    name            varchar(100) NOT NULL,
    description     varchar(255),
    system_role     boolean     NOT NULL DEFAULT false,
    super_admin     boolean     NOT NULL DEFAULT false,
    created_at      timestamptz NOT NULL,
    created_by      varchar(100),
    updated_at      timestamptz NOT NULL,
    updated_by      varchar(100),
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE identity.role_permission (
    role_id         uuid NOT NULL REFERENCES identity.role (id) ON DELETE CASCADE,
    permission_id   uuid NOT NULL REFERENCES identity.permission (id) ON DELETE CASCADE,
    PRIMARY KEY (role_id, permission_id)
);

CREATE TABLE identity.app_user (
    id                    uuid PRIMARY KEY,
    version               bigint      NOT NULL DEFAULT 0,
    username              varchar(100) NOT NULL,
    password_hash         varchar(255) NOT NULL,
    full_name             varchar(150) NOT NULL,
    email                 varchar(150),
    phone                 varchar(30),
    employee_code         varchar(50),
    status                varchar(20)  NOT NULL,
    primary_branch_id     uuid,
    must_change_password  boolean     NOT NULL DEFAULT false,
    failed_login_attempts integer     NOT NULL DEFAULT 0,
    locked_until          timestamptz,
    last_login_at         timestamptz,
    password_changed_at   timestamptz,
    created_at            timestamptz NOT NULL,
    created_by            varchar(100),
    updated_at            timestamptz NOT NULL,
    updated_by            varchar(100),
    CONSTRAINT uq_app_user_username UNIQUE (username)
);

CREATE UNIQUE INDEX uq_app_user_email ON identity.app_user (lower(email)) WHERE email IS NOT NULL;
CREATE INDEX ix_app_user_status ON identity.app_user (status);

CREATE TABLE identity.user_role (
    user_id  uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    role_id  uuid NOT NULL REFERENCES identity.role (id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE TABLE identity.user_branch (
    user_id   uuid NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    branch_id uuid NOT NULL,
    PRIMARY KEY (user_id, branch_id)
);

CREATE TABLE identity.refresh_token (
    id             uuid PRIMARY KEY,
    user_id        uuid        NOT NULL REFERENCES identity.app_user (id) ON DELETE CASCADE,
    token_hash     varchar(128) NOT NULL,
    issued_at      timestamptz NOT NULL,
    expires_at     timestamptz NOT NULL,
    revoked_at     timestamptz,
    replaced_by_id uuid,
    user_agent     varchar(255),
    ip_address     varchar(64),
    CONSTRAINT uq_refresh_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_refresh_token_user ON identity.refresh_token (user_id);
CREATE INDEX ix_refresh_token_expires ON identity.refresh_token (expires_at);

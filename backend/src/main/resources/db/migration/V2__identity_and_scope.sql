CREATE TABLE iam_user_account (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_iam_user_account PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    login_normalized NVARCHAR(254) NOT NULL,
    password_hash NVARCHAR(255) NOT NULL,
    status VARCHAR(16) NOT NULL,
    auth_version BIGINT NOT NULL CONSTRAINT DF_iam_user_account_auth_version DEFAULT 1,
    entity_version BIGINT NOT NULL CONSTRAINT DF_iam_user_account_entity_version DEFAULT 0,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_iam_user_account_public_id UNIQUE (public_id),
    CONSTRAINT UQ_iam_user_account_login UNIQUE (login_normalized),
    CONSTRAINT CK_iam_user_account_login_trimmed CHECK (login_normalized = LTRIM(RTRIM(login_normalized))),
    CONSTRAINT CK_iam_user_account_status CHECK (status IN ('ENABLED', 'DISABLED')),
    CONSTRAINT CK_iam_user_account_auth_version CHECK (auth_version >= 1)
);

CREATE TABLE iam_permission (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_iam_permission PRIMARY KEY,
    code VARCHAR(64) NOT NULL CONSTRAINT UQ_iam_permission_code UNIQUE
);

CREATE TABLE iam_role_bundle (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_iam_role_bundle PRIMARY KEY,
    code VARCHAR(32) NOT NULL CONSTRAINT UQ_iam_role_bundle_code UNIQUE
);

CREATE TABLE iam_role_permission (
    role_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    CONSTRAINT PK_iam_role_permission PRIMARY KEY (role_id, permission_id),
    CONSTRAINT FK_iam_role_permission_role FOREIGN KEY (role_id) REFERENCES iam_role_bundle(id),
    CONSTRAINT FK_iam_role_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission(id)
);

CREATE TABLE iam_account_role (
    account_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    CONSTRAINT PK_iam_account_role PRIMARY KEY (account_id, role_id),
    CONSTRAINT FK_iam_account_role_account FOREIGN KEY (account_id) REFERENCES iam_user_account(id),
    CONSTRAINT FK_iam_account_role_role FOREIGN KEY (role_id) REFERENCES iam_role_bundle(id)
);

CREATE TABLE iam_account_permission (
    account_id BIGINT NOT NULL,
    permission_id BIGINT NOT NULL,
    granted_at DATETIME2(6) NOT NULL,
    CONSTRAINT PK_iam_account_permission PRIMARY KEY (account_id, permission_id),
    CONSTRAINT FK_iam_account_permission_account FOREIGN KEY (account_id) REFERENCES iam_user_account(id),
    CONSTRAINT FK_iam_account_permission_permission FOREIGN KEY (permission_id) REFERENCES iam_permission(id)
);

CREATE TABLE org_branch (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_branch PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    code VARCHAR(32) NOT NULL,
    name NVARCHAR(120) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_org_branch_enabled DEFAULT 1,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_org_branch_public_id UNIQUE (public_id),
    CONSTRAINT UQ_org_branch_code UNIQUE (code)
);

CREATE TABLE org_location (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_org_location PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    branch_id BIGINT NOT NULL,
    code VARCHAR(32) NOT NULL,
    name NVARCHAR(120) NOT NULL,
    enabled BIT NOT NULL CONSTRAINT DF_org_location_enabled DEFAULT 1,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_org_location_public_id UNIQUE (public_id),
    CONSTRAINT UQ_org_location_branch_code UNIQUE (branch_id, code),
    CONSTRAINT UQ_org_location_id_branch UNIQUE (id, branch_id),
    CONSTRAINT FK_org_location_branch FOREIGN KEY (branch_id) REFERENCES org_branch(id)
);

CREATE TABLE iam_staff_assignment (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_iam_staff_assignment PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    account_id BIGINT NOT NULL,
    branch_id BIGINT NOT NULL,
    location_id BIGINT NULL,
    active BIT NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    updated_at DATETIME2(6) NOT NULL,
    CONSTRAINT UQ_iam_staff_assignment_public_id UNIQUE (public_id),
    CONSTRAINT UQ_iam_staff_assignment_scope UNIQUE (account_id, branch_id, location_id),
    CONSTRAINT FK_iam_staff_assignment_account FOREIGN KEY (account_id) REFERENCES iam_user_account(id),
    CONSTRAINT FK_iam_staff_assignment_branch FOREIGN KEY (branch_id) REFERENCES org_branch(id),
    CONSTRAINT FK_iam_staff_assignment_location_branch FOREIGN KEY (location_id, branch_id)
        REFERENCES org_location(id, branch_id)
);

CREATE INDEX IX_iam_staff_assignment_account_active
    ON iam_staff_assignment(account_id, active, branch_id, location_id);

CREATE TABLE audit_event (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_audit_event PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL,
    actor_type VARCHAR(16) NOT NULL,
    actor_account_id BIGINT NULL,
    action VARCHAR(64) NOT NULL,
    resource_type VARCHAR(64) NOT NULL,
    resource_public_id UNIQUEIDENTIFIER NULL,
    branch_id BIGINT NULL,
    location_id BIGINT NULL,
    correlation_id VARCHAR(64) NULL,
    occurred_at DATETIME2(6) NOT NULL,
    result VARCHAR(16) NOT NULL,
    details_json NVARCHAR(MAX) NOT NULL,
    CONSTRAINT UQ_audit_event_public_id UNIQUE (public_id),
    CONSTRAINT FK_audit_event_actor FOREIGN KEY (actor_account_id) REFERENCES iam_user_account(id),
    CONSTRAINT FK_audit_event_branch FOREIGN KEY (branch_id) REFERENCES org_branch(id),
    CONSTRAINT FK_audit_event_location FOREIGN KEY (location_id) REFERENCES org_location(id),
    CONSTRAINT CK_audit_event_actor_type CHECK (actor_type IN ('HUMAN', 'SERVICE', 'INTEGRATION', 'SYSTEM')),
    CONSTRAINT CK_audit_event_result CHECK (result IN ('SUCCESS', 'FAILURE')),
    CONSTRAINT CK_audit_event_details_json CHECK (ISJSON(details_json) = 1)
);

CREATE INDEX IX_audit_event_actor_time ON audit_event(actor_account_id, occurred_at);
CREATE INDEX IX_audit_event_scope_time ON audit_event(branch_id, location_id, occurred_at);

INSERT INTO iam_permission(code) VALUES
    ('IDENTITY_MANAGE'),
    ('CATALOG_MANAGE'),
    ('PRICE_MANAGE'),
    ('INVENTORY_VIEW'),
    ('INVENTORY_ADJUST'),
    ('POS_SELL'),
    ('FULFILL_PICKUP'),
    ('ORDER_VIEW_SCOPED'),
    ('ORDER_CANCEL'),
    ('REPORT_VIEW'),
    ('PAYMENT_EVENT_APPLY');

INSERT INTO iam_role_bundle(code) VALUES
    ('CUSTOMER'),
    ('CASHIER'),
    ('OPERATIONS'),
    ('ADMINISTRATOR'),
    ('PROVIDER');

INSERT INTO iam_role_permission(role_id, permission_id)
SELECT roles.id, permissions.id
FROM iam_role_bundle roles
JOIN iam_permission permissions ON
       (roles.code = 'CASHIER' AND permissions.code IN ('POS_SELL', 'ORDER_VIEW_SCOPED'))
    OR (roles.code = 'OPERATIONS' AND permissions.code IN (
        'CATALOG_MANAGE', 'PRICE_MANAGE', 'INVENTORY_VIEW', 'INVENTORY_ADJUST',
        'FULFILL_PICKUP', 'ORDER_VIEW_SCOPED', 'ORDER_CANCEL', 'REPORT_VIEW'))
    OR (roles.code = 'ADMINISTRATOR' AND permissions.code = 'IDENTITY_MANAGE')
    OR (roles.code = 'PROVIDER' AND permissions.code = 'PAYMENT_EVENT_APPLY');

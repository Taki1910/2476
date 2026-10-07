CREATE TABLE product_presentation_revision (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_product_presentation_revision PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_product_presentation_revision_public UNIQUE,
    product_id BIGINT NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    summary_vi NVARCHAR(500) NULL,
    summary_en NVARCHAR(500) NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_product_presentation_revision_version DEFAULT 0,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    CONSTRAINT FK_product_presentation_product FOREIGN KEY(product_id) REFERENCES catalog_product(id),
    CONSTRAINT FK_product_presentation_creator FOREIGN KEY(created_by_account_public_id) REFERENCES iam_user_account(public_id),
    CONSTRAINT UQ_product_presentation_revision_number UNIQUE(product_id, revision_number),
    CONSTRAINT CK_product_presentation_revision_number CHECK(revision_number > 0),
    CONSTRAINT CK_product_presentation_version CHECK(entity_version >= 0),
    CONSTRAINT CK_product_presentation_status CHECK(status IN ('DRAFT','PUBLISHED','RETIRED')),
    CONSTRAINT CK_product_presentation_summary_vi CHECK(summary_vi IS NULL OR LEN(LTRIM(RTRIM(summary_vi))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_summary_en CHECK(summary_en IS NULL OR LEN(LTRIM(RTRIM(summary_en))) BETWEEN 1 AND 500),
    CONSTRAINT CK_product_presentation_lifecycle CHECK(
        (status='DRAFT' AND published_at IS NULL AND retired_at IS NULL)
        OR (status='PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL AND summary_vi IS NOT NULL AND summary_en IS NOT NULL)
        OR (status='RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL AND summary_vi IS NOT NULL AND summary_en IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_product_presentation_one_draft
    ON product_presentation_revision(product_id) WHERE status='DRAFT';
CREATE UNIQUE INDEX UX_product_presentation_one_published
    ON product_presentation_revision(product_id) WHERE status='PUBLISHED';

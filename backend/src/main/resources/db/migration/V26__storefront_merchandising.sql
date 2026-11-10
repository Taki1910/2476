CREATE TABLE storefront_page_revision (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_storefront_page_revision PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_storefront_page_revision_public UNIQUE,
    page_key VARCHAR(16) NOT NULL,
    revision_number INT NOT NULL,
    status VARCHAR(16) NOT NULL,
    entity_version BIGINT NOT NULL CONSTRAINT DF_storefront_page_revision_version DEFAULT 0,
    created_by_account_public_id UNIQUEIDENTIFIER NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    published_at DATETIME2(6) NULL,
    retired_at DATETIME2(6) NULL,
    CONSTRAINT UQ_storefront_page_revision_number UNIQUE(page_key, revision_number),
    CONSTRAINT FK_storefront_page_revision_creator FOREIGN KEY(created_by_account_public_id)
        REFERENCES iam_user_account(public_id),
    CONSTRAINT CK_storefront_page_revision_page CHECK(page_key = 'HOME'),
    CONSTRAINT CK_storefront_page_revision_number CHECK(revision_number > 0),
    CONSTRAINT CK_storefront_page_revision_version CHECK(entity_version >= 0),
    CONSTRAINT CK_storefront_page_revision_lifecycle CHECK(
        (status = 'DRAFT' AND published_at IS NULL AND retired_at IS NULL)
        OR (status = 'PUBLISHED' AND published_at IS NOT NULL AND retired_at IS NULL)
        OR (status = 'RETIRED' AND published_at IS NOT NULL AND retired_at IS NOT NULL)
    )
);

CREATE UNIQUE INDEX UX_storefront_home_draft
    ON storefront_page_revision(page_key) WHERE status = 'DRAFT';
CREATE UNIQUE INDEX UX_storefront_home_published
    ON storefront_page_revision(page_key) WHERE status = 'PUBLISHED';

CREATE TABLE storefront_section (
    id BIGINT IDENTITY(1,1) NOT NULL CONSTRAINT PK_storefront_section PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL CONSTRAINT UQ_storefront_section_public UNIQUE,
    page_revision_id BIGINT NOT NULL,
    position INT NOT NULL,
    section_type VARCHAR(32) NOT NULL,
    is_enabled BIT NOT NULL,
    title_vi NVARCHAR(120) NOT NULL,
    title_en NVARCHAR(120) NOT NULL,
    supporting_text_vi NVARCHAR(500) NULL,
    supporting_text_en NVARCHAR(500) NULL,
    cta_label_vi NVARCHAR(80) NOT NULL,
    cta_label_en NVARCHAR(80) NOT NULL,
    cta_kind VARCHAR(24) NOT NULL,
    cta_target_public_id UNIQUEIDENTIFIER NULL,
    cta_search_term NVARCHAR(80) NULL,
    promotion_family_public_id UNIQUEIDENTIFIER NULL,
    media_key VARCHAR(32) NULL,
    CONSTRAINT UQ_storefront_section_position UNIQUE(page_revision_id, position),
    CONSTRAINT FK_storefront_section_revision FOREIGN KEY(page_revision_id)
        REFERENCES storefront_page_revision(id) ON DELETE CASCADE,
    CONSTRAINT FK_storefront_section_promotion FOREIGN KEY(promotion_family_public_id)
        REFERENCES promotion_family(public_id),
    CONSTRAINT CK_storefront_section_position CHECK(position BETWEEN 0 AND 7),
    CONSTRAINT CK_storefront_section_type CHECK(section_type IN ('HERO_BANNER','PRODUCT_COLLECTION','PROMOTION_SPOTLIGHT')),
    CONSTRAINT CK_storefront_section_copy CHECK(
        LEN(LTRIM(RTRIM(title_vi))) BETWEEN 1 AND 120
        AND LEN(LTRIM(RTRIM(title_en))) BETWEEN 1 AND 120
        AND LEN(LTRIM(RTRIM(cta_label_vi))) BETWEEN 1 AND 80
        AND LEN(LTRIM(RTRIM(cta_label_en))) BETWEEN 1 AND 80
        AND (supporting_text_vi IS NULL OR LEN(LTRIM(RTRIM(supporting_text_vi))) BETWEEN 1 AND 500)
        AND (supporting_text_en IS NULL OR LEN(LTRIM(RTRIM(supporting_text_en))) BETWEEN 1 AND 500)
        AND ((supporting_text_vi IS NULL AND supporting_text_en IS NULL)
             OR (supporting_text_vi IS NOT NULL AND supporting_text_en IS NOT NULL))
    ),
    CONSTRAINT CK_storefront_section_cta CHECK(
        (cta_kind IN ('PRODUCT_DETAIL','PROMOTION_DETAIL') AND cta_target_public_id IS NOT NULL AND cta_search_term IS NULL)
        OR (cta_kind = 'CATALOG_SEARCH' AND cta_target_public_id IS NULL
            AND LEN(LTRIM(RTRIM(cta_search_term))) BETWEEN 1 AND 80)
        OR (cta_kind = 'OFFERS' AND cta_target_public_id IS NULL AND cta_search_term IS NULL)
    ),
    CONSTRAINT CK_storefront_section_media CHECK(
        media_key IS NULL OR media_key IN ('COURT_CLASSIC','METRO_RUNNER','STUDIO_LOW','TRAIL_FORM',
            'AFTER_DARK','DAILY_CANVAS','COURT_HIGH','PACE_KNIT')
    ),
    CONSTRAINT CK_storefront_section_shape CHECK(
        (section_type = 'HERO_BANNER' AND media_key IS NOT NULL AND promotion_family_public_id IS NULL)
        OR (section_type = 'PRODUCT_COLLECTION' AND media_key IS NULL AND promotion_family_public_id IS NULL)
        OR (section_type = 'PROMOTION_SPOTLIGHT' AND media_key IS NULL
            AND promotion_family_public_id IS NOT NULL AND cta_kind = 'PROMOTION_DETAIL'
            AND cta_target_public_id = promotion_family_public_id)
    )
);

CREATE UNIQUE INDEX UX_storefront_revision_hero
    ON storefront_section(page_revision_id) WHERE section_type = 'HERO_BANNER';

CREATE TABLE storefront_collection_product (
    section_id BIGINT NOT NULL,
    product_public_id UNIQUEIDENTIFIER NOT NULL,
    position INT NOT NULL,
    CONSTRAINT PK_storefront_collection_product PRIMARY KEY(section_id, product_public_id),
    CONSTRAINT UQ_storefront_collection_product_position UNIQUE(section_id, position),
    CONSTRAINT FK_storefront_collection_product_section FOREIGN KEY(section_id)
        REFERENCES storefront_section(id) ON DELETE CASCADE,
    CONSTRAINT FK_storefront_collection_product_product FOREIGN KEY(product_public_id)
        REFERENCES catalog_product(public_id),
    CONSTRAINT CK_storefront_collection_product_position CHECK(position BETWEEN 0 AND 7)
);

INSERT INTO iam_permission(code)
SELECT 'STOREFRONT_MANAGE'
WHERE NOT EXISTS (SELECT 1 FROM iam_permission WHERE code = 'STOREFRONT_MANAGE');

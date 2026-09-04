CREATE TABLE catalog_shoe_fit_profile (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    public_id UNIQUEIDENTIFIER NOT NULL DEFAULT NEWSEQUENTIALID(),
    product_id BIGINT NOT NULL,
    size_system VARCHAR(16) NOT NULL,
    fit_tendency VARCHAR(24) NOT NULL,
    width_profile VARCHAR(16) NOT NULL,
    created_at DATETIME2(6) NOT NULL,
    CONSTRAINT uq_catalog_shoe_fit_profile_public UNIQUE (public_id),
    CONSTRAINT uq_catalog_shoe_fit_profile_product UNIQUE (product_id),
    CONSTRAINT fk_catalog_shoe_fit_profile_product FOREIGN KEY (product_id) REFERENCES catalog_product(id),
    CONSTRAINT ck_catalog_shoe_fit_profile_size_system CHECK (size_system IN ('EU')),
    CONSTRAINT ck_catalog_shoe_fit_profile_tendency CHECK (fit_tendency IN ('TRUE_TO_SIZE', 'RUNS_SMALL', 'RUNS_LARGE')),
    CONSTRAINT ck_catalog_shoe_fit_profile_width CHECK (width_profile IN ('NARROW', 'REGULAR', 'WIDE'))
);

CREATE TABLE catalog_shoe_fit_size_range (
    id BIGINT IDENTITY(1,1) NOT NULL PRIMARY KEY,
    profile_id BIGINT NOT NULL,
    size_label VARCHAR(32) NOT NULL,
    min_foot_length_mm DECIMAL(6,2) NOT NULL,
    max_foot_length_mm DECIMAL(6,2) NOT NULL,
    min_foot_width_mm DECIMAL(6,2) NOT NULL,
    max_foot_width_mm DECIMAL(6,2) NOT NULL,
    CONSTRAINT fk_catalog_shoe_fit_size_range_profile FOREIGN KEY (profile_id) REFERENCES catalog_shoe_fit_profile(id) ON DELETE CASCADE,
    CONSTRAINT uq_catalog_shoe_fit_size_range_size UNIQUE (profile_id, size_label),
    CONSTRAINT ck_catalog_shoe_fit_size_range_length CHECK (
        min_foot_length_mm >= 150 AND max_foot_length_mm <= 350 AND min_foot_length_mm < max_foot_length_mm
    ),
    CONSTRAINT ck_catalog_shoe_fit_size_range_width CHECK (
        min_foot_width_mm >= 50 AND max_foot_width_mm <= 160 AND min_foot_width_mm < max_foot_width_mm
    )
);

CREATE INDEX ix_catalog_shoe_fit_size_range_profile ON catalog_shoe_fit_size_range(profile_id, min_foot_length_mm, max_foot_length_mm);

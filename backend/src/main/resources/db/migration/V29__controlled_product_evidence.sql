ALTER TABLE catalog_product ADD
    intended_use VARCHAR(24) NULL,
    primary_surface VARCHAR(24) NULL,
    upper_construction VARCHAR(48) NULL,
    cut_profile VARCHAR(16) NULL,
    primary_sole_profile VARCHAR(24) NULL;

GO

ALTER TABLE catalog_product ADD
    CONSTRAINT CK_catalog_product_intended_use CHECK (
        intended_use IS NULL OR intended_use IN ('CASUAL_DAILY','ROAD_RUNNING','TRAIL_WALKING','TRAIL_RUNNING')),
    CONSTRAINT CK_catalog_product_primary_surface CHECK (
        primary_surface IS NULL OR primary_surface IN ('SMOOTH_HARD_SURFACE','ROAD_TRACK','MIXED_TRAIL')),
    CONSTRAINT CK_catalog_product_upper_construction CHECK (
        upper_construction IS NULL OR upper_construction IN (
            'SYNTHETIC_LEATHER','CANVAS_TEXTILE','MESH_TEXTILE','KNIT_TEXTILE','WOVEN_TEXTILE',
            'MESH_TEXTILE_WITH_SYNTHETIC_OVERLAYS')),
    CONSTRAINT CK_catalog_product_cut_profile CHECK (
        cut_profile IS NULL OR cut_profile IN ('LOW_TOP','HIGH_TOP')),
    CONSTRAINT CK_catalog_product_primary_sole_profile CHECK (
        primary_sole_profile IS NULL OR primary_sole_profile IN ('CUPSOLE','FLEX_GROOVED','LUGGED'));

ALTER TABLE catalog_product ADD
    category NVARCHAR(32) NULL,
    collection NVARCHAR(64) NULL,
    featured BIT NOT NULL CONSTRAINT DF_catalog_product_featured DEFAULT 0,
    new_arrival BIT NOT NULL CONSTRAINT DF_catalog_product_new_arrival DEFAULT 0,
    campaign_eligible BIT NOT NULL CONSTRAINT DF_catalog_product_campaign_eligible DEFAULT 1,
    merchandising_rank INT NOT NULL CONSTRAINT DF_catalog_product_merchandising_rank DEFAULT 100,
    hero_image VARCHAR(255) NULL,
    primary_image VARCHAR(255) NULL;

GO

ALTER TABLE catalog_product ADD
    CONSTRAINT CK_catalog_product_category CHECK (category IS NULL OR LEN(LTRIM(RTRIM(category))) > 0),
    CONSTRAINT CK_catalog_product_collection CHECK (collection IS NULL OR LEN(LTRIM(RTRIM(collection))) > 0),
    CONSTRAINT CK_catalog_product_merchandising_rank CHECK (merchandising_rank >= 0);

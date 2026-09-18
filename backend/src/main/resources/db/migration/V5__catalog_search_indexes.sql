-- Catalog search and browse: weighted search vector, the indexes every catalog query needs,
-- and the CHECK constraints that mirror the product write DTO.

-- A generated column's expression cannot be altered, so the unweighted V1 vector is dropped and
-- recreated. Weights let ts_rank tell a match in the name (A) from one in the description (B)
-- or in an attribute value such as the wood or style (C).
ALTER TABLE products DROP COLUMN search_vector;
ALTER TABLE products ADD COLUMN search_vector TSVECTOR GENERATED ALWAYS AS (
    setweight(to_tsvector('english', coalesce(name, '')), 'A') ||
    setweight(to_tsvector('english', coalesce(description, '')), 'B') ||
    setweight(jsonb_to_tsvector('english', coalesce(attributes, '{}'::jsonb), '["string"]'), 'C')
) STORED;

-- Full-text search: without this every search reads every product.
CREATE INDEX idx_products_search_vector ON products USING GIN (search_vector);

-- Suggestions as you type: full-text search only matches whole words, trigrams match fragments.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE INDEX idx_products_name_trgm ON products USING GIN (name gin_trgm_ops);

-- Postgres indexes primary keys and UNIQUE columns, never foreign keys. These serve the category
-- and brand filters, product detail lookups, and the ON DELETE CASCADE scans.
CREATE INDEX idx_products_category_id ON products (category_id);
CREATE INDEX idx_products_brand_id ON products (brand_id);
CREATE INDEX idx_product_images_product_id ON product_images (product_id);
CREATE INDEX idx_product_reviews_product_id ON product_reviews (product_id);
CREATE INDEX idx_categories_parent_id ON categories (parent_id);

-- Sorted browsing without a search term. Partial on active, because every listing filters on it.
CREATE INDEX idx_products_active_price ON products (price) WHERE active;
CREATE INDEX idx_products_active_rating ON products (average_rating DESC NULLS LAST) WHERE active;
CREATE INDEX idx_products_active_created ON products (created_at DESC) WHERE active;

-- Mirrors ProductRequest's @DecimalMin / @Min rules.
ALTER TABLE products
    ADD CONSTRAINT chk_products_price_non_negative CHECK (price >= 0),
    ADD CONSTRAINT chk_products_stock_non_negative CHECK (stock_quantity >= 0),
    ADD CONSTRAINT chk_products_weight_positive CHECK (weight_kg IS NULL OR weight_kg > 0),
    ADD CONSTRAINT chk_products_dimensions_positive CHECK (
        (width_cm IS NULL OR width_cm > 0) AND
        (height_cm IS NULL OR height_cm > 0) AND
        (depth_cm IS NULL OR depth_cm > 0));

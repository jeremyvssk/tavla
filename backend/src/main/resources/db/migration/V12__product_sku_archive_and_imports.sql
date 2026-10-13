-- Products get a SKU (what a bulk upload matches rows on) and can be archived; categories get an order
-- staff choose; and bulk uploads are kept as jobs between the check and the import.

-- The shop's own article code. Optional, unique ignoring case, so "ch166" can't sit beside "CH166".
-- The demo catalog fills it from the supplier's code (db/seed, tools/catalog-import/build_seed.py).
ALTER TABLE products ADD COLUMN sku VARCHAR(64);
CREATE UNIQUE INDEX uq_products_sku ON products (lower(sku)) WHERE sku IS NOT NULL;

-- Archived: taken out of the shop for good, but kept because past orders point at it. Hidden is just
-- active = false; archived is hidden and also out of the staff's everyday list.
ALTER TABLE products ADD COLUMN archived_at TIMESTAMP;
ALTER TABLE products ADD CONSTRAINT ck_products_archived_hidden CHECK (archived_at IS NULL OR NOT active);

-- The staff product list filters by state and sorts by price, stock, rating or age.
CREATE INDEX ix_products_staff_state ON products (active, archived_at, created_at DESC);

-- Siblings are listed by position, then name; 0 everywhere keeps today's alphabetical order.
ALTER TABLE categories ADD COLUMN position INTEGER NOT NULL DEFAULT 0;

-- A bulk upload between its check and its import. The file's rows wait in `planned`, as they were in the
-- file, until someone presses Import; they are checked again then, and the rows with errors can be
-- downloaded from there to fix. `header` is a CSV file's header row.
CREATE TABLE import_jobs (
    id           UUID PRIMARY KEY,
    file_name    VARCHAR(255) NOT NULL,
    format       VARCHAR(4) NOT NULL CHECK (format IN ('JSON', 'CSV')),
    size_bytes   INTEGER NOT NULL CHECK (size_bytes >= 0),
    uploaded_by  UUID REFERENCES users(id) ON DELETE SET NULL,
    uploaded_at  TIMESTAMP NOT NULL DEFAULT NOW(),
    status       VARCHAR(10) NOT NULL CHECK (status IN ('CHECKED', 'DONE', 'FAILED')),
    row_count    INTEGER NOT NULL CHECK (row_count >= 0),
    on_match     VARCHAR(6) NOT NULL CHECK (on_match IN ('UPDATE', 'SKIP')),
    counts       JSONB NOT NULL,
    errors       JSONB NOT NULL,
    changes      JSONB NOT NULL,
    planned      JSONB,
    header       JSONB,
    failure      VARCHAR(255),
    finished_at  TIMESTAMP
);

-- a job's rows are cleared once it is done or failed; the job itself stays as a record of what happened
CREATE INDEX ix_import_jobs_uploaded ON import_jobs (uploaded_at DESC);

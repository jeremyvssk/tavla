-- The shopper's own account: a phone number, saved addresses, a billing address on orders, and deleting the
-- account while the orders stay for the books. Every personal field here is encrypted (crypto/FieldCipher,
-- "v1:" + base64), so it is TEXT and never filtered, sorted or joined on.

ALTER TABLE users ADD COLUMN phone TEXT;

CREATE TABLE user_addresses (
    id               UUID PRIMARY KEY,
    user_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- what the shopper calls it: Home, Work
    label            VARCHAR(40) NOT NULL CHECK (length(btrim(label)) > 0),
    first_name       TEXT NOT NULL,
    last_name        TEXT NOT NULL,
    street           TEXT NOT NULL,
    flat             TEXT,
    postcode         TEXT NOT NULL,
    city             TEXT NOT NULL,
    country          VARCHAR(2) NOT NULL CHECK (country IN ('EE', 'LV', 'LT', 'FI')),
    phone            TEXT NOT NULL,
    default_delivery BOOLEAN NOT NULL DEFAULT FALSE,
    default_billing  BOOLEAN NOT NULL DEFAULT FALSE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX ix_user_addresses_user ON user_addresses (user_id, created_at);
-- at most one default of each kind per account; AddressService moves a default in one transaction
CREATE UNIQUE INDEX uq_user_addresses_default_delivery ON user_addresses (user_id) WHERE default_delivery;
CREATE UNIQUE INDEX uq_user_addresses_default_billing ON user_addresses (user_id) WHERE default_billing;

-- The invoice address, when it differs from the delivery address; null when it is the same.
ALTER TABLE orders ADD COLUMN billing_name TEXT;
ALTER TABLE orders ADD COLUMN billing_street TEXT;
ALTER TABLE orders ADD COLUMN billing_postcode TEXT;
ALTER TABLE orders ADD COLUMN billing_city TEXT;
ALTER TABLE orders ADD COLUMN billing_country VARCHAR(2) CHECK (billing_country IN ('EE', 'LV', 'LT', 'FI'));
ALTER TABLE orders ADD CONSTRAINT ck_orders_billing_whole CHECK (
    (billing_name IS NULL) = (billing_street IS NULL) AND (billing_name IS NULL) = (billing_postcode IS NULL)
    AND (billing_name IS NULL) = (billing_city IS NULL) AND (billing_name IS NULL) = (billing_country IS NULL));

-- Deleting an account keeps its orders: they are the shop's accounting records (the Estonian Accounting Act
-- keeps them seven years). The order loses its link to the account, so an order whose account is gone has
-- neither an owner nor a guest cookie, and nobody can open it from the shop any more.
ALTER TABLE orders DROP CONSTRAINT orders_user_id_fkey;
ALTER TABLE orders ADD CONSTRAINT orders_user_id_fkey FOREIGN KEY (user_id) REFERENCES users (id) ON DELETE SET NULL;
ALTER TABLE orders DROP CONSTRAINT ck_orders_one_owner;
ALTER TABLE orders ADD CONSTRAINT ck_orders_at_most_one_owner CHECK (user_id IS NULL OR guest_token_hash IS NULL);

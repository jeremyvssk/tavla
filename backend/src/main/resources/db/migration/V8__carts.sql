-- Carts live on the server, so a signed-in cart follows the account and the server can check stock and
-- prices. A cart belongs to exactly one owner: a user, or a guest known only by a random token kept in an
-- HttpOnly cookie. Only the token's SHA-256 is stored, like the refresh and reset tokens.
CREATE TABLE carts (
    id               UUID PRIMARY KEY,
    -- a user has one cart; deleting the user (GDPR hard delete) takes it along
    user_id          UUID UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    guest_token_hash VARCHAR(64) UNIQUE,
    created_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    -- bumped on every change; the expiry job deletes guest carts nobody touched for a while
    updated_at       TIMESTAMP NOT NULL DEFAULT NOW(),
    CONSTRAINT ck_carts_one_owner CHECK ((user_id IS NULL) <> (guest_token_hash IS NULL))
);

CREATE INDEX ix_carts_guest_updated_at ON carts (updated_at) WHERE user_id IS NULL;

-- One row per product per cart. No price column: the price is read from products on every read, so a
-- cart never shows a stale price. The order copies the price when it is placed.
CREATE TABLE cart_items (
    cart_id    UUID NOT NULL REFERENCES carts(id) ON DELETE CASCADE,
    -- a product deleted from the catalog leaves the carts too; a deactivated one stays and shows as unavailable
    product_id UUID NOT NULL REFERENCES products(id) ON DELETE CASCADE,
    -- 10 is the per-line limit the storefront has always had (CartService.LINE_LIMIT)
    quantity   INTEGER NOT NULL CHECK (quantity BETWEEN 1 AND 10),
    added_at   TIMESTAMP NOT NULL DEFAULT NOW(),
    PRIMARY KEY (cart_id, product_id)
);

-- the cascade from products, and the recommendations, look lines up by product
CREATE INDEX ix_cart_items_product ON cart_items (product_id);

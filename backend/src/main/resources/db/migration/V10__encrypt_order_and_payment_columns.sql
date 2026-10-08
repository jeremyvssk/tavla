-- Encryption at rest for orders, their lines and payments, plus the refund statuses.
--
-- The columns below now hold AES-256-GCM ciphertext (crypto/FieldCipher, key in APP_ENCRYPTION_KEY),
-- written and read through the Encrypted JPA converter: v1:base64(iv || ciphertext || tag). They become
-- TEXT because the ciphertext is longer than the value and a fresh IV makes every one different. The
-- length limits stay on the plaintext, in the request DTOs. What stays plain is what queries filter,
-- sort or join on: ids, status, dates, money, the country.
--
-- Ships together with V9, so no order or payment was ever stored in plain text. The converter refuses
-- a plain value rather than guess, so a row written without it fails loudly instead of leaking.

-- Contact and delivery details. The country stays plain: shipping rules depend on it, and four values
-- identify nobody.
ALTER TABLE orders
    ALTER COLUMN email      TYPE TEXT,
    ALTER COLUMN phone      TYPE TEXT,
    ALTER COLUMN first_name TYPE TEXT,
    ALTER COLUMN last_name  TYPE TEXT,
    ALTER COLUMN street     TYPE TEXT,
    ALTER COLUMN flat       TYPE TEXT,
    ALTER COLUMN postcode   TYPE TEXT,
    ALTER COLUMN city       TYPE TEXT;

-- What was bought. The money stays plain so its CHECKs still hold.
ALTER TABLE order_items
    ALTER COLUMN product_name TYPE TEXT,
    ALTER COLUMN options      TYPE TEXT,
    ALTER COLUMN image_url    TYPE TEXT;

-- The payment record: Stripe's PaymentIntent id, the card's brand and last 4, and the decline code.
-- Nothing looks a payment up by these (order_id is the key), so their UNIQUE and format CHECK go:
-- both would only ever see ciphertext.
ALTER TABLE payments DROP CONSTRAINT payments_provider_payment_id_key;
ALTER TABLE payments DROP CONSTRAINT payments_card_last4_check;
ALTER TABLE payments
    ALTER COLUMN provider_payment_id TYPE TEXT,
    ALTER COLUMN card_brand          TYPE TEXT,
    ALTER COLUMN card_last4          TYPE TEXT,
    ALTER COLUMN failure_code        TYPE TEXT;

-- Refunds. A paid order the shopper cancels goes SUCCEEDED -> REFUND_DUE (cancelled here, Stripe hasn't
-- accepted the refund yet; RefundRetry keeps asking) -> REFUNDING (accepted) -> REFUNDED (confirmed).
ALTER TABLE payments DROP CONSTRAINT payments_status_check;
ALTER TABLE payments ALTER COLUMN status TYPE VARCHAR(12);
ALTER TABLE payments ADD CONSTRAINT payments_status_check CHECK (status IN (
    'STARTED', 'SUCCEEDED', 'FAILED', 'CANCELED', 'REFUND_DUE', 'REFUNDING', 'REFUNDED'));

-- RefundRetry looks every minute for refunds Stripe hasn't accepted yet.
CREATE INDEX ix_payments_refund_due ON payments (updated_at) WHERE status = 'REFUND_DUE';

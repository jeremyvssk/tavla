-- The users table's personal data encrypted at rest, like orders (V10) and addresses (V15): the email, the
-- name, the Google photo link and the 2FA secret now hold FieldCipher ciphertext ("v1:" + base64), so
-- they are TEXT. The 2FA secret is encrypted, not hashed: the server needs it back to compute the codes.
-- Passwords and backup codes stay BCrypt and SHA-256 hashes, which are only ever compared.
--
-- Sign-in looks the email up, and a fresh IV makes every ciphertext different, so lookups and uniqueness
-- move to email_hash, an HMAC of the lower-cased email (crypto/BlindIndex). The old unique constraint
-- and V4's lower(email) index can't work on ciphertext and go.
--
-- SQL can't run the Java cipher, so rows that exist now are encrypted right after this runs, in Java
-- (user/UserEncryptionBackfill, a Flyway afterMigrate callback). The same step covers the demo seed's
-- reviewers, which R__seed_catalog inserts in plain SQL. Until it runs, those rows have no email_hash,
-- which is why the column allows NULL; the CHECK makes sure no encrypted email ever lacks one.

ALTER TABLE users DROP CONSTRAINT users_email_key;
DROP INDEX uq_users_email_lower;

ALTER TABLE users
    ALTER COLUMN email             TYPE TEXT,
    ALTER COLUMN full_name         TYPE TEXT,
    ALTER COLUMN avatar_url        TYPE TEXT,
    ALTER COLUMN two_factor_secret TYPE TEXT,
    ADD COLUMN email_hash VARCHAR(64),
    ADD CONSTRAINT ck_users_encrypted_email_has_hash CHECK (email NOT LIKE 'v1:%' OR email_hash IS NOT NULL);

CREATE UNIQUE INDEX uq_users_email_hash ON users (email_hash);

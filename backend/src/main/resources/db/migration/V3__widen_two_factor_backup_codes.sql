-- 2FA enrollment stores 8 hashed backup codes; they don't fit in VARCHAR(255). Widen to TEXT.
-- TEXT and VARCHAR share the same JDBC type, so the String entity mapping still validates.
ALTER TABLE "users" ALTER COLUMN "two_factor_backup_codes" TYPE TEXT;

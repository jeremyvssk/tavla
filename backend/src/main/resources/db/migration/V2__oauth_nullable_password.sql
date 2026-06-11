-- OAuth users have no local password. Allow NULL password_hash, but keep the
-- guarantee that LOCAL (email/password) accounts always have one.
ALTER TABLE "users" ALTER COLUMN "password_hash" DROP NOT NULL;

ALTER TABLE "users"
ADD CONSTRAINT "password_hash_required_for_local"
CHECK ("auth_provider" <> 'LOCAL' OR "password_hash" IS NOT NULL);

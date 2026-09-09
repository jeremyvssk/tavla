-- Email uniqueness must ignore case: UNIQUE on VARCHAR is case-sensitive, so "Ada@shop.com"
-- and "ada@shop.com" were two accounts and the second one could never log in.
-- UserService folds addresses on write and on lookup; this index is what makes it true
-- regardless of the code path. Fails loudly if existing rows already collide.

UPDATE "users" SET "email" = lower(trim("email")) WHERE "email" <> lower(trim("email"));

CREATE UNIQUE INDEX "uq_users_email_lower" ON "users" (lower("email"));

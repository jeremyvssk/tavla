-- Keeps products.average_rating and products.review_count true for every writer: the API, the seed
-- migration, psql, a future admin tool. Recomputed from product_reviews rather than incremented,
-- so a missed or duplicated event can never leave the numbers drifting.

CREATE FUNCTION refresh_product_rating(target UUID) RETURNS void
LANGUAGE plpgsql AS $$
BEGIN
    -- Lock the product row first, in a statement of its own. Two reviews for one product arriving
    -- together then queue here. Under READ COMMITTED every statement in this function takes a
    -- fresh snapshot, so the UPDATE below sees a review the other transaction committed while we
    -- waited. A bare UPDATE would have taken its snapshot *before* the wait and silently dropped
    -- that review from the average.
    -- NO KEY UPDATE, not UPDATE: inserting a review takes KEY SHARE on this row for the foreign key
    -- check. FOR UPDATE conflicts with KEY SHARE, so two inserts that both pass their FK check before
    -- either reaches this line would each wait for the other: a deadlock. NO KEY UPDATE doesn't
    -- conflict with KEY SHARE, and it is the same lock a plain UPDATE takes anyway.
    PERFORM 1 FROM products WHERE id = target FOR NO KEY UPDATE;

    UPDATE products p
    SET average_rating = r.avg_rating,
        review_count   = r.cnt
    FROM (SELECT round(avg(rating)::numeric, 2) AS avg_rating, count(*)::int AS cnt
          FROM product_reviews
          WHERE product_id = target) r
    WHERE p.id = target;
END;
$$;

CREATE FUNCTION product_reviews_refresh_rating() RETURNS trigger
LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP = 'DELETE' THEN
        PERFORM refresh_product_rating(OLD.product_id);
    ELSE
        PERFORM refresh_product_rating(NEW.product_id);
        IF TG_OP = 'UPDATE' AND OLD.product_id <> NEW.product_id THEN
            PERFORM refresh_product_rating(OLD.product_id);
        END IF;
    END IF;
    RETURN NULL;
END;
$$;

CREATE TRIGGER trg_product_reviews_refresh_rating
AFTER INSERT OR UPDATE OR DELETE ON product_reviews
FOR EACH ROW EXECUTE FUNCTION product_reviews_refresh_rating();

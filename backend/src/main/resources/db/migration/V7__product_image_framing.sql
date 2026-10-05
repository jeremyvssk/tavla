-- How the storefront frames each photo on its square tile: where the photo is cut, what to trim,
-- how much to brighten an off-white ground. Measured from the image by
-- tools/catalog-import/frame_photos.py; NULL (an uploaded image, say) means "show it whole".
-- JSONB rather than seven columns: it is read and written only as a whole, never queried by field.
ALTER TABLE product_images ADD COLUMN framing JSONB;

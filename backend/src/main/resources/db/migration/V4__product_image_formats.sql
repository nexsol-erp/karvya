-- ---------------------------------------------------------------------------
-- Which renditions exist for a photograph
-- ---------------------------------------------------------------------------
--
-- storage_key names a base, not a file: the storefront asks for
-- /media/{key}-{width}.{format}. Until now every row was produced offline by
-- scripts/optimise-images.mjs, which always emits AVIF, WebP and JPEG, so the
-- markup could assume all three existed.
--
-- Photographs uploaded through the admin are resized in the application, which
-- has no AVIF or WebP encoder available to it, so those rows carry JPEG only.
-- A <source type="image/avif"> is chosen by the browser on the strength of the
-- type alone - it does not check that the file is there - so guessing wrong
-- shows a broken image rather than falling back. The renditions therefore have
-- to be recorded rather than assumed.

ALTER TABLE product_image
    ADD COLUMN formats VARCHAR(64) NOT NULL DEFAULT 'jpg';

-- everything already stored came from the offline pipeline
UPDATE product_image SET formats = 'avif,webp,jpg';

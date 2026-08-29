-- Karvya store :: initial catalogue
--
-- Five products, each photographed twice, from the supplied originals.
-- Storage keys match the output of scripts/optimise-images.mjs.
--
-- IMPORTANT: every product below has placeholder_content = TRUE. Names,
-- prices, dimensions and care instructions are placeholders. Descriptions
-- state only what is visible in the photograph - no material claim, origin,
-- use or benefit is asserted that was not supplied. Review and clear the
-- placeholder flag in the admin product screen before launch.

INSERT INTO product (
    sku, slug, name, category_id, short_description, description,
    price, material, colour, dimensions, care_instructions,
    stock_quantity, low_stock_threshold, featured, status, placeholder_content
)
SELECT
    v.sku, v.slug, v.name, c.id, v.short_description, v.description,
    v.price, v.material, v.colour, v.dimensions, v.care_instructions,
    v.stock, 3, v.featured, 'ACTIVE', TRUE
FROM (VALUES
    (
        'KV-BH-01',
        'terracotta-roof-coir-nest-house',
        'Terracotta Roof Coir Nest House',
        'Cylindrical coir nest house with a deep red conical roof and a jute hanging cord.',
        'A hanging nest house with a cylindrical body wound in natural coir fibre and a conical roof dyed deep terracotta red. A single round entrance opens at the front. It hangs from a braided jute cord. [PLACEHOLDER DESCRIPTION - expand with your own wording before launch.]',
        1250.00,
        'Coir fibre',
        'Natural coir with terracotta red roof',
        '[PLACEHOLDER] Measure and enter height x diameter',
        '[PLACEHOLDER] Enter your care guidance',
        8, TRUE
    ),
    (
        'KV-BH-02',
        'natural-coir-dome-nest-house',
        'Natural Coir Dome Nest House',
        'Undyed coir nest house with a domed roof and a twisted rope hanger.',
        'A hanging nest house wound entirely in undyed coir fibre, with a softly domed roof over a tapered body and a single round entrance. It hangs from a twisted rope loop. [PLACEHOLDER DESCRIPTION - expand with your own wording before launch.]',
        950.00,
        'Coir fibre',
        'Natural undyed coir',
        '[PLACEHOLDER] Measure and enter height x diameter',
        '[PLACEHOLDER] Enter your care guidance',
        6, TRUE
    ),
    (
        'KV-BH-03',
        'teardrop-coir-nest-house',
        'Teardrop Coir Nest House',
        'Teardrop-shaped coir nest house with a deep red roof and a wide entrance.',
        'A hanging nest house with a rounded teardrop body in natural coir fibre, topped with a conical roof dyed deep red. The entrance is set wide at the front. It hangs from a braided jute cord. [PLACEHOLDER DESCRIPTION - expand with your own wording before launch.]',
        1450.00,
        'Coir fibre',
        'Natural coir with deep red roof',
        '[PLACEHOLDER] Measure and enter height x width',
        '[PLACEHOLDER] Enter your care guidance',
        4, FALSE
    ),
    (
        'KV-BH-04',
        'pitched-roof-coir-bird-house',
        'Pitched Roof Coir Bird House',
        'Square coir bird house with a dark felted pitched roof.',
        'A hanging bird house with a squared body wound in natural coir fibre, sheltered by a pitched roof in dark felted material. A single round entrance opens at the front. It hangs from a braided jute cord. [PLACEHOLDER DESCRIPTION - expand with your own wording before launch.]',
        1150.00,
        'Coir fibre with felted roof',
        'Natural coir with dark brown roof',
        '[PLACEHOLDER] Measure and enter height x width x depth',
        '[PLACEHOLDER] Enter your care guidance',
        2, FALSE
    ),
    (
        'KV-BH-05',
        'twin-entrance-coir-bird-house',
        'Twin Entrance Coir Bird House',
        'Wide coir bird house with two copper-rimmed entrances.',
        'A broad hanging bird house wound in natural coir fibre, with two round entrances each finished with a copper rim. It hangs from a single rope cord. [PLACEHOLDER DESCRIPTION - expand with your own wording before launch.]',
        1650.00,
        'Coir fibre with copper rims',
        'Natural coir with copper detail',
        '[PLACEHOLDER] Measure and enter width x height x depth',
        '[PLACEHOLDER] Enter your care guidance',
        5, TRUE
    )
) AS v (sku, slug, name, short_description, description, price, material,
        colour, dimensions, care_instructions, stock, featured)
CROSS JOIN (SELECT id FROM category WHERE slug = 'bird-houses-and-nests') c;


-- Two views per product. View "a" is primary; both are 1856 x 1958 after the watermark crop.
INSERT INTO product_image (
    product_id, storage_key, alt_text, content_type, width, height, display_order, is_primary
)
SELECT p.id, v.storage_key, v.alt_text, 'image/webp', 1856, 1958, v.display_order, v.is_primary
FROM (VALUES
    ('KV-BH-01', 'products/kv-bh-01/a', 'Cylindrical coir nest house with a deep red conical roof, hanging from a jute cord', 0, TRUE),
    ('KV-BH-01', 'products/kv-bh-01/b', 'Second view of the terracotta roof coir nest house', 1, FALSE),
    ('KV-BH-02', 'products/kv-bh-02/a', 'Undyed coir nest house with a domed roof, hanging from a twisted rope', 0, TRUE),
    ('KV-BH-02', 'products/kv-bh-02/b', 'Second view of the natural coir dome nest house', 1, FALSE),
    ('KV-BH-03', 'products/kv-bh-03/a', 'Teardrop-shaped coir nest house with a deep red roof and wide entrance', 0, TRUE),
    ('KV-BH-03', 'products/kv-bh-03/b', 'Second view of the teardrop coir nest house', 1, FALSE),
    ('KV-BH-04', 'products/kv-bh-04/a', 'Square coir bird house with a dark felted pitched roof', 0, TRUE),
    ('KV-BH-04', 'products/kv-bh-04/b', 'Second view of the pitched roof coir bird house', 1, FALSE),
    ('KV-BH-05', 'products/kv-bh-05/a', 'Wide coir bird house with two copper-rimmed entrances', 0, TRUE),
    ('KV-BH-05', 'products/kv-bh-05/b', 'Second view of the twin entrance coir bird house', 1, FALSE)
) AS v (sku, storage_key, alt_text, display_order, is_primary)
JOIN product p ON p.sku = v.sku;

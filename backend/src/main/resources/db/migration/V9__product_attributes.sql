-- ---------------------------------------------------------------------------
-- Attributes an administrator defines, instead of columns a developer does
-- ---------------------------------------------------------------------------
--
-- The catalogue had four fixed columns - material, colour, dimensions, care
-- instructions - with their labels written into the product page. That works
-- for one kind of thing and nothing else: a book has an author, a publisher
-- and an ISBN, and none of those is a material.
--
-- Definitions are scoped to a category, because this shop sells more than one
-- kind of thing. A book shows an author; a bird house shows a material; and
-- neither should be asked for the other's fields. A definition with no category
-- applies to everything, which is what a weight or a country of origin wants.
--
-- Author is deliberately NOT one of these. It is the field customers search and
-- browse by, so it needs an index and a place on the product card - and neither
-- is reasonable against a generic key-value table.

ALTER TABLE product ADD COLUMN author VARCHAR(200);

-- What that field is called here, and whether it exists at all.
--
-- "Author" is right for a book, "Artist" for a record, and nothing at all for a
-- bird house. Null means this category has no such field, so the product form
-- does not ask and the product page does not show it - which is what keeps one
-- indexed, searchable column usable across kinds of product that have no word
-- in common.
ALTER TABLE category ADD COLUMN author_label VARCHAR(40);

-- Searching "rushdie" has to find the books, so this is the same shape as the
-- name search rather than a plain btree on the raw value.
CREATE INDEX ix_product_author ON product (lower(author));

CREATE TABLE product_attribute (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    label         VARCHAR(80)  NOT NULL,
    -- stable across a rename, so relabelling "Care" to "Care instructions"
    -- does not orphan every value recorded against it
    slug          VARCHAR(80)  NOT NULL UNIQUE,
    -- null means it applies to every product, whatever the category
    category_id   BIGINT       REFERENCES category (id) ON DELETE CASCADE,
    help_text     VARCHAR(255),
    display_order INTEGER      NOT NULL DEFAULT 0,
    active        BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by    VARCHAR(160)
);

CREATE INDEX ix_product_attribute_category ON product_attribute (category_id, display_order);

CREATE TABLE product_attribute_value (
    product_id   BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    attribute_id BIGINT       NOT NULL REFERENCES product_attribute (id) ON DELETE CASCADE,
    value        TEXT         NOT NULL,
    PRIMARY KEY (product_id, attribute_id)
);

-- ---------------------------------------------------------------------------
-- Carry the existing four across, so nothing already entered is lost
-- ---------------------------------------------------------------------------
--
-- Scoped to the category the seeded products are in rather than left global:
-- these are craft attributes, and a book should not be asked for care
-- instructions. If the shop only ever sells one kind of thing, clearing the
-- category on each of them makes them global again.

INSERT INTO product_attribute (label, slug, category_id, display_order)
SELECT v.label, v.slug, c.id, v.ord
  FROM (VALUES
        ('Material',   'material',          1),
        ('Colour',     'colour',            2),
        ('Dimensions', 'dimensions',        3),
        ('Care',       'care-instructions', 4)
       ) AS v(label, slug, ord)
  CROSS JOIN (SELECT id FROM category WHERE slug = 'bird-houses-and-nests') c
 WHERE EXISTS (SELECT 1 FROM category WHERE slug = 'bird-houses-and-nests');

INSERT INTO product_attribute_value (product_id, attribute_id, value)
SELECT p.id, a.id, v.value
  FROM product p
  CROSS JOIN LATERAL (VALUES
        ('material',          p.material),
        ('colour',            p.colour),
        ('dimensions',        p.dimensions),
        ('care-instructions', p.care_instructions)
       ) AS v(slug, value)
  JOIN product_attribute a ON a.slug = v.slug
 WHERE v.value IS NOT NULL AND btrim(v.value) <> '';

ALTER TABLE product
    DROP COLUMN material,
    DROP COLUMN colour,
    DROP COLUMN dimensions,
    DROP COLUMN care_instructions;

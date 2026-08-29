-- ---------------------------------------------------------------------------
-- Who supplies each product
-- ---------------------------------------------------------------------------
--
-- A vendor is a table of its own rather than columns on the product, because
-- one supplier makes several pieces: the address and phone number would
-- otherwise be copied per product, and correcting a changed number would mean
-- editing every one of them.
--
-- What varies per product is the price paid for it and, sometimes, the lead
-- time. Those live on the product; everything about the supplier lives here.
--
-- None of this is ever shown to a customer. The price paid is the shop's
-- margin, and a supplier's contact details are not the shopper's business, so
-- no public DTO carries any of it.

CREATE TABLE vendor (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name            VARCHAR(200) NOT NULL,
    contact_name    VARCHAR(160),
    email           VARCHAR(255),
    phone           VARCHAR(32),
    address         TEXT,
    -- free text, not a number of days: suppliers quote "2 to 3 weeks" and
    -- "after the monsoon", and rounding that into an integer loses the meaning
    delivery_time   VARCHAR(160),
    conditions      TEXT,
    active          BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by      VARCHAR(160)
);

-- Two suppliers can share a trading name, so this is not unique; it exists to
-- make the admin list sort and search without a scan.
CREATE INDEX ix_vendor_name ON vendor (lower(name));

ALTER TABLE product
    -- SET NULL rather than CASCADE: removing a supplier must never delete the
    -- products they made, only the record of who supplied them
    ADD COLUMN vendor_id BIGINT REFERENCES vendor (id) ON DELETE SET NULL,
    ADD COLUMN vendor_price NUMERIC(10, 2),
    ADD COLUMN vendor_delivery_time VARCHAR(160),
    ADD CONSTRAINT ck_product_vendor_price CHECK (vendor_price IS NULL OR vendor_price >= 0);

CREATE INDEX ix_product_vendor ON product (vendor_id);

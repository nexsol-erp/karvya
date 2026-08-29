-- Karvya store :: initial schema
-- PostgreSQL 17. Money is NUMERIC(10,2), never floating point.
-- Timestamps are TIMESTAMPTZ. updated_at / updated_by are maintained by the
-- application layer so the audit trail records the acting user, not the session role.

-- ---------------------------------------------------------------------------
-- identity
-- ---------------------------------------------------------------------------

CREATE TABLE role (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code        VARCHAR(32)  NOT NULL,
    label       VARCHAR(64)  NOT NULL,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_role_code UNIQUE (code)
);

CREATE TABLE app_user (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email                 VARCHAR(255) NOT NULL,
    email_normalized      VARCHAR(255) NOT NULL,
    phone                 VARCHAR(32),
    password_hash         VARCHAR(255) NOT NULL,
    full_name             VARCHAR(160) NOT NULL,
    enabled               BOOLEAN      NOT NULL DEFAULT TRUE,
    must_change_password  BOOLEAN      NOT NULL DEFAULT FALSE,
    failed_attempts       INTEGER      NOT NULL DEFAULT 0,
    locked_until          TIMESTAMPTZ,
    email_verified_at     TIMESTAMPTZ,
    last_login_at         TIMESTAMPTZ,
    version               BIGINT       NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by            VARCHAR(160),
    CONSTRAINT uq_app_user_email_normalized UNIQUE (email_normalized),
    CONSTRAINT ck_app_user_failed_attempts CHECK (failed_attempts >= 0)
);

-- phone is optional, but unique when supplied
CREATE UNIQUE INDEX uq_app_user_phone ON app_user (phone) WHERE phone IS NOT NULL;

CREATE TABLE user_role (
    user_id  BIGINT NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    role_id  BIGINT NOT NULL REFERENCES role (id)     ON DELETE CASCADE,
    PRIMARY KEY (user_id, role_id)
);

CREATE INDEX ix_user_role_role ON user_role (role_id);

CREATE TABLE customer_address (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id         BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    label           VARCHAR(64),
    recipient_name  VARCHAR(160) NOT NULL,
    phone           VARCHAR(32)  NOT NULL,
    line1           VARCHAR(255) NOT NULL,
    line2           VARCHAR(255),
    city            VARCHAR(120) NOT NULL,
    state           VARCHAR(120) NOT NULL,
    postal_code     VARCHAR(24)  NOT NULL,
    is_default      BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_customer_address_user ON customer_address (user_id);
-- at most one default address per customer
CREATE UNIQUE INDEX uq_customer_address_default ON customer_address (user_id) WHERE is_default;

CREATE TABLE password_reset_token (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id       BIGINT       NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    token_hash    VARCHAR(128) NOT NULL,
    expires_at    TIMESTAMPTZ  NOT NULL,
    used_at       TIMESTAMPTZ,
    requested_ip  VARCHAR(64),
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT uq_password_reset_token_hash UNIQUE (token_hash)
);

CREATE INDEX ix_password_reset_user ON password_reset_token (user_id);
CREATE INDEX ix_password_reset_expiry ON password_reset_token (expires_at) WHERE used_at IS NULL;

-- ---------------------------------------------------------------------------
-- catalogue
-- ---------------------------------------------------------------------------

CREATE TABLE category (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(160) NOT NULL,
    slug           VARCHAR(160) NOT NULL,
    description    TEXT,
    image_key      VARCHAR(255),
    display_order  INTEGER      NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(160),
    CONSTRAINT uq_category_slug UNIQUE (slug)
);

CREATE TABLE product (
    id                    BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    sku                   VARCHAR(64)   NOT NULL,
    slug                  VARCHAR(200)  NOT NULL,
    name                  VARCHAR(200)  NOT NULL,
    category_id           BIGINT        NOT NULL REFERENCES category (id),
    short_description     VARCHAR(400),
    description           TEXT,
    price                 NUMERIC(10,2) NOT NULL,
    material              VARCHAR(160),
    colour                VARCHAR(120),
    dimensions            VARCHAR(160),
    care_instructions     TEXT,
    stock_quantity        INTEGER       NOT NULL DEFAULT 0,
    low_stock_threshold   INTEGER       NOT NULL DEFAULT 3,
    featured              BOOLEAN       NOT NULL DEFAULT FALSE,
    status                VARCHAR(16)   NOT NULL DEFAULT 'DRAFT',
    placeholder_content   BOOLEAN       NOT NULL DEFAULT FALSE,
    version               BIGINT        NOT NULL DEFAULT 0,
    created_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by            VARCHAR(160),
    CONSTRAINT uq_product_sku  UNIQUE (sku),
    CONSTRAINT uq_product_slug UNIQUE (slug),
    CONSTRAINT ck_product_price CHECK (price >= 0),
    CONSTRAINT ck_product_stock CHECK (stock_quantity >= 0),
    CONSTRAINT ck_product_status CHECK (status IN ('DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'))
);

CREATE INDEX ix_product_status_category ON product (status, category_id);
CREATE INDEX ix_product_status_featured ON product (status, featured);
CREATE INDEX ix_product_created ON product (created_at DESC);

CREATE TABLE product_image (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    product_id     BIGINT       NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    storage_key    VARCHAR(255) NOT NULL,
    alt_text       VARCHAR(255) NOT NULL,
    content_type   VARCHAR(64)  NOT NULL,
    width          INTEGER,
    height         INTEGER,
    bytes          BIGINT,
    display_order  INTEGER      NOT NULL DEFAULT 0,
    is_primary     BOOLEAN      NOT NULL DEFAULT FALSE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX ix_product_image_product ON product_image (product_id, display_order);
-- exactly one primary image per product
CREATE UNIQUE INDEX uq_product_image_primary ON product_image (product_id) WHERE is_primary;

-- ---------------------------------------------------------------------------
-- carts
-- ---------------------------------------------------------------------------

CREATE TABLE cart (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id     BIGINT      NOT NULL REFERENCES app_user (id) ON DELETE CASCADE,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_user UNIQUE (user_id)
);

CREATE TABLE cart_item (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    cart_id     BIGINT      NOT NULL REFERENCES cart (id)    ON DELETE CASCADE,
    product_id  BIGINT      NOT NULL REFERENCES product (id) ON DELETE CASCADE,
    quantity    INTEGER     NOT NULL,
    added_at    TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_cart_item UNIQUE (cart_id, product_id),
    CONSTRAINT ck_cart_item_quantity CHECK (quantity > 0)
);

-- ---------------------------------------------------------------------------
-- orders
-- ---------------------------------------------------------------------------

CREATE TABLE payment_method (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    code           VARCHAR(48)  NOT NULL,
    label          VARCHAR(120) NOT NULL,
    instructions   TEXT,
    display_order  INTEGER      NOT NULL DEFAULT 0,
    active         BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(160),
    CONSTRAINT uq_payment_method_code UNIQUE (code)
);

CREATE TABLE customer_order (
    id                   BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_number         VARCHAR(32)   NOT NULL,
    user_id              BIGINT        REFERENCES app_user (id) ON DELETE SET NULL,
    status               VARCHAR(24)   NOT NULL DEFAULT 'NEW',
    payment_status       VARCHAR(24)   NOT NULL DEFAULT 'PENDING',
    payment_method_code  VARCHAR(48)   NOT NULL,
    currency             VARCHAR(3)    NOT NULL DEFAULT 'INR',
    subtotal             NUMERIC(10,2) NOT NULL,
    delivery_charge      NUMERIC(10,2) NOT NULL DEFAULT 0,
    total                NUMERIC(10,2) NOT NULL,

    -- immutable delivery snapshot, independent of any saved address
    delivery_name        VARCHAR(160)  NOT NULL,
    delivery_phone       VARCHAR(32)   NOT NULL,
    delivery_email       VARCHAR(255),
    address_line1        VARCHAR(255)  NOT NULL,
    address_line2        VARCHAR(255),
    city                 VARCHAR(120)  NOT NULL,
    state                VARCHAR(120)  NOT NULL,
    postal_code          VARCHAR(24)   NOT NULL,
    delivery_notes       TEXT,
    customer_comments    TEXT,

    internal_notes       TEXT,
    access_token_hash    VARCHAR(128)  NOT NULL,
    stock_restored_at    TIMESTAMPTZ,
    placed_at            TIMESTAMPTZ   NOT NULL DEFAULT now(),
    version              BIGINT        NOT NULL DEFAULT 0,
    created_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_at           TIMESTAMPTZ   NOT NULL DEFAULT now(),
    updated_by           VARCHAR(160),

    CONSTRAINT uq_customer_order_number UNIQUE (order_number),
    CONSTRAINT ck_order_totals CHECK (subtotal >= 0 AND delivery_charge >= 0 AND total >= 0),
    CONSTRAINT ck_order_status CHECK (status IN
        ('NEW', 'CONFIRMED', 'PROCESSING', 'SHIPPED', 'DELIVERED', 'CANCELLED')),
    CONSTRAINT ck_order_payment_status CHECK (payment_status IN
        ('PENDING', 'AWAITING_PAYMENT', 'PAID_OFFLINE', 'REFUNDED'))
);

CREATE INDEX ix_order_status_placed ON customer_order (status, placed_at DESC);
CREATE INDEX ix_order_user_placed   ON customer_order (user_id, placed_at DESC);
CREATE INDEX ix_order_payment_status ON customer_order (payment_status, placed_at DESC);
CREATE INDEX ix_order_delivery_phone ON customer_order (delivery_phone);

CREATE TABLE order_item (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    product_id    BIGINT        REFERENCES product (id) ON DELETE SET NULL,
    product_name  VARCHAR(200)  NOT NULL,
    product_sku   VARCHAR(64)   NOT NULL,
    unit_price    NUMERIC(10,2) NOT NULL,
    quantity      INTEGER       NOT NULL,
    line_total    NUMERIC(10,2) NOT NULL,
    image_key     VARCHAR(255),
    CONSTRAINT ck_order_item_quantity CHECK (quantity > 0),
    CONSTRAINT ck_order_item_amounts CHECK (unit_price >= 0 AND line_total >= 0)
);

CREATE INDEX ix_order_item_order ON order_item (order_id);
CREATE INDEX ix_order_item_product ON order_item (product_id);

CREATE TABLE order_status_history (
    id          BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id    BIGINT       NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    field       VARCHAR(24)  NOT NULL,
    from_value  VARCHAR(24),
    to_value    VARCHAR(24)  NOT NULL,
    note        TEXT,
    changed_by  VARCHAR(160) NOT NULL,
    changed_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_history_field CHECK (field IN ('STATUS', 'PAYMENT_STATUS'))
);

CREATE INDEX ix_order_history_order ON order_status_history (order_id, changed_at);

CREATE TABLE offline_payment (
    id            BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    order_id      BIGINT        NOT NULL REFERENCES customer_order (id) ON DELETE CASCADE,
    method_code   VARCHAR(48)   NOT NULL,
    reference     VARCHAR(160),
    amount        NUMERIC(10,2) NOT NULL,
    received_on   DATE          NOT NULL,
    note          TEXT,
    recorded_by   VARCHAR(160)  NOT NULL,
    created_at    TIMESTAMPTZ   NOT NULL DEFAULT now(),
    CONSTRAINT ck_offline_payment_amount CHECK (amount > 0)
);

CREATE INDEX ix_offline_payment_order ON offline_payment (order_id);

-- ---------------------------------------------------------------------------
-- enquiries, settings, notifications
-- ---------------------------------------------------------------------------

CREATE TABLE contact_enquiry (
    id             BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name           VARCHAR(160) NOT NULL,
    email          VARCHAR(255) NOT NULL,
    phone          VARCHAR(32),
    subject        VARCHAR(200) NOT NULL,
    message        TEXT         NOT NULL,
    status         VARCHAR(16)  NOT NULL DEFAULT 'NEW',
    internal_note  TEXT,
    handled_by     VARCHAR(160),
    source_ip      VARCHAR(64),
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_enquiry_status CHECK (status IN ('NEW', 'IN_PROGRESS', 'RESOLVED'))
);

CREATE INDEX ix_enquiry_status_created ON contact_enquiry (status, created_at DESC);

CREATE TABLE site_setting (
    setting_key    VARCHAR(96)  PRIMARY KEY,
    setting_value  TEXT,
    value_type     VARCHAR(16)  NOT NULL DEFAULT 'STRING',
    description    VARCHAR(400),
    updated_at     TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_by     VARCHAR(160),
    CONSTRAINT ck_setting_type CHECK (value_type IN
        ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'URL', 'HTML', 'JSON'))
);

CREATE TABLE email_notification (
    id               BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    type             VARCHAR(48)  NOT NULL,
    recipient        VARCHAR(255) NOT NULL,
    subject          VARCHAR(255) NOT NULL,
    payload          JSONB        NOT NULL,
    related_order_id BIGINT       REFERENCES customer_order (id) ON DELETE SET NULL,
    status           VARCHAR(16)  NOT NULL DEFAULT 'PENDING',
    attempts         INTEGER      NOT NULL DEFAULT 0,
    last_error       TEXT,
    last_attempt_at  TIMESTAMPTZ,
    next_attempt_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    sent_at          TIMESTAMPTZ,
    created_at       TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT ck_notification_status CHECK (status IN ('PENDING', 'SENT', 'FAILED')),
    CONSTRAINT ck_notification_attempts CHECK (attempts >= 0)
);

-- the outbox worker only ever scans pending rows that are due
CREATE INDEX ix_notification_due ON email_notification (next_attempt_at)
    WHERE status = 'PENDING';
CREATE INDEX ix_notification_order ON email_notification (related_order_id);

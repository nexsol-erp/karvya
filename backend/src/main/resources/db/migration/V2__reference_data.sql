-- Karvya store :: reference data
-- Roles, offline payment methods, the launch category, and every configurable
-- site setting. Copy marked [PLACEHOLDER] must be replaced by the business
-- before launch; nothing here asserts a fact about the business that has not
-- been supplied.

INSERT INTO role (code, label) VALUES
    ('ADMIN',    'Administrator'),
    ('STAFF',    'Staff'),
    ('CUSTOMER', 'Customer');

INSERT INTO payment_method (code, label, instructions, display_order, active) VALUES
    ('CASH_ON_DELIVERY', 'Cash on delivery',
     'Pay the delivery agent in cash when your order arrives.', 1, TRUE),
    ('BANK_TRANSFER', 'Bank transfer',
     '[PLACEHOLDER] Bank account details will be shared by our team once your order is confirmed.', 2, TRUE),
    ('UPI_ON_CONFIRMATION', 'UPI after confirmation',
     '[PLACEHOLDER] A UPI ID will be shared by our team once your order is confirmed.', 3, TRUE),
    ('PAY_ON_COLLECTION', 'Pay when collecting',
     'Pay when you collect the piece in person. Our team will arrange a time with you.', 4, TRUE);

INSERT INTO category (name, slug, description, display_order, active) VALUES
    ('Bird Houses & Nests', 'bird-houses-and-nests',
     'Hanging bird houses and nesting shelters, each shaped by hand from coir fibre.',
     1, TRUE);

INSERT INTO site_setting (setting_key, setting_value, value_type, description) VALUES
    -- store identity
    ('store.name',              'Karvya',        'STRING',  'Store name shown in the header, emails and page titles'),
    ('store.tagline',           'Handwoven coir craft', 'STRING', 'Short line beside the store name'),
    ('store.logo_key',          NULL,            'STRING',  'Storage key of the uploaded logo; the wordmark is used when empty'),

    -- contact
    ('contact.whatsapp_number', NULL,            'STRING',  'Digits only, country code first. Falls back to APP_WHATSAPP_NUMBER. Drives every wa.me link'),
    ('contact.admin_email',     NULL,            'STRING',  'Where order and enquiry notifications are sent. Falls back to APP_ADMIN_NOTIFICATION_EMAIL'),
    ('contact.public_email',    NULL,            'STRING',  '[PLACEHOLDER] Address shown to customers on the contact page'),
    ('contact.address',         NULL,            'TEXT',    '[PLACEHOLDER] Business address shown in the footer'),

    -- money
    ('locale.currency',         'INR',           'STRING',  'ISO currency code'),
    ('locale.tag',              'en-IN',         'STRING',  'BCP 47 locale for number and date formatting'),
    ('delivery.charge',         '0.00',          'DECIMAL', 'Flat delivery charge applied to every order'),
    ('delivery.free_threshold', NULL,            'DECIMAL', 'Order subtotal at or above which delivery is free. Empty disables the rule'),

    -- catalogue
    ('catalogue.low_stock_threshold', '3',       'INTEGER', 'Dashboard flags products at or below this stock level'),
    ('catalogue.page_size',     '12',            'INTEGER', 'Products per page in the shop'),

    -- homepage copy
    ('content.hero_heading',    'Handwoven coir bird houses', 'STRING', 'Main headline on the home page'),
    ('content.hero_subheading', 'Each piece is shaped by hand from natural coconut fibre.', 'TEXT', 'Supporting line under the headline'),
    ('content.story_heading',   'Made by hand, one at a time', 'STRING', 'Craftsmanship section heading'),
    ('content.story_body',      '[PLACEHOLDER] Describe who makes these pieces and how. Replace this before launch.', 'TEXT', 'Craftsmanship section copy'),
    ('content.why_handmade_body', '[PLACEHOLDER] Explain what handmade means for your pieces. Replace this before launch.', 'TEXT', 'Why Choose Handmade section copy'),
    ('content.materials_body',  '[PLACEHOLDER] Describe the materials you work with. Avoid environmental claims you cannot support.', 'TEXT', 'Natural materials section copy'),
    ('content.checkout_notice', 'Your order will be confirmed by our team. Payment instructions will be shared separately.', 'TEXT', 'Shown prominently before order confirmation'),

    -- social
    ('social.instagram',        NULL,            'URL',     '[PLACEHOLDER] Full profile URL, or empty to hide the link'),
    ('social.facebook',         NULL,            'URL',     '[PLACEHOLDER] Full profile URL, or empty to hide the link'),
    ('social.youtube',          NULL,            'URL',     '[PLACEHOLDER] Full channel URL, or empty to hide the link'),

    -- policies
    ('policy.shipping',         '[PLACEHOLDER] Describe your delivery timelines and areas served.', 'HTML', 'Shipping policy page content'),
    ('policy.returns',          '[PLACEHOLDER] Describe your returns and exchange terms.',          'HTML', 'Returns policy page content'),
    ('policy.privacy',          '[PLACEHOLDER] Describe what customer data you collect and why.',   'HTML', 'Privacy policy page content');

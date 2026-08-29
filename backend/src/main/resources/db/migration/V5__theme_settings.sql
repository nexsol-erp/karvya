-- ---------------------------------------------------------------------------
-- Appearance: colours, fonts and corner radius
-- ---------------------------------------------------------------------------
--
-- These drive the storefront theme at runtime. They are public by necessity:
-- the browser needs them to paint the first screen, and nothing here is a
-- secret.
--
-- Every default below is the value the theme already shipped with, so applying
-- this migration changes nothing on screen. An empty value means "use the
-- built-in default" rather than "no colour", which is what lets an owner undo
-- a change without having to remember what was there before.

ALTER TABLE site_setting DROP CONSTRAINT ck_setting_type;
ALTER TABLE site_setting ADD CONSTRAINT ck_setting_type CHECK (value_type IN
    ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'URL', 'HTML', 'JSON',
     'COLOUR', 'FONT'));

INSERT INTO site_setting (setting_key, setting_value, value_type, description) VALUES
    ('theme.colour_primary',    '#A33B2E', 'COLOUR',
     'Accent colour. Primary buttons and links. Keep it dark enough for white text to read on it'),
    ('theme.colour_secondary',  '#A8743F', 'COLOUR',
     'Supporting accent, used sparingly'),
    ('theme.colour_background', '#F0E7D8', 'COLOUR',
     'Page background'),
    ('theme.colour_surface',    '#FBF7F0', 'COLOUR',
     'Cards and panels sitting on the page background'),
    ('theme.colour_text',       '#33322E', 'COLOUR',
     'Body text. Must contrast strongly with the background or the shop becomes hard to read'),

    ('theme.font_heading',      'Fraunces', 'FONT',
     'Typeface for headings'),
    ('theme.font_body',         'Karla',    'FONT',
     'Typeface for body text, buttons and form fields'),

    ('theme.corner_radius',     '12',       'INTEGER',
     'Corner rounding in pixels, 0 to 32. Zero gives square corners')
ON CONFLICT (setting_key) DO NOTHING;

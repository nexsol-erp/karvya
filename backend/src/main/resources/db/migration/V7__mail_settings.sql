-- ---------------------------------------------------------------------------
-- SMTP, configurable without a redeploy
-- ---------------------------------------------------------------------------
--
-- Mail was environment-only, read once at boot. That meant an owner who wanted
-- to start sending order confirmations had to edit a file on the server and
-- restart the application - which is not something the person running a shop
-- should have to do, and not something they can do from the admin at all.
--
-- The password is a real secret, unlike everything else in this table, so it
-- gets a value type of its own. SECRET is never returned by the API: the admin
-- screen is told whether one is stored, never what it is. An empty submission
-- therefore has to mean "leave it alone" rather than "clear it", or every save
-- of the surrounding form would wipe the password.
--
-- Empty settings fall back to the MAIL_* environment variables, so an existing
-- deployment configured that way keeps working untouched.

ALTER TABLE site_setting DROP CONSTRAINT ck_setting_type;
ALTER TABLE site_setting ADD CONSTRAINT ck_setting_type CHECK (value_type IN
    ('STRING', 'TEXT', 'INTEGER', 'DECIMAL', 'BOOLEAN', 'URL', 'HTML', 'JSON',
     'COLOUR', 'FONT', 'SECRET'));

INSERT INTO site_setting (setting_key, setting_value, value_type, description) VALUES
    ('mail.host', NULL, 'STRING',
     'SMTP server, e.g. smtp-relay.brevo.com. Empty falls back to MAIL_HOST'),
    ('mail.port', NULL, 'INTEGER',
     'Usually 587 for STARTTLS. Empty falls back to MAIL_PORT'),
    ('mail.username', NULL, 'STRING',
     'SMTP login. Often not the same as the from address'),
    ('mail.password', NULL, 'SECRET',
     'SMTP password or API key. Never shown again once saved; leave blank to keep the current one'),
    ('mail.from', NULL, 'STRING',
     'Address customers see. Most providers reject a domain they have not verified'),
    ('mail.auth', 'true', 'BOOLEAN',
     'Offer the username and password. Off only for a local relay that wants neither'),
    ('mail.starttls', 'true', 'BOOLEAN',
     'Upgrade the connection to TLS. Required by every hosted provider')
ON CONFLICT (setting_key) DO NOTHING;

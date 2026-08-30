-- ---------------------------------------------------------------------------
-- The social links are rendered now, so stop saying they are not
-- ---------------------------------------------------------------------------
--
-- These three carried "NOT SHOWN YET" so nobody would fill them in for a footer
-- that ignored them. The footer renders them, so the warning would now be the
-- lie instead.

UPDATE site_setting SET description =
    'Full address of your Instagram profile, e.g. https://instagram.com/yourshop. Shown in the footer; empty hides the icon'
 WHERE setting_key = 'social.instagram';

UPDATE site_setting SET description =
    'Full address of your Facebook page. Shown in the footer; empty hides the icon'
 WHERE setting_key = 'social.facebook';

UPDATE site_setting SET description =
    'Full address of your YouTube channel. Shown in the footer; empty hides the icon'
 WHERE setting_key = 'social.youtube';

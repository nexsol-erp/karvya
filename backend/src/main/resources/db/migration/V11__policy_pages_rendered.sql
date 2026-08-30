-- ---------------------------------------------------------------------------
-- The policy pages exist now, so stop saying they do not
-- ---------------------------------------------------------------------------
--
-- These three carried "NOT SHOWN YET" so nobody would write a returns policy no
-- customer could reach. There are pages at /shipping, /returns and /privacy,
-- and the footer links each one once it has been written, so the warning would
-- now be the lie instead.

UPDATE site_setting SET description =
    'Shown at /shipping. Delivery timelines and areas served. Empty leaves the page saying it is not published, and the footer does not link it'
 WHERE setting_key = 'policy.shipping';

UPDATE site_setting SET description =
    'Shown at /returns. Your returns and exchange terms. Empty leaves the page saying it is not published, and the footer does not link it'
 WHERE setting_key = 'policy.returns';

UPDATE site_setting SET description =
    'Shown at /privacy. What customer data you collect and why. Empty leaves the page saying it is not published, and the footer does not link it'
 WHERE setting_key = 'policy.privacy';

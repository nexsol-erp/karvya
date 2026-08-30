-- ---------------------------------------------------------------------------
-- The hero and the address are rendered now, so stop saying they are not
-- ---------------------------------------------------------------------------
--
-- The last three settings carrying "NOT SHOWN YET". The home page leads with
-- the hero copy and the footer shows the address, so the warning would now be
-- the lie instead - and with these three, nothing in this table claims to do
-- something it does not.

UPDATE site_setting SET description =
    'The first line on the home page. Empty falls back to the shop name'
 WHERE setting_key = 'content.hero_heading';

UPDATE site_setting SET description =
    'The sentence under it, and the page description search engines show. Empty hides the line'
 WHERE setting_key = 'content.hero_subheading';

UPDATE site_setting SET description =
    'Shown in the footer, under the contact details. Line breaks are kept, so write it as you would on an envelope'
 WHERE setting_key = 'contact.address';

-- The home page also leads with the tagline, above the heading, which its
-- description did not mention.
UPDATE site_setting SET description =
    'Short line beside the shop name in the header, and above the heading on the home page'
 WHERE setting_key = 'store.tagline';

-- ---------------------------------------------------------------------------
-- Say where each piece of copy actually appears
-- ---------------------------------------------------------------------------
--
-- These descriptions are the only guidance an administrator gets in the
-- Settings screen, and several of them named a section that does not exist:
-- 'content.story_body' was described as the craftsmanship section of the home
-- page, but no page read it. Someone following that description would write
-- copy, save it, and find the shop unchanged with nothing to say why.
--
-- Three of them now feed the Our Story page. The rest are still not rendered
-- anywhere, and say so, so that nobody spends an afternoon writing a returns
-- policy that no customer can reach.

UPDATE site_setting SET description =
    'Heading above the story on the Our Story page'
 WHERE setting_key = 'content.story_heading';

UPDATE site_setting SET description =
    'The story on the Our Story page. Leave a blank line between paragraphs'
 WHERE setting_key = 'content.story_body';

UPDATE site_setting SET description =
    '[PLACEHOLDER] "Why handmade" section on the Our Story page. Hidden until written. Explain what handmade means for your pieces'
 WHERE setting_key = 'content.why_handmade_body';

UPDATE site_setting SET description =
    '[PLACEHOLDER] "Natural materials" section on the Our Story page. Hidden until written. Avoid environmental claims you cannot support'
 WHERE setting_key = 'content.materials_body';

UPDATE site_setting SET description =
    'NOT SHOWN YET - no page reads this. Main headline intended for the home page'
 WHERE setting_key = 'content.hero_heading';

UPDATE site_setting SET description =
    'NOT SHOWN YET - no page reads this. Supporting line intended under the headline'
 WHERE setting_key = 'content.hero_subheading';

UPDATE site_setting SET description =
    'NOT SHOWN YET - there is no shipping policy page. Describe your delivery timelines and areas served'
 WHERE setting_key = 'policy.shipping';

UPDATE site_setting SET description =
    'NOT SHOWN YET - there is no returns policy page. Describe your returns and exchange terms'
 WHERE setting_key = 'policy.returns';

UPDATE site_setting SET description =
    'NOT SHOWN YET - there is no privacy policy page. Describe what customer data you collect and why'
 WHERE setting_key = 'policy.privacy';

UPDATE site_setting SET description =
    'NOT SHOWN YET - the footer does not render it. Business address'
 WHERE setting_key = 'contact.address';

UPDATE site_setting SET description =
    'NOT SHOWN YET - the footer does not render social links'
 WHERE setting_key IN ('social.instagram', 'social.facebook', 'social.youtube');

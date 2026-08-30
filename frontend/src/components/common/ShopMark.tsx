import Box from '@mui/material/Box';
import Typography from '@mui/material/Typography';

import { useSiteSettings } from '../../hooks/useSiteSettings';

/**
 * The shop's logo, or its name set as a wordmark.
 *
 * <p>One component for the header and the footer, so the two cannot drift into
 * showing different things. The name is the alt text rather than a decorative
 * empty string: this is the link back to the home page, and a screen reader
 * announcing an unnamed link at the top of every page is the difference between
 * a site being navigable and not.
 *
 * <p>The height is fixed and the width follows, because a shop's mark may be a
 * wide wordmark or a square badge and neither should be stretched to match the
 * other.
 */
export function ShopMark({
  height,
  showTagline = false,
}: {
  height: number | { xs: number; md: number };
  showTagline?: boolean;
}) {
  const settings = useSiteSettings();

  if (settings.logoKey) {
    return (
      <Box
        component="img"
        src={`/media/${settings.logoKey}.png`}
        alt={settings.storeName}
        sx={{ height, width: 'auto', maxWidth: 240, display: 'block' }}
      />
    );
  }

  return (
    <Box>
      <Typography
        component="span"
        sx={{
          fontFamily: '"Fraunces", Georgia, serif',
          fontWeight: 600,
          fontSize: { xs: '1.4rem', md: '1.6rem' },
          letterSpacing: '-0.01em',
          display: 'block',
          lineHeight: 1.1,
        }}
      >
        {settings.storeName}
      </Typography>
      {showTagline && settings.tagline && (
        <Typography
          component="span"
          sx={{
            fontSize: 11,
            letterSpacing: '0.14em',
            textTransform: 'uppercase',
            color: 'text.secondary',
            display: { xs: 'none', sm: 'block' },
          }}
        >
          {settings.tagline}
        </Typography>
      )}
    </Box>
  );
}

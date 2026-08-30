import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import IconButton from '@mui/material/IconButton';
import InstagramIcon from '@mui/icons-material/Instagram';
import FacebookIcon from '@mui/icons-material/Facebook';
import YouTubeIcon from '@mui/icons-material/YouTube';
import { Link as RouterLink } from 'react-router-dom';

import { useSiteSettings } from '../../hooks/useSiteSettings';
import { whatsAppLink } from '../../lib/format';
import { isWritten } from '../../lib/copy';

/**
 * Site footer.
 *
 * <p>Contact rows render only when a value has actually been configured. An
 * address or social link that has not been supplied is omitted rather than
 * stubbed, so the footer never asserts something about the business that is
 * not true.
 */
/**
 * The networks that have been configured, in a fixed order.
 *
 * <p>Each is hidden until it has an address: an icon linking nowhere invites a
 * click that goes nowhere, which is worse than not offering it.
 */
const SOCIAL = [
  { key: 'instagram', label: 'Instagram', Icon: InstagramIcon },
  { key: 'facebook', label: 'Facebook', Icon: FacebookIcon },
  { key: 'youtube', label: 'YouTube', Icon: YouTubeIcon },
] as const;

/**
 * Whether a stored address is safe to put in an href.
 *
 * <p>The setting is typed as a URL and the server refuses anything that is not
 * http or https, so this should never reject anything. It is here because an
 * href is the one place a stored string becomes executable - a javascript:
 * address in a link runs on click - and a value written before that validation
 * existed, or straight into the database, would not have passed through it.
 */
function isWebAddress(value: string): boolean {
  try {
    const { protocol } = new URL(value);
    return protocol === 'http:' || protocol === 'https:';
  } catch {
    return false;
  }
}

export function Footer() {
  const settings = useSiteSettings();

  // Only the policies that have been written. Each page exists either way, so
  // an address already shared keeps working - but there is no reason to send
  // anyone from here to a page that says nothing yet.
  const policies = [
    { to: '/shipping', label: 'Delivery', body: settings.shippingPolicy },
    { to: '/returns', label: 'Returns', body: settings.returnsPolicy },
    { to: '/privacy', label: 'Privacy', body: settings.privacyPolicy },
  ].filter((policy) => isWritten(policy.body));

  // resolved once: only the networks that have a usable address
  const social = SOCIAL.map(({ key, label, Icon }) => ({
    key,
    label,
    Icon,
    href: settings[key],
  })).filter((entry) => entry.href !== '' && isWebAddress(entry.href));
  const year = new Date().getFullYear();

  return (
    <Box
      component="footer"
      sx={{ mt: 10, borderTop: 1, borderColor: 'divider', bgcolor: 'background.paper' }}
    >
      <Container maxWidth="lg" sx={{ py: { xs: 5, md: 7 } }}>
        <Stack
          direction={{ xs: 'column', md: 'row' }}
          spacing={{ xs: 4, md: 8 }}
          sx={{ justifyContent: 'space-between' }}
        >
          <Box sx={{ maxWidth: 340 }}>
            <Typography
              sx={{
                fontFamily: '"Fraunces", Georgia, serif',
                fontWeight: 600,
                fontSize: '1.5rem',
                mb: 1,
              }}
            >
              {settings.storeName}
            </Typography>
            <Typography variant="body2">{settings.tagline}</Typography>
          </Box>

          <Stack spacing={1.25}>
            <Typography variant="overline" color="text.secondary">
              Explore
            </Typography>
            <Link component={RouterLink} to="/shop" color="text.primary">
              Shop
            </Link>
            <Link component={RouterLink} to="/our-story" color="text.primary">
              Our Story
            </Link>
            <Link component={RouterLink} to="/contact" color="text.primary">
              Contact
            </Link>
          </Stack>

          <Stack spacing={1.25}>
            <Typography variant="overline" color="text.secondary">
              Get in touch
            </Typography>
            {settings.contactEmail ? (
              <Link href={`mailto:${settings.contactEmail}`} color="text.primary">
                {settings.contactEmail}
              </Link>
            ) : (
              <Typography variant="body2" color="text.disabled">
                Email address not yet configured
              </Typography>
            )}
            {settings.whatsAppEnabled ? (
              <Link
                href={whatsAppLink(settings.whatsAppNumber, `Hello ${settings.storeName}, I have a question.`)}
                target="_blank"
                rel="noopener noreferrer"
                color="text.primary"
              >
                Chat on WhatsApp
              </Link>
            ) : (
              <Typography variant="body2" color="text.disabled">
                WhatsApp number not yet configured
              </Typography>
            )}
          </Stack>

          <Stack spacing={1.25}>
            <Typography variant="overline" color="text.secondary">
              Policies
            </Typography>
            {policies.length > 0 ? (
              policies.map(({ to, label }) => (
                <Link key={to} component={RouterLink} to={to} color="text.primary">
                  {label}
                </Link>
              ))
            ) : (
              <Typography variant="body2" color="text.disabled">
                None published yet
              </Typography>
            )}
          </Stack>
        </Stack>

        {social.length > 0 && (
          <Stack direction="row" spacing={0.5} sx={{ mt: 4 }}>
            {social.map(({ key, label, Icon, href }) => (
              <IconButton
                key={key}
                component="a"
                href={href}
                target="_blank"
                rel="noopener noreferrer me"
                // the icon carries no text, so the link needs a name of its own
                aria-label={`${settings.storeName} on ${label}`}
                size="small"
                sx={{ color: 'text.secondary', '&:hover': { color: 'text.primary' } }}
              >
                <Icon fontSize="small" />
              </IconButton>
            ))}
          </Stack>
        )}

        <Typography variant="body2" sx={{ mt: social.length > 0 ? 3 : 6, pt: 3, borderTop: 1, borderColor: 'divider' }}>
          © {year} {settings.storeName}. All rights reserved.
        </Typography>
      </Container>
    </Box>
  );
}

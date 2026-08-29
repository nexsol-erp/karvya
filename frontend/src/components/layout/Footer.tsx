import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Link from '@mui/material/Link';
import { Link as RouterLink } from 'react-router-dom';

import { config, whatsAppEnabled } from '../../config';
import { whatsAppLink } from '../../lib/format';

/**
 * Site footer.
 *
 * <p>Contact rows render only when a value has actually been configured. An
 * address or social link that has not been supplied is omitted rather than
 * stubbed, so the footer never asserts something about the business that is
 * not true.
 */
export function Footer() {
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
              {config.storeName}
            </Typography>
            <Typography variant="body2">{config.tagline}</Typography>
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
            {config.contactEmail ? (
              <Link href={`mailto:${config.contactEmail}`} color="text.primary">
                {config.contactEmail}
              </Link>
            ) : (
              <Typography variant="body2" color="text.disabled">
                Email address not yet configured
              </Typography>
            )}
            {whatsAppEnabled() ? (
              <Link
                href={whatsAppLink(config.whatsAppNumber, `Hello ${config.storeName}, I have a question.`)}
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
        </Stack>

        <Typography variant="body2" sx={{ mt: 6, pt: 3, borderTop: 1, borderColor: 'divider' }}>
          © {year} {config.storeName}. All rights reserved.
        </Typography>
      </Container>
    </Box>
  );
}

import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Box from '@mui/material/Box';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import EmailIcon from '@mui/icons-material/EmailOutlined';

import { SEOHead } from '../components/common/SEOHead';
import { config, whatsAppEnabled } from '../config';
import { whatsAppLink } from '../lib/format';

/**
 * Contact page.
 *
 * <p>The enquiry form arrives with its backend endpoint. Until then this page
 * offers only the channels that genuinely work, and says plainly when one has
 * not been configured - a form that silently discarded messages would be worse
 * than no form.
 */
export function Contact() {
  const hasEmail = Boolean(config.contactEmail);
  const hasWhatsApp = whatsAppEnabled();

  return (
    <Container maxWidth="sm" sx={{ py: { xs: 5, md: 9 } }}>
      <SEOHead
        title="Contact"
        description={`Get in touch with ${config.storeName}.`}
        path="/contact"
      />

      <Typography variant="overline" color="text.secondary">
        Get in touch
      </Typography>
      <Typography variant="h1" sx={{ fontSize: { xs: '2.3rem', md: '3rem' }, mb: 3 }}>
        Contact
      </Typography>

      <Typography sx={{ mb: 4 }}>
        Questions about a piece, an order, or a custom request are all welcome.
      </Typography>

      {!hasEmail && !hasWhatsApp && (
        <Alert severity="warning" variant="outlined" sx={{ mb: 3 }}>
          No contact channel has been configured yet. An administrator can add
          an email address and a WhatsApp number in site settings.
        </Alert>
      )}

      <Stack spacing={2}>
        {hasWhatsApp && (
          <Button
            component="a"
            href={whatsAppLink(
              config.whatsAppNumber,
              `Hello ${config.storeName}, I have a question.`,
            )}
            target="_blank"
            rel="noopener noreferrer"
            variant="contained"
            size="large"
            startIcon={<WhatsAppIcon />}
          >
            Chat on WhatsApp
          </Button>
        )}

        {hasEmail && (
          <Button
            component="a"
            href={`mailto:${config.contactEmail}`}
            variant="outlined"
            size="large"
            startIcon={<EmailIcon />}
          >
            {config.contactEmail}
          </Button>
        )}
      </Stack>

      <Box sx={{ mt: 5, p: 2.5, borderRadius: 3, border: 1, borderColor: 'divider' }}>
        <Typography variant="body2">
          An enquiry form that saves your message and notifies our team is on
          its way. For now, the channels above reach us directly.
        </Typography>
      </Box>
    </Container>
  );
}

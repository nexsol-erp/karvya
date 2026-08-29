import { useState } from 'react';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import TextField from '@mui/material/TextField';
import Divider from '@mui/material/Divider';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import EmailIcon from '@mui/icons-material/EmailOutlined';

import { SEOHead } from '../components/common/SEOHead';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { whatsAppLink } from '../lib/format';
import { ApiError } from '../api/client';
import { submitEnquiry } from '../api/enquiries';

const BLANK = { name: '', email: '', phone: '', subject: '', message: '', website: '' };

/**
 * Contact page.
 *
 * <p>The form is the reliable channel: it stores the message and notifies the
 * shop, so an enquiry survives whether or not anyone is watching WhatsApp.
 * The direct channels stay above it for anyone who would rather use them, and
 * hide themselves when they have not been configured - a link that goes
 * nowhere is worse than no link.
 */
export function Contact() {
  const settings = useSiteSettings();
  const hasEmail = Boolean(settings.contactEmail);
  const hasWhatsApp = settings.whatsAppEnabled;

  const [form, setForm] = useState(BLANK);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [sent, setSent] = useState(false);
  const [sending, setSending] = useState(false);

  const set =
    (key: keyof typeof BLANK) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [key]: event.target.value }));

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSending(true);

    try {
      await submitEnquiry(form);
      setSent(true);
      setForm(BLANK);
    } catch (err) {
      if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setFieldErrors(err.fieldErrors);
        setError('Please check the highlighted fields.');
      } else if (err instanceof ApiError && err.status === 429) {
        // the limit is per address per hour, and saying so is more use than
        // "something went wrong" to someone who simply sent two messages
        setError(
          'That is several messages in a short time. Please wait a little while, ' +
            'or use one of the channels above if it is urgent.',
        );
      } else {
        setError('Your message could not be sent just now. Please try again.');
      }
    } finally {
      setSending(false);
    }
  }

  return (
    <Container maxWidth="sm" sx={{ py: { xs: 5, md: 9 } }}>
      <SEOHead
        title="Contact"
        description={`Get in touch with ${settings.storeName}.`}
        path="/contact"
      />

      <Typography variant="overline" color="text.secondary">
        Get in touch
      </Typography>
      <Typography variant="h1" sx={{ fontSize: { xs: '2.3rem', md: '3rem' }, mb: 3 }}>
        Contact
      </Typography>

      <Typography sx={{ mb: 4 }}>
        Questions about an item, an order, or a custom request are all welcome.
      </Typography>

      <Stack spacing={2}>
        {hasWhatsApp && (
          <Button
            component="a"
            href={whatsAppLink(
              settings.whatsAppNumber,
              `Hello ${settings.storeName}, I have a question.`,
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
            href={`mailto:${settings.contactEmail}`}
            variant="outlined"
            size="large"
            startIcon={<EmailIcon />}
          >
            {settings.contactEmail}
          </Button>
        )}
      </Stack>

      {(hasWhatsApp || hasEmail) && (
        <Divider sx={{ my: 4 }}>
          <Typography variant="body2">or send a message</Typography>
        </Divider>
      )}

      <Card sx={{ p: { xs: 2.5, md: 3 }, mt: hasWhatsApp || hasEmail ? 0 : 4 }}>
        {sent ? (
          <Alert severity="success">
            <AlertTitle>Thank you — your message has been sent</AlertTitle>
            We have it, and someone will reply to the address you gave. There is
            no need to send it again.
            <Box sx={{ mt: 1.5 }}>
              <Button size="small" onClick={() => setSent(false)}>
                Send another
              </Button>
            </Box>
          </Alert>
        ) : (
          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1.05rem', mb: 2 }}>
              Send us a message
            </Typography>

            {error && (
              <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
                {error}
              </Alert>
            )}

            <Grid container spacing={2}>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Your name"
                  value={form.name}
                  onChange={set('name')}
                  error={Boolean(fieldErrors.name)}
                  helperText={fieldErrors.name}
                  autoComplete="name"
                  required
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Email address"
                  type="email"
                  value={form.email}
                  onChange={set('email')}
                  error={Boolean(fieldErrors.email)}
                  helperText={fieldErrors.email ?? 'So we can reply'}
                  autoComplete="email"
                  required
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Phone (optional)"
                  value={form.phone}
                  onChange={set('phone')}
                  error={Boolean(fieldErrors.phone)}
                  helperText={fieldErrors.phone}
                  autoComplete="tel"
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12, sm: 6 }}>
                <TextField
                  label="Subject"
                  value={form.subject}
                  onChange={set('subject')}
                  error={Boolean(fieldErrors.subject)}
                  helperText={fieldErrors.subject}
                  required
                  fullWidth
                />
              </Grid>
              <Grid size={{ xs: 12 }}>
                <TextField
                  label="Message"
                  value={form.message}
                  onChange={set('message')}
                  error={Boolean(fieldErrors.message)}
                  helperText={fieldErrors.message}
                  multiline
                  minRows={5}
                  required
                  fullWidth
                />
              </Grid>
            </Grid>

            {/*
              A bot fills every field it finds; a person never sees this one.
              Positioned off-screen rather than display:none, which is the first
              thing a scraper checks for, and taken out of the tab order and the
              accessibility tree so nobody using a keyboard or a screen reader
              can land on it by accident.
            */}
            <Box
              aria-hidden="true"
              sx={{ position: 'absolute', left: '-9999px', width: 1, height: 1, overflow: 'hidden' }}
            >
              <label htmlFor="contact-website">Do not fill this in</label>
              <input
                id="contact-website"
                name="website"
                type="text"
                tabIndex={-1}
                autoComplete="off"
                value={form.website}
                onChange={set('website')}
              />
            </Box>

            <Button
              type="submit"
              variant="contained"
              size="large"
              disabled={sending}
              sx={{ mt: 2.5 }}
            >
              {sending ? 'Sending…' : 'Send message'}
            </Button>
          </Box>
        )}
      </Card>
    </Container>
  );
}

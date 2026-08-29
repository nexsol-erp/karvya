import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import Box from '@mui/material/Box';

import { SEOHead } from '../components/common/SEOHead';
import { forgotPassword } from '../api/auth';

/**
 * Requests a reset link.
 *
 * <p>The confirmation is deliberately non-committal about whether the address
 * is registered. The server answers identically either way, and saying "we
 * have sent you an email" would give away what the API is careful not to.
 */
export function ForgotPassword() {
  const [email, setEmail] = useState('');
  const [sent, setSent] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await forgotPassword(email);
      setSent(true);
    } catch {
      setError('We could not process that just now. Please try again in a moment.');
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ py: { xs: 6, md: 10 } }}>
      <SEOHead title="Reset your password" path="/forgot-password" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1 }}>
        Reset your password
      </Typography>

      {sent ? (
        <Stack spacing={3} sx={{ mt: 3 }}>
          <Alert severity="success">
            If an account exists for {email}, a reset link is on its way. The
            link is valid for one hour.
          </Alert>
          <Typography variant="body2">
            Nothing arrived? Check your spam folder, or{' '}
            <Link component="button" type="button" onClick={() => setSent(false)}>
              try a different address
            </Link>
            .
          </Typography>
          <Link component={RouterLink} to="/login" variant="body2">
            Back to sign in
          </Link>
        </Stack>
      ) : (
        <>
          <Typography variant="body2" sx={{ mb: 4 }}>
            Enter your email address and we will send you a link to set a new password.
          </Typography>

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Stack spacing={2.5}>
              {error && <Alert severity="error">{error}</Alert>}

              <TextField
                label="Email address"
                type="email"
                value={email}
                onChange={(e) => setEmail(e.target.value)}
                autoComplete="email"
                required
                fullWidth
                autoFocus
              />

              <Button type="submit" variant="contained" size="large" disabled={submitting}>
                {submitting ? 'Sending…' : 'Send reset link'}
              </Button>

              <Link component={RouterLink} to="/login" variant="body2">
                Back to sign in
              </Link>
            </Stack>
          </Box>
        </>
      )}
    </Container>
  );
}

import { useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import Box from '@mui/material/Box';

import { SEOHead } from '../components/common/SEOHead';
import { register } from '../api/auth';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/client';

const MIN_PASSWORD_LENGTH = 10;

export function Register() {
  const { signIn } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState({ fullName: '', email: '', phone: '', password: '' });
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }));

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setSubmitting(true);
    try {
      await register({
        fullName: form.fullName,
        email: form.email,
        phone: form.phone || undefined,
        password: form.password,
      });
      // sign straight in, so the visitor is not asked to type it all again
      await signIn(form.email, form.password);
      navigate('/account', { replace: true });
    } catch (err) {
      if (err instanceof ApiError) {
        // the server names the offending fields; surface them on the inputs
        if (Object.keys(err.fieldErrors).length > 0) {
          setFieldErrors(err.fieldErrors);
        } else {
          setError(err.message);
        }
      } else {
        setError('We could not create your account just now. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ py: { xs: 6, md: 10 } }}>
      <SEOHead title="Create an account" path="/register" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1 }}>
        Create an account
      </Typography>
      <Typography variant="body2" sx={{ mb: 4 }}>
        To keep your addresses and follow your orders. You can also order as a guest.
      </Typography>

      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Stack spacing={2.5}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="Your name"
            value={form.fullName}
            onChange={set('fullName')}
            error={Boolean(fieldErrors.fullName)}
            helperText={fieldErrors.fullName}
            autoComplete="name"
            required
            fullWidth
            autoFocus
          />

          <TextField
            label="Email address"
            type="email"
            value={form.email}
            onChange={set('email')}
            error={Boolean(fieldErrors.email)}
            helperText={fieldErrors.email}
            autoComplete="email"
            required
            fullWidth
          />

          <TextField
            label="Mobile number (optional)"
            value={form.phone}
            onChange={set('phone')}
            error={Boolean(fieldErrors.phone)}
            helperText={fieldErrors.phone ?? 'Used to reach you about an order'}
            autoComplete="tel"
            fullWidth
          />

          <TextField
            label="Password"
            type="password"
            value={form.password}
            onChange={set('password')}
            error={Boolean(fieldErrors.password)}
            helperText={fieldErrors.password ?? `At least ${MIN_PASSWORD_LENGTH} characters`}
            autoComplete="new-password"
            required
            fullWidth
          />

          <Button type="submit" variant="contained" size="large" disabled={submitting}>
            {submitting ? 'Creating your account…' : 'Create account'}
          </Button>

          <Typography variant="body2" sx={{ pt: 0.5 }}>
            Already have an account?{' '}
            <Link component={RouterLink} to="/login">
              Sign in
            </Link>
          </Typography>
        </Stack>
      </Box>
    </Container>
  );
}

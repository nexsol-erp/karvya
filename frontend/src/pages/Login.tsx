import { useState } from 'react';
import { Link as RouterLink, useNavigate, useLocation } from 'react-router-dom';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import Box from '@mui/material/Box';

import { SEOHead } from '../components/common/SEOHead';
import { useAuth } from '../hooks/useAuth';
import { ApiError } from '../api/client';

export function Login() {
  const { signIn } = useAuth();
  const navigate = useNavigate();
  const location = useLocation();

  const [email, setEmail] = useState('');
  const [password, setPassword] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  // return the visitor to wherever they were headed before being asked to sign in
  const redirectTo = (location.state as { from?: string } | null)?.from ?? '/account';

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setSubmitting(true);
    try {
      await signIn(email, password);
      navigate(redirectTo, { replace: true });
    } catch (err) {
      // the server does not say whether the account exists, and neither does this
      if (err instanceof ApiError && err.status === 401) {
        setError('That email address and password do not match.');
      } else if (err instanceof ApiError && err.status === 429) {
        setError('Too many attempts. Please wait a few minutes and try again.');
      } else {
        setError('We could not sign you in just now. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Container maxWidth="xs" sx={{ py: { xs: 6, md: 10 } }}>
      <SEOHead title="Sign in" path="/login" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1 }}>
        Sign in
      </Typography>
      <Typography variant="body2" sx={{ mb: 4 }}>
        To see your orders and saved addresses.
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

          <TextField
            label="Password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            autoComplete="current-password"
            required
            fullWidth
          />

          <Button type="submit" variant="contained" size="large" disabled={submitting}>
            {submitting ? 'Signing in…' : 'Sign in'}
          </Button>

          <Stack direction='row' sx={{ justifyContent: 'space-between', pt: 0.5 }}>
            <Link component={RouterLink} to="/forgot-password" variant="body2">
              Forgot your password?
            </Link>
            <Link component={RouterLink} to="/register" variant="body2">
              Create an account
            </Link>
          </Stack>
        </Stack>
      </Box>
    </Container>
  );
}

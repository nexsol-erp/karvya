import { useState } from 'react';
import { Link as RouterLink, useSearchParams, useNavigate } from 'react-router-dom';
import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import Link from '@mui/material/Link';
import Box from '@mui/material/Box';

import { SEOHead } from '../components/common/SEOHead';
import { resetPassword } from '../api/auth';
import { ApiError } from '../api/client';

const MIN_PASSWORD_LENGTH = 10;

export function ResetPassword() {
  const [params] = useSearchParams();
  const navigate = useNavigate();
  const token = params.get('token') ?? '';

  const [password, setPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const mismatch = confirm.length > 0 && password !== confirm;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (mismatch) return;

    setError(null);
    setSubmitting(true);
    try {
      await resetPassword({ token, newPassword: password });
      navigate('/login', {
        replace: true,
        state: { notice: 'Your password has been changed. Please sign in.' },
      });
    } catch (err) {
      if (err instanceof ApiError && err.fieldErrors.newPassword) {
        setError(err.fieldErrors.newPassword);
      } else if (err instanceof ApiError && err.status === 422) {
        setError('That reset link is no longer valid. Please request a new one.');
      } else {
        setError('We could not reset your password just now. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!token) {
    return (
      <Container maxWidth="xs" sx={{ py: { xs: 6, md: 10 } }}>
        <SEOHead title="Reset your password" path="/reset-password" noIndex />
        <Alert severity="warning" sx={{ mb: 3 }}>
          This reset link is incomplete. Please open the link from your email
          exactly as it was sent.
        </Alert>
        <Link component={RouterLink} to="/forgot-password">
          Request a new link
        </Link>
      </Container>
    );
  }

  return (
    <Container maxWidth="xs" sx={{ py: { xs: 6, md: 10 } }}>
      <SEOHead title="Choose a new password" path="/reset-password" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1 }}>
        Choose a new password
      </Typography>
      <Typography variant="body2" sx={{ mb: 4 }}>
        Signing in again afterwards will use this new password.
      </Typography>

      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Stack spacing={2.5}>
          {error && <Alert severity="error">{error}</Alert>}

          <TextField
            label="New password"
            type="password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            helperText={`At least ${MIN_PASSWORD_LENGTH} characters`}
            autoComplete="new-password"
            required
            fullWidth
            autoFocus
          />

          <TextField
            label="Confirm new password"
            type="password"
            value={confirm}
            onChange={(e) => setConfirm(e.target.value)}
            error={mismatch}
            helperText={mismatch ? 'The two passwords do not match' : ' '}
            autoComplete="new-password"
            required
            fullWidth
          />

          <Button
            type="submit"
            variant="contained"
            size="large"
            disabled={submitting || mismatch || password.length < MIN_PASSWORD_LENGTH}
          >
            {submitting ? 'Saving…' : 'Save new password'}
          </Button>
        </Stack>
      </Box>
    </Container>
  );
}

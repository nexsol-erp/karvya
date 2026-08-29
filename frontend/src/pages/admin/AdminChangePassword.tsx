import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import Container from '@mui/material/Container';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';

import { SEOHead } from '../../components/common/SEOHead';
import { changePassword } from '../../api/auth';
import { useAuth } from '../../hooks/useAuth';
import { ApiError } from '../../api/client';

const MIN_LENGTH = 10;

/**
 * Forced password change.
 *
 * <p>Reached when the account still carries the credential it was created
 * with. Changing it ends the session, which is correct - the new password must
 * be proved, not assumed - so this signs back in explicitly afterwards.
 */
export function AdminChangePassword() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  const [currentPassword, setCurrentPassword] = useState('');
  const [newPassword, setNewPassword] = useState('');
  const [confirm, setConfirm] = useState('');
  const [error, setError] = useState<string | null>(null);
  const [submitting, setSubmitting] = useState(false);

  const mismatch = confirm.length > 0 && newPassword !== confirm;
  const tooShort = newPassword.length > 0 && newPassword.length < MIN_LENGTH;

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    if (mismatch || tooShort) return;

    setError(null);
    setSubmitting(true);
    try {
      await changePassword({ currentPassword, newPassword });
      // the server invalidated the session; clear ours and ask them to sign in
      await signOut();
      navigate('/admin/login', {
        replace: true,
        state: { notice: 'Your password has been changed. Please sign in again.' },
      });
    } catch (err) {
      if (err instanceof ApiError && err.status === 422) {
        setError(err.message);
      } else if (err instanceof ApiError && Object.keys(err.fieldErrors).length > 0) {
        setError(Object.values(err.fieldErrors)[0]);
      } else {
        setError('We could not change your password just now. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  return (
    <Box sx={{ minHeight: '100dvh', display: 'grid', placeItems: 'center', bgcolor: 'background.default' }}>
      <Container maxWidth="xs">
        <SEOHead title="Choose a new password" path="/admin/change-password" noIndex />

        <Card sx={{ p: 3 }}>
          <Typography variant="h6" component="h1" sx={{ mb: 1 }}>
            Choose a new password
          </Typography>

          {user?.mustChangePassword && (
            <Alert severity="warning" sx={{ mb: 2.5 }}>
              <AlertTitle>Before you continue</AlertTitle>
              This account still uses the password it was set up with. Choose a
              new one to open the back office.
            </Alert>
          )}

          <Box component="form" onSubmit={handleSubmit} noValidate>
            <Stack spacing={2.5}>
              {error && <Alert severity="error">{error}</Alert>}

              <TextField
                label="Current password"
                type="password"
                value={currentPassword}
                onChange={(e) => setCurrentPassword(e.target.value)}
                autoComplete="current-password"
                required
                fullWidth
                autoFocus
              />

              <TextField
                label="New password"
                type="password"
                value={newPassword}
                onChange={(e) => setNewPassword(e.target.value)}
                error={tooShort}
                helperText={tooShort ? `Use at least ${MIN_LENGTH} characters` : `At least ${MIN_LENGTH} characters`}
                autoComplete="new-password"
                required
                fullWidth
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
                disabled={submitting || mismatch || tooShort || newPassword.length === 0}
              >
                {submitting ? 'Saving…' : 'Save and sign in again'}
              </Button>
            </Stack>
          </Box>
        </Card>
      </Container>
    </Box>
  );
}

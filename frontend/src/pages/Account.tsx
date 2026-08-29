import { useQuery } from '@tanstack/react-query';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Chip from '@mui/material/Chip';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import { useNavigate } from 'react-router-dom';

import { SEOHead } from '../components/common/SEOHead';
import { EmptyState } from '../components/common/EmptyState';
import { authKeys, getProfile, listAddresses } from '../api/auth';
import { useAuth } from '../hooks/useAuth';

/**
 * Account overview.
 *
 * <p>Order history joins this page once orders exist. Nothing here is stubbed
 * in the meantime - a panel that always reads "no orders" would be indistinguishable
 * from a broken one.
 */
export function Account() {
  const { user, signOut } = useAuth();
  const navigate = useNavigate();

  const profile = useQuery({ queryKey: authKeys.profile, queryFn: getProfile });
  const addresses = useQuery({ queryKey: authKeys.addresses, queryFn: listAddresses });

  async function handleSignOut() {
    await signOut();
    navigate('/', { replace: true });
  }

  return (
    <Container maxWidth="md" sx={{ py: { xs: 4, md: 7 } }}>
      <SEOHead title="Your account" path="/account" noIndex />

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={2}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'baseline' }, mb: 4 }}
      >
        <div>
          <Typography variant="overline" color="text.secondary">
            Your account
          </Typography>
          <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.6rem' } }}>
            {user?.fullName ?? 'Welcome'}
          </Typography>
        </div>
        <Button variant="outlined" onClick={handleSignOut}>
          Sign out
        </Button>
      </Stack>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
              Your details
            </Typography>

            {profile.isPending ? (
              <Stack spacing={1}>
                <Skeleton width="70%" />
                <Skeleton width="50%" />
                <Skeleton width="60%" />
              </Stack>
            ) : profile.isError ? (
              <Alert severity="error">Your details could not be loaded.</Alert>
            ) : (
              <Stack spacing={1.5} divider={<Divider flexItem />}>
                <Row label="Name" value={profile.data.fullName} />
                <Row label="Email" value={profile.data.email} />
                <Row label="Phone" value={profile.data.phone ?? 'Not provided'} />
                <Row
                  label="Member since"
                  value={new Date(profile.data.memberSince).toLocaleDateString('en-IN', {
                    year: 'numeric',
                    month: 'long',
                  })}
                />
              </Stack>
            )}
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 6 }}>
          <Card sx={{ p: 3, height: '100%' }}>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
              Delivery addresses
            </Typography>

            {addresses.isPending ? (
              <Stack spacing={1}>
                <Skeleton width="80%" />
                <Skeleton width="65%" />
              </Stack>
            ) : (addresses.data?.length ?? 0) === 0 ? (
              <EmptyState
                title="No saved addresses"
                description="Addresses you use at checkout can be saved here for next time."
              />
            ) : (
              <Stack spacing={2} divider={<Divider flexItem />}>
                {addresses.data?.map((address) => (
                  <div key={address.id}>
                    <Stack
                      direction="row"
                      spacing={1}
                      sx={{ alignItems: 'center', mb: 0.5, flexWrap: 'wrap' }}
                    >
                      <Typography sx={{ fontWeight: 700 }}>
                        {address.label ?? address.recipientName}
                      </Typography>
                      {address.isDefault && <Chip size="small" color="primary" label="Default" />}
                    </Stack>
                    <Typography variant="body2">
                      {[address.line1, address.line2, address.city, address.state, address.postalCode]
                        .filter(Boolean)
                        .join(', ')}
                    </Typography>
                    <Typography variant="body2">{address.phone}</Typography>
                  </div>
                ))}
              </Stack>
            )}
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" spacing={2} sx={{ justifyContent: 'space-between' }}>
      <Typography variant="body2" sx={{ color: 'text.secondary', fontWeight: 600 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ textAlign: 'right' }}>
        {value}
      </Typography>
    </Stack>
  );
}

import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import { Link as RouterLink } from 'react-router-dom';

import { SEOHead } from '../components/common/SEOHead';

export function NotFound() {
  return (
    <Container maxWidth="sm" sx={{ py: { xs: 10, md: 16 }, textAlign: 'center' }}>
      <SEOHead title="Page not found" path="/404" noIndex />

      <Stack spacing={3} sx={{ alignItems: 'center' }}>
        <Typography
          sx={{
            fontFamily: '"Fraunces", Georgia, serif',
            fontSize: { xs: '4rem', md: '5.5rem' },
            fontWeight: 600,
            lineHeight: 1,
            color: 'primary.main',
          }}
        >
          404
        </Typography>

        <Typography variant="h4" component="h1">
          We could not find that page
        </Typography>

        <Typography variant="body1" color="text.secondary" sx={{ maxWidth: '44ch' }}>
          The link may be out of date, or the piece may no longer be listed.
        </Typography>

        <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 1 }}>
          <Button component={RouterLink} to="/shop" variant="contained" size="large">
            Browse the shop
          </Button>
          <Button component={RouterLink} to="/" variant="outlined" size="large">
            Go home
          </Button>
        </Stack>
      </Stack>
    </Container>
  );
}

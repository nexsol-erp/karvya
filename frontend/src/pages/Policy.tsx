import Container from '@mui/material/Container';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Box from '@mui/material/Box';

import { SEOHead } from '../components/common/SEOHead';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { isWritten } from '../lib/copy';

export type PolicyKind = 'shipping' | 'returns' | 'privacy';

const POLICIES: Record<PolicyKind, { title: string; description: string; setting: string }> = {
  shipping: {
    title: 'Delivery',
    description: 'How and when orders are delivered.',
    setting: 'policy.shipping',
  },
  returns: {
    title: 'Returns',
    description: 'Returns and exchanges.',
    setting: 'policy.returns',
  },
  privacy: {
    title: 'Privacy',
    description: 'What we collect, and why.',
    setting: 'policy.privacy',
  },
};

/**
 * One of the three policy pages.
 *
 * <p>The page exists whether or not the policy has been written, because the
 * address is the sort of thing that gets linked to from elsewhere - an order
 * confirmation, a marketplace listing, somebody's email - and a link that 404s
 * later is worse than one that says the policy is not published yet. The footer
 * only offers the ones that have been written, so nothing on this site leads
 * here empty.
 *
 * <p>The copy is rich text, cleaned by the server both when it is saved and
 * again when it is served.
 */
export function Policy({ kind }: { kind: PolicyKind }) {
  const settings = useSiteSettings();
  const { title, description, setting } = POLICIES[kind];

  const html =
    kind === 'shipping'
      ? settings.shippingPolicy
      : kind === 'returns'
        ? settings.returnsPolicy
        : settings.privacyPolicy;

  const written = isWritten(html);

  return (
    <Container maxWidth="md" sx={{ py: { xs: 5, md: 9 } }}>
      <SEOHead
        title={title}
        description={`${description} ${settings.storeName}.`}
        path={`/${kind}`}
        // an unwritten policy is not worth a search result
        noIndex={!written}
      />

      <Typography variant="overline" color="text.secondary">
        {settings.storeName}
      </Typography>
      <Typography variant="h1" sx={{ fontSize: { xs: '2.3rem', md: '3rem' }, mb: 3 }}>
        {title}
      </Typography>

      {written ? (
        <Box
          // Cleaned by the server on save and again on read, against the same
          // list of permitted tags. Rendered as markup because that is what it
          // is: an owner writing a returns policy wants paragraphs and links.
          dangerouslySetInnerHTML={{ __html: html as string }}
          sx={{
            '& p': { lineHeight: 1.7, mb: 2 },
            '& ul, & ol': { pl: 3, mb: 2 },
            '& li': { mb: 0.75 },
            '& h2, & h3': { mt: 3, mb: 1.5, fontSize: '1.25rem', fontWeight: 600 },
            '& a': { color: 'primary.main' },
            '& img': { maxWidth: '100%', height: 'auto', borderRadius: 1 },
          }}
        />
      ) : (
        <Alert severity="info" variant="outlined">
          <AlertTitle>Not published yet</AlertTitle>
          This policy has not been written. Nothing on the site links here until
          it is — an administrator can add it under Settings, in{' '}
          <strong>{setting}</strong>.
        </Alert>
      )}
    </Container>
  );
}

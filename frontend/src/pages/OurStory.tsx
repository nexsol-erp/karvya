import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';

import { SEOHead } from '../components/common/SEOHead';
import { config } from '../config';

/**
 * Brand story.
 *
 * <p>The copy here is deliberately thin. Nothing about who makes these pieces,
 * where, or since when has been supplied, and inventing it would put a false
 * claim in front of customers. The notice stays visible until real copy is
 * entered in site settings.
 */
export function OurStory() {
  return (
    <Container maxWidth="md" sx={{ py: { xs: 5, md: 9 } }}>
      <SEOHead
        title="Our Story"
        description={`About ${config.storeName} and the coir pieces we make.`}
        path="/our-story"
      />

      <Typography variant="overline" color="text.secondary">
        About us
      </Typography>
      <Typography variant="h1" sx={{ fontSize: { xs: '2.3rem', md: '3rem' }, mb: 4 }}>
        Our Story
      </Typography>

      <Alert severity="info" variant="outlined" sx={{ mb: 4 }}>
        This page is awaiting its final copy. An administrator can replace the
        text below from site settings.
      </Alert>

      <Stack spacing={3}>
        <Typography>
          {config.storeName} makes hanging bird houses and nesting shelters from
          coir, the fibre drawn from coconut husk. Each piece is wound by hand
          over a form and finished individually, which is why the texture and
          shape vary from one to the next.
        </Typography>

        <Typography>
          Some pieces use dyed fibre for the roof, and others are left in the
          natural colour of the husk. Cord or rope is fitted for hanging. Where
          a piece uses another material, it is listed on that product page
          rather than described in general terms here.
        </Typography>

        <Typography color="text.secondary">
          More about the people who make these pieces, and how they are made,
          will appear here once that has been written.
        </Typography>
      </Stack>
    </Container>
  );
}

import Container from '@mui/material/Container';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Alert from '@mui/material/Alert';

import { SEOHead } from '../components/common/SEOHead';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { isWritten, paragraphs } from '../lib/copy';

/**
 * Brand story, written by the shop owner in site settings.
 *
 * <p>Three settings feed this page: content.story_body, content.why_handmade_body
 * and content.materials_body. Each section appears only once its copy has been
 * written - seeded text still carrying the [PLACEHOLDER] marker is treated as
 * absent, because printing the word PLACEHOLDER at a customer is worse than
 * showing them less.
 *
 * <p>Where the story has not been written, the page falls back to the few things
 * that are true of every piece regardless of who made it. That is deliberately
 * thin: nothing about the people, the place or the years is known here, and
 * inventing it would put a false claim in front of customers.
 */
export function OurStory() {
  const settings = useSiteSettings();

  const story = isWritten(settings.storyBody);
  const whyHandmade = isWritten(settings.whyHandmadeBody);
  const materials = isWritten(settings.materialsBody);

  const storyHeading = isWritten(settings.storyHeading)
    ? settings.storyHeading
    : 'Made by hand, one at a time';

  return (
    <Container maxWidth="md" sx={{ py: { xs: 5, md: 9 } }}>
      <SEOHead
        title="Our Story"
        description={
          story
            ? paragraphs(settings.storyBody)[0].slice(0, 155)
            : `About ${settings.storeName} and the coir pieces we make.`
        }
        path="/our-story"
      />

      <Typography variant="overline" color="text.secondary">
        About us
      </Typography>
      <Typography variant="h1" sx={{ fontSize: { xs: '2.3rem', md: '3rem' }, mb: 4 }}>
        Our Story
      </Typography>

      {!story && (
        <Alert severity="info" variant="outlined" sx={{ mb: 4 }}>
          This page is awaiting its final copy. An administrator can write it
          under Settings, in <strong>content.story_body</strong>.
        </Alert>
      )}

      <Stack spacing={5}>
        <Stack spacing={2}>
          <Typography variant="h2" sx={{ fontSize: { xs: '1.5rem', md: '1.9rem' } }}>
            {storyHeading}
          </Typography>

          {story ? (
            paragraphs(settings.storyBody).map((text, index) => (
              <Typography key={index}>{text}</Typography>
            ))
          ) : (
            <>
              <Typography>
                {settings.storeName} makes hanging bird houses and nesting shelters
                from coir, the fibre drawn from coconut husk. Each piece is wound by
                hand over a form and finished individually, which is why the texture
                and shape vary from one to the next.
              </Typography>
              <Typography>
                Some pieces use dyed fibre for the roof, and others are left in the
                natural colour of the husk. Cord or rope is fitted for hanging. Where
                a piece uses another material, it is listed on that product page
                rather than described in general terms here.
              </Typography>
            </>
          )}
        </Stack>

        {whyHandmade && (
          <Stack spacing={2}>
            <Typography variant="h2" sx={{ fontSize: { xs: '1.5rem', md: '1.9rem' } }}>
              Why handmade
            </Typography>
            {paragraphs(settings.whyHandmadeBody).map((text, index) => (
              <Typography key={index}>{text}</Typography>
            ))}
          </Stack>
        )}

        {materials && (
          <Stack spacing={2}>
            <Typography variant="h2" sx={{ fontSize: { xs: '1.5rem', md: '1.9rem' } }}>
              Natural materials
            </Typography>
            {paragraphs(settings.materialsBody).map((text, index) => (
              <Typography key={index}>{text}</Typography>
            ))}
          </Stack>
        )}
      </Stack>
    </Container>
  );
}

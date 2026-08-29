import { useQuery } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Skeleton from '@mui/material/Skeleton';
import Divider from '@mui/material/Divider';
import { Link as RouterLink } from 'react-router-dom';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';

import { catalogKeys, searchProducts, getCategories } from '../api/catalog';
import { ProductGrid } from '../components/catalog/ProductGrid';
import { ProductImage } from '../components/common/ProductImage';
import { SEOHead } from '../components/common/SEOHead';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { whatsAppLink } from '../lib/format';
import { palette } from '../theme';

const FEATURED_QUERY = { featured: true, size: 8, sort: 'RELEVANCE' } as const;

/**
 * Home page.
 *
 * <p>The hero photograph is the first featured product rather than a
 * hard-coded file, so changing what leads the site is an admin action. Its
 * image is the page's largest contentful paint, so it loads eagerly at high
 * priority while everything below stays lazy.
 */
export function Landing() {
  const settings = useSiteSettings();
  const featured = useQuery({
    queryKey: catalogKeys.products(FEATURED_QUERY),
    queryFn: () => searchProducts(FEATURED_QUERY),
  });

  const categories = useQuery({
    queryKey: catalogKeys.categories,
    queryFn: getCategories,
  });

  const products = featured.data?.content ?? [];
  const hero = products[0];

  return (
    <>
      <SEOHead
        title={settings.storeName}
        description="Handwoven coir bird houses and nesting shelters, each piece shaped by hand from natural coconut fibre."
        path="/"
        imageKey={hero?.image?.key ?? null}
      />

      {/* ---------------- hero ---------------- */}
      <Box sx={{ bgcolor: 'background.default', overflow: 'hidden' }}>
        <Container maxWidth="lg" sx={{ py: { xs: 5, md: 9 } }}>
          <Grid container spacing={{ xs: 4, md: 8 }} sx={{ alignItems: 'center' }}>
            <Grid size={{ xs: 12, md: 6 }}>
              <Stack spacing={3}>
                <Typography variant="overline" color="primary">
                  Handmade natural fibre
                </Typography>

                <Typography variant="h1" sx={{ fontSize: { xs: '2.5rem', sm: '3.2rem', md: '3.9rem' } }}>
                  Handwoven coir bird houses
                </Typography>

                <Typography sx={{ fontSize: { xs: '1.05rem', md: '1.15rem' }, maxWidth: '46ch' }}>
                  Each piece is wound by hand from natural coconut fibre, shaped
                  over a form, and finished with a cord to hang it by. No two
                  come out quite the same.
                </Typography>

                <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ pt: 1 }}>
                  <Button
                    component={RouterLink}
                    to="/shop"
                    variant="contained"
                    size="large"
                    sx={{ px: 3.5 }}
                  >
                    Shop collection
                  </Button>

                  {settings.whatsAppEnabled && (
                    <Button
                      component="a"
                      href={whatsAppLink(
                        settings.whatsAppNumber,
                        `Hello ${settings.storeName}, I would like to know more about what you sell.`,
                      )}
                      target="_blank"
                      rel="noopener noreferrer"
                      variant="outlined"
                      size="large"
                      startIcon={<WhatsAppIcon />}
                    >
                      Chat on WhatsApp
                    </Button>
                  )}
                </Stack>
              </Stack>
            </Grid>

            <Grid size={{ xs: 12, md: 6 }}>
              <Box
                sx={{
                  borderRadius: 4,
                  overflow: 'hidden',
                  border: 1,
                  borderColor: 'divider',
                  boxShadow: '0 24px 60px -34px rgba(51,50,46,0.5)',
                }}
              >
                {featured.isPending ? (
                  <Skeleton variant="rectangular" sx={{ aspectRatio: '4 / 5', width: '100%', height: 'auto' }} />
                ) : (
                  <ProductImage
                    image={hero?.image ?? null}
                    aspectRatio="4 / 5"
                    priority
                    sizes="(max-width: 900px) 100vw, 560px"
                  />
                )}
              </Box>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* ---------------- featured ---------------- */}
      <Container maxWidth="lg" sx={{ py: { xs: 6, md: 9 } }}>
        <Stack
          direction="row"
          sx={{ justifyContent: 'space-between', alignItems: 'baseline', mb: 3.5 }}
        >
          <Box>
            <Typography variant="overline" color="text.secondary">
              Selected items
            </Typography>
            <Typography variant="h2" sx={{ fontSize: { xs: '1.9rem', md: '2.4rem' } }}>
              Featured
            </Typography>
          </Box>
          <Button component={RouterLink} to="/shop">
            View all
          </Button>
        </Stack>

        <ProductGrid
          products={products}
          loading={featured.isPending}
          skeletonCount={4}
          priorityCount={0}
        />
      </Container>

      {/* ---------------- categories ---------------- */}
      {(categories.data?.length ?? 0) > 0 && (
        <Container maxWidth="lg" sx={{ pb: { xs: 6, md: 9 } }}>
          <Typography variant="overline" color="text.secondary">
            Browse
          </Typography>
          <Typography variant="h2" sx={{ fontSize: { xs: '1.9rem', md: '2.4rem' }, mb: 3 }}>
            Categories
          </Typography>

          <Grid container spacing={{ xs: 2, md: 3 }}>
            {categories.data?.map((category) => (
              <Grid key={category.id} size={{ xs: 12, sm: 6, md: 4 }}>
                <Box
                  component={RouterLink}
                  to={`/shop/${category.slug}`}
                  sx={{
                    display: 'block',
                    p: 3,
                    height: '100%',
                    borderRadius: 3,
                    border: 1,
                    borderColor: 'divider',
                    bgcolor: 'background.paper',
                    textDecoration: 'none',
                    color: 'text.primary',
                    transition: 'border-color 180ms ease, transform 180ms ease',
                    '&:hover': { borderColor: 'primary.main', transform: 'translateY(-2px)' },
                  }}
                >
                  <Typography variant="h6" component="h3">
                    {category.name}
                  </Typography>
                  {category.description && (
                    <Typography variant="body2" sx={{ mt: 1 }}>
                      {category.description}
                    </Typography>
                  )}
                  <Typography variant="body2" sx={{ mt: 1.5, color: 'primary.main', fontWeight: 600 }}>
                    {category.productCount} {category.productCount === 1 ? 'item' : 'items'}
                  </Typography>
                </Box>
              </Grid>
            ))}
          </Grid>
        </Container>
      )}

      {/* ---------------- why handmade ---------------- */}
      <Box sx={{ bgcolor: 'background.paper', borderTop: 1, borderBottom: 1, borderColor: 'divider' }}>
        <Container maxWidth="lg" sx={{ py: { xs: 6, md: 9 } }}>
          <Grid container spacing={{ xs: 4, md: 8 }}>
            <Grid size={{ xs: 12, md: 5 }}>
              <Typography variant="overline" color="text.secondary">
                Why handmade
              </Typography>
              <Typography variant="h2" sx={{ fontSize: { xs: '1.9rem', md: '2.4rem' }, mt: 0.5 }}>
                Made one at a time
              </Typography>
            </Grid>

            <Grid size={{ xs: 12, md: 7 }}>
              <Stack spacing={3} divider={<Divider flexItem />}>
                <Box>
                  <Typography variant="h6" component="h3" sx={{ mb: 0.75 }}>
                    Wound by hand
                  </Typography>
                  <Typography variant="body2">
                    Every piece is formed from coir fibre wound over a shape,
                    which is why the surface texture differs from one to the next.
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h6" component="h3" sx={{ mb: 0.75 }}>
                    Finished individually
                  </Typography>
                  <Typography variant="body2">
                    Roofs, entrances and hanging cords are fitted piece by piece.
                    Small variations in shape and colour are part of the making.
                  </Typography>
                </Box>
                <Box>
                  <Typography variant="h6" component="h3" sx={{ mb: 0.75 }}>
                    Photographed as sold
                  </Typography>
                  <Typography variant="body2">
                    The photographs on this site are of the pieces themselves,
                    not stock imagery.
                  </Typography>
                </Box>
              </Stack>
            </Grid>
          </Grid>
        </Container>
      </Box>

      {/* ---------------- materials ---------------- */}
      <Container maxWidth="lg" sx={{ py: { xs: 6, md: 9 } }}>
        <Grid container spacing={{ xs: 4, md: 8 }} sx={{ alignItems: 'center' }}>
          <Grid size={{ xs: 12, md: 6 }}>
            <Typography variant="overline" color="text.secondary">
              Materials
            </Typography>
            <Typography variant="h2" sx={{ fontSize: { xs: '1.9rem', md: '2.4rem' }, mt: 0.5, mb: 2 }}>
              Coir, and little else
            </Typography>
            <Typography sx={{ maxWidth: '52ch' }}>
              Coir is the fibre from the husk of a coconut. These pieces are made
              from it, with dyed fibre used for some of the roofs and cord or
              rope for hanging. Where a piece uses another material, it is listed
              on the product page.
            </Typography>
          </Grid>

          <Grid size={{ xs: 12, md: 6 }}>
            <Box
              sx={{
                borderRadius: 4,
                overflow: 'hidden',
                border: 1,
                borderColor: 'divider',
                background: `linear-gradient(135deg, ${palette.coirLight}, ${palette.coconut})`,
              }}
            >
              {products[1]?.image ? (
                <ProductImage
                  image={products[1].image}
                  aspectRatio="16 / 11"
                  sizes="(max-width: 900px) 100vw, 560px"
                />
              ) : (
                <Box sx={{ aspectRatio: '16 / 11' }} />
              )}
            </Box>
          </Grid>
        </Grid>
      </Container>
    </>
  );
}

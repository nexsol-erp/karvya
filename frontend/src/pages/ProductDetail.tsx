import { useState } from 'react';
import { useParams, Link as RouterLink } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import ButtonBase from '@mui/material/ButtonBase';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Breadcrumbs from '@mui/material/Breadcrumbs';
import Link from '@mui/material/Link';
import IconButton from '@mui/material/IconButton';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';

import { catalogKeys, getProduct, getRelatedProducts } from '../api/catalog';
import { ProductImage } from '../components/common/ProductImage';
import { ProductGrid } from '../components/catalog/ProductGrid';
import { StockBadge } from '../components/common/StockBadge';
import { SEOHead } from '../components/common/SEOHead';
import { NotFound } from './NotFound';
import { ApiError } from '../api/client';
import type { ProductDetail as ProductDetailType } from '../api/types';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { useCart } from '../hooks/useCart';
import { formatMoney, whatsAppLink } from '../lib/format';

/** Attribute rows are omitted entirely when the value is missing. */
/** A value by its label, case-insensitively, for the structured data. */
function attributeNamed(product: ProductDetailType, label: string): string | undefined {
  return product.attributes.find((a) => a.label.toLowerCase() === label)?.value;
}

function Attribute({ label, value }: { label: string; value: string | null }) {
  if (!value) return null;
  return (
    <Stack direction="row" spacing={2} sx={{ py: 1.25 }}>
      <Typography variant="body2" sx={{ minWidth: 132, color: 'text.secondary', fontWeight: 600 }}>
        {label}
      </Typography>
      <Typography variant="body2" sx={{ color: 'text.primary' }}>
        {value}
      </Typography>
    </Stack>
  );
}

export function ProductDetail() {
  const settings = useSiteSettings();
  const { slug = '' } = useParams();
  const [selected, setSelected] = useState(0);
  const [quantity, setQuantity] = useState(1);
  const [adding, setAdding] = useState(false);
  const [added, setAdded] = useState(false);
  const { addToCart, quantityOf } = useCart();

  const product = useQuery({
    queryKey: catalogKeys.product(slug),
    queryFn: () => getProduct(slug),
    retry: (count, error) => !(error instanceof ApiError && error.isNotFound) && count < 2,
  });

  const related = useQuery({
    queryKey: catalogKeys.related(slug),
    queryFn: () => getRelatedProducts(slug),
    enabled: product.isSuccess,
  });

  if (product.error instanceof ApiError && product.error.isNotFound) {
    return <NotFound />;
  }

  if (product.isPending) {
    return (
      <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
        <Grid container spacing={{ xs: 4, md: 7 }}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Skeleton variant="rectangular" sx={{ aspectRatio: '4 / 5', width: '100%', height: 'auto', borderRadius: 3 }} />
          </Grid>
          <Grid size={{ xs: 12, md: 5 }}>
            <Skeleton height={52} width="80%" />
            <Skeleton height={36} width="40%" sx={{ mt: 2 }} />
            <Skeleton height={20} sx={{ mt: 3 }} />
            <Skeleton height={20} width="90%" />
            <Skeleton height={20} width="75%" />
          </Grid>
        </Grid>
      </Container>
    );
  }

  if (product.isError) {
    return (
      <Container maxWidth="lg" sx={{ py: 8 }}>
        <Alert severity="error">
          This item could not be loaded. Please refresh to try again.
        </Alert>
      </Container>
    );
  }

  const p = product.data;
  const images = p.images.length > 0 ? p.images : [null];
  const active = images[Math.min(selected, images.length - 1)];
  const canonicalUrl = `${window.location.origin}/product/${p.slug}`;

  // the cart already holds everything the catalogue has, so adding more would
  // only be capped server-side - say so instead
  const alreadyAtStockLimit = quantityOf(p.id) >= p.stockQuantity;

  const enquiry = [
    `Hello ${settings.storeName}, I would like to ask about this item:`,
    '',
    p.name,
    canonicalUrl,
  ].join('\n');

  // Structured data describes only what the catalogue actually holds.
  const structuredData = {
    '@context': 'https://schema.org',
    '@type': 'Product',
    name: p.name,
    sku: p.sku,
    description: p.shortDescription ?? p.description ?? undefined,
    image: p.images.map((i) => `${window.location.origin}/media/${i.key}-1280.jpg`),
    // schema.org names these; whether this product has them is now up to
    // whoever defined its attributes, so they are looked up rather than assumed
    material: attributeNamed(p, 'material'),
    color: attributeNamed(p, 'colour') ?? attributeNamed(p, 'color'),
    category: p.categoryName,
    offers: {
      '@type': 'Offer',
      price: String(p.price),
      priceCurrency: settings.currency,
      availability: p.inStock
        ? 'https://schema.org/InStock'
        : 'https://schema.org/OutOfStock',
      url: canonicalUrl,
    },
  };

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 3, md: 6 } }}>
      <SEOHead
        title={p.name}
        description={p.shortDescription ?? undefined}
        path={`/product/${p.slug}`}
        imageKey={p.images[0]?.key ?? null}
        structuredData={structuredData}
      />

      <Breadcrumbs sx={{ mb: 3 }}>
        <Link component={RouterLink} to="/shop" color="text.secondary">
          Shop
        </Link>
        <Link component={RouterLink} to={`/shop/${p.categorySlug}`} color="text.secondary">
          {p.categoryName}
        </Link>
        <Typography color="text.primary" variant="body2">
          {p.name}
        </Typography>
      </Breadcrumbs>

      <Grid container spacing={{ xs: 4, md: 7 }}>
        {/* ---------------- gallery ---------------- */}
        <Grid size={{ xs: 12, md: 7 }}>
          <Box
            sx={{
              borderRadius: 3,
              overflow: 'hidden',
              border: 1,
              borderColor: 'divider',
              bgcolor: 'background.paper',
            }}
          >
            <ProductImage
              image={active}
              aspectRatio="4 / 5"
              priority
              sizes="(max-width: 900px) 100vw, 660px"
            />
          </Box>

          {images.length > 1 && (
            <Stack direction="row" spacing={1.5} sx={{ mt: 1.5 }}>
              {p.images.map((image, index) => (
                <ButtonBase
                  key={image.key}
                  onClick={() => setSelected(index)}
                  aria-label={`View image ${index + 1} of ${p.images.length}`}
                  aria-current={index === selected}
                  sx={{
                    width: 88,
                    borderRadius: 2,
                    overflow: 'hidden',
                    border: 2,
                    borderColor: index === selected ? 'primary.main' : 'divider',
                  }}
                >
                  <ProductImage image={image} aspectRatio="1 / 1" sizes="88px" />
                </ButtonBase>
              ))}
            </Stack>
          )}
        </Grid>

        {/* ---------------- details ---------------- */}
        <Grid size={{ xs: 12, md: 5 }}>
          <Stack spacing={2.5}>
            <Box>
              <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.4rem' }, mb: 1.5 }}>
                {p.name}
              </Typography>
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Typography sx={{ fontSize: '1.6rem', fontWeight: 700 }}>
                  {formatMoney(p.price, settings.currency, settings.locale)}
                </Typography>
                <StockBadge inStock={p.inStock} quantity={p.stockQuantity} size="medium" />
              </Stack>
            </Box>

            {p.placeholderContent && (
              <Alert severity="info" variant="outlined">
                This listing still carries placeholder text. Prices and details
                are provisional until reviewed.
              </Alert>
            )}

            {p.description && <Typography>{p.description}</Typography>}

            <Divider />

            <Box>
              {/* The category decides what this field is called, and whether
                  it exists: "Author" for a book, "Artist" for a record, absent
                  for something nobody wrote. */}
              {p.authorLabel && <Attribute label={p.authorLabel} value={p.author} />}

              {/* Everything else the administrator defined for this kind of
                  product, already in the order they chose. */}
              {p.attributes.map((attribute) => (
                <Attribute key={attribute.label} label={attribute.label} value={attribute.value} />
              ))}
              <Attribute label="SKU" value={p.sku} />
            </Box>

            <Divider />

            {p.inStock && (
              <Stack direction="row" spacing={2} sx={{ alignItems: 'center' }}>
                <Typography variant="body2" sx={{ fontWeight: 600 }}>
                  Quantity
                </Typography>
                <Stack
                  direction="row"
                  sx={{ alignItems: 'center', border: 1, borderColor: 'divider', borderRadius: 999 }}
                >
                  <IconButton
                    size="small"
                    onClick={() => setQuantity((n) => Math.max(1, n - 1))}
                    disabled={quantity <= 1}
                    aria-label="Decrease quantity"
                  >
                    <RemoveIcon fontSize="small" />
                  </IconButton>
                  <Typography sx={{ minWidth: 34, textAlign: 'center', fontWeight: 600 }} aria-live="polite">
                    {quantity}
                  </Typography>
                  <IconButton
                    size="small"
                    onClick={() => setQuantity((n) => Math.min(p.stockQuantity, n + 1))}
                    disabled={quantity >= p.stockQuantity}
                    aria-label="Increase quantity"
                  >
                    <AddIcon fontSize="small" />
                  </IconButton>
                </Stack>
              </Stack>
            )}

            {p.inStock && (
              <Stack spacing={1}>
                <Button
                  variant="contained"
                  size="large"
                  fullWidth
                  disabled={adding || alreadyAtStockLimit}
                  startIcon={<ShoppingBagOutlinedIcon />}
                  onClick={async () => {
                    setAdding(true);
                    try {
                      await addToCart(p.id, quantity);
                      setAdded(true);
                    } finally {
                      setAdding(false);
                    }
                  }}
                >
                  {adding ? 'Adding…' : 'Add to cart'}
                </Button>

                {/* the note explains why the button is disabled, but it must
                    not replace the confirmation of an add just made - adding
                    the last unit is exactly when both conditions are true */}
                {alreadyAtStockLimit && !added && (
                  <Typography variant="body2" color="text.secondary">
                    All {p.stockQuantity} in stock are already in your cart.
                  </Typography>
                )}

                {added && (
                  <Alert severity="success" onClose={() => setAdded(false)}>
                    Added to your cart.{' '}
                    <Link component={RouterLink} to="/cart">
                      View cart
                    </Link>
                  </Alert>
                )}
              </Stack>
            )}

            {settings.whatsAppEnabled && (
              <Button
                component="a"
                href={whatsAppLink(settings.whatsAppNumber, enquiry)}
                target="_blank"
                rel="noopener noreferrer"
                variant="outlined"
                size="large"
                fullWidth
                startIcon={<WhatsAppIcon />}
              >
                Ask about this item on WhatsApp
              </Button>
            )}
          </Stack>
        </Grid>
      </Grid>

      {/* ---------------- related ---------------- */}
      {(related.data?.length ?? 0) > 0 && (
        <Box sx={{ mt: { xs: 7, md: 10 } }}>
          <Typography variant="h2" sx={{ fontSize: { xs: '1.7rem', md: '2.1rem' }, mb: 3 }}>
            You may also like
          </Typography>
          <ProductGrid products={related.data ?? []} priorityCount={0} />
        </Box>
      )}
    </Container>
  );
}

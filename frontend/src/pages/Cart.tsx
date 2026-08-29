import { useState } from 'react';
import { Link as RouterLink } from 'react-router-dom';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Skeleton from '@mui/material/Skeleton';
import Link from '@mui/material/Link';
import AddIcon from '@mui/icons-material/Add';
import RemoveIcon from '@mui/icons-material/Remove';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlineOutlined';

import { SEOHead } from '../components/common/SEOHead';
import { EmptyState } from '../components/common/EmptyState';
import { ProductImage } from '../components/common/ProductImage';
import { useCart } from '../hooks/useCart';
import { formatMoney } from '../lib/format';
import { useSiteSettings } from '../hooks/useSiteSettings';

export function Cart() {
  const settings = useSiteSettings();
  const { cart, isLoading, setQuantity, removeItem, dismissAdjustments } = useCart();
  const [busyProduct, setBusyProduct] = useState<number | null>(null);

  const money = (value: string | number) => formatMoney(value, cart.currency, settings.locale);

  async function change(productId: number, quantity: number) {
    setBusyProduct(productId);
    try {
      await setQuantity(productId, quantity);
    } finally {
      setBusyProduct(null);
    }
  }

  if (isLoading) {
    return (
      <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
        <Skeleton height={52} width={220} />
        <Skeleton height={120} sx={{ mt: 3 }} />
        <Skeleton height={120} />
      </Container>
    );
  }

  if (cart.lines.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: { xs: 6, md: 10 } }}>
        <SEOHead title="Your cart" path="/cart" noIndex />
        <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.6rem' }, mb: 4 }}>
          Your cart
        </Typography>

        {cart.adjustments.length > 0 && (
          <Alert severity="info" onClose={dismissAdjustments} sx={{ mb: 3 }}>
            <AlertTitle>Your cart changed</AlertTitle>
            {cart.adjustments.map((a) => (
              <div key={`${a.productId}-${a.kind}`}>{a.message}</div>
            ))}
          </Alert>
        )}

        <EmptyState
          title="Your cart is empty"
          description="Pieces you add will appear here, ready for checkout."
        />
        <Stack sx={{ alignItems: 'center', mt: 3 }}>
          <Button component={RouterLink} to="/shop" variant="contained" size="large">
            Browse the shop
          </Button>
        </Stack>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
      <SEOHead title="Your cart" path="/cart" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.6rem' }, mb: 1 }}>
        Your cart
      </Typography>
      <Typography variant="body2" sx={{ mb: 4 }}>
        {cart.itemCount} {cart.itemCount === 1 ? 'item' : 'items'}
      </Typography>

      {cart.adjustments.length > 0 && (
        <Alert severity="warning" onClose={dismissAdjustments} sx={{ mb: 3 }}>
          <AlertTitle>Your cart changed</AlertTitle>
          {cart.adjustments.map((a) => (
            <div key={`${a.productId}-${a.kind}`}>{a.message}</div>
          ))}
        </Alert>
      )}

      <Grid container spacing={{ xs: 3, md: 4 }}>
        <Grid size={{ xs: 12, md: 8 }}>
          <Stack spacing={2}>
            {cart.lines.map((line) => {
              const busy = busyProduct === line.productId;
              return (
                <Card key={line.productId} sx={{ p: 2 }}>
                  <Stack direction="row" spacing={2}>
                    <Box
                      component={RouterLink}
                      to={`/product/${line.slug}`}
                      sx={{ width: { xs: 88, sm: 116 }, flexShrink: 0, borderRadius: 2, overflow: 'hidden' }}
                    >
                      <ProductImage image={line.image} aspectRatio="1 / 1" sizes="116px" />
                    </Box>

                    <Stack sx={{ flexGrow: 1, minWidth: 0 }} spacing={0.5}>
                      <Link
                        component={RouterLink}
                        to={`/product/${line.slug}`}
                        color="text.primary"
                        sx={{ fontWeight: 700 }}
                      >
                        {line.name}
                      </Link>
                      <Typography variant="body2">{money(line.unitPrice)} each</Typography>

                      <Box sx={{ flexGrow: 1 }} />

                      <Stack
                        direction="row"
                        spacing={1}
                        sx={{ alignItems: 'center', flexWrap: 'wrap', mt: 1 }}
                      >
                        <Stack
                          direction="row"
                          sx={{ alignItems: 'center', border: 1, borderColor: 'divider', borderRadius: 999 }}
                        >
                          <IconButton
                            size="small"
                            aria-label={`Reduce quantity of ${line.name}`}
                            disabled={busy || line.quantity <= 1}
                            onClick={() => change(line.productId, line.quantity - 1)}
                          >
                            <RemoveIcon fontSize="small" />
                          </IconButton>
                          <Typography sx={{ minWidth: 32, textAlign: 'center', fontWeight: 600 }}>
                            {line.quantity}
                          </Typography>
                          <IconButton
                            size="small"
                            aria-label={`Increase quantity of ${line.name}`}
                            disabled={busy || line.quantity >= line.availableStock}
                            onClick={() => change(line.productId, line.quantity + 1)}
                          >
                            <AddIcon fontSize="small" />
                          </IconButton>
                        </Stack>

                        {line.quantity >= line.availableStock && (
                          <Typography variant="body2" color="text.disabled">
                            All {line.availableStock} in stock
                          </Typography>
                        )}

                        <Box sx={{ flexGrow: 1 }} />

                        <Typography sx={{ fontWeight: 700 }}>{money(line.lineTotal)}</Typography>

                        <IconButton
                          aria-label={`Remove ${line.name} from your cart`}
                          disabled={busy}
                          onClick={() => removeItem(line.productId)}
                        >
                          <DeleteOutlineIcon fontSize="small" />
                        </IconButton>
                      </Stack>
                    </Stack>
                  </Stack>
                </Card>
              );
            })}
          </Stack>

          <Button component={RouterLink} to="/shop" sx={{ mt: 2 }}>
            Continue shopping
          </Button>
        </Grid>

        {/* ---------------- summary ---------------- */}
        <Grid size={{ xs: 12, md: 4 }}>
          <Card sx={{ p: 3, position: { md: 'sticky' }, top: { md: 96 } }}>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
              Order summary
            </Typography>

            <Stack spacing={1.5}>
              <Row label="Subtotal" value={money(cart.subtotal)} />
              <Row
                label="Delivery"
                value={
                  Number(cart.deliveryCharge) === 0 ? 'Free' : money(cart.deliveryCharge)
                }
              />

              {cart.amountToFreeDelivery != null && (
                <Alert severity="info" sx={{ py: 0.5 }}>
                  Add {money(cart.amountToFreeDelivery)} more for free delivery.
                </Alert>
              )}

              <Divider />

              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography sx={{ fontWeight: 700 }}>Total</Typography>
                <Typography sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                  {money(cart.total)}
                </Typography>
              </Stack>

              <Button
                component={RouterLink}
                to="/checkout"
                variant="contained"
                size="large"
                fullWidth
                sx={{ mt: 1 }}
              >
                Proceed to checkout
              </Button>
            </Stack>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

function Row({ label, value }: { label: string; value: string }) {
  return (
    <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
      <Typography variant="body2">{label}</Typography>
      <Typography variant="body2" sx={{ fontWeight: 600, color: 'text.primary' }}>
        {value}
      </Typography>
    </Stack>
  );
}

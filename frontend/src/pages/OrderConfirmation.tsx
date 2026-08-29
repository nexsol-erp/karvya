import { useEffect } from 'react';
import { Link as RouterLink, useParams, useSearchParams } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Chip from '@mui/material/Chip';
import Skeleton from '@mui/material/Skeleton';
import CheckCircleOutlineIcon from '@mui/icons-material/CheckCircleOutlineOutlined';
import WhatsAppIcon from '@mui/icons-material/WhatsApp';

import { SEOHead } from '../components/common/SEOHead';
import { ProductImage } from '../components/common/ProductImage';
import { NotFound } from './NotFound';
import { ApiError } from '../api/client';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../hooks/useAuth';
import { formatMoney, whatsAppLink } from '../lib/format';
import { useSiteSettings } from '../hooks/useSiteSettings';
import {
  ORDER_STATUS_LABELS,
  PAYMENT_STATUS_LABELS,
  getMyOrder,
  getOrderByToken,
  orderKeys,
} from '../api/orders';

/**
 * The order confirmation.
 *
 * <p>Reached two ways: a guest arrives with the opaque token issued at
 * checkout, while a signed-in customer is matched on their account. Both land
 * on this page, so there is one thing to maintain.
 */
export function OrderConfirmation() {
  const settings = useSiteSettings();
  const { orderNumber = '' } = useParams();
  const [params] = useSearchParams();
  const token = params.get('token');
  const { isSignedIn } = useAuth();
  const { clear } = useCart();

  const order = useQuery({
    queryKey: orderKeys.confirmation(orderNumber, token),
    queryFn: () =>
      token ? getOrderByToken(orderNumber, token) : getMyOrder(orderNumber),
    retry: (count, error) => !(error instanceof ApiError && error.isNotFound) && count < 2,
  });

  /**
   * Empties the cart only once the order is confirmed to exist. Clearing on
   * submit would lose someone's cart if the request failed on the way back.
   */
  useEffect(() => {
    if (order.isSuccess) {
      void clear();
    }
    // deliberately keyed on the order number: re-clearing on every render of
    // the same confirmation would fight the user if they add something new
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [order.isSuccess, orderNumber]);

  // 401 and 404 are shown identically. Without a token an anonymous visitor
  // falls through to the account endpoint and is refused, and distinguishing
  // "not yours" from "does not exist" would confirm the order is real to
  // anyone guessing order numbers.
  if (order.error instanceof ApiError
      && (order.error.isNotFound || order.error.status === 401)) {
    return <NotFound />;
  }

  if (order.isPending) {
    return (
      <Container maxWidth="md" sx={{ py: { xs: 5, md: 8 } }}>
        <Skeleton height={64} width="60%" />
        <Skeleton height={200} sx={{ mt: 3 }} />
      </Container>
    );
  }

  if (order.isError) {
    return (
      <Container maxWidth="md" sx={{ py: 8 }}>
        <Alert severity="error">
          This order could not be loaded. Please refresh, or contact us with your
          order number.
        </Alert>
      </Container>
    );
  }

  const o = order.data;
  const money = (value: string | number) => formatMoney(value, o.currency, settings.locale);

  const whatsAppSummary = [
    `Hello ${settings.storeName}, about my order ${o.orderNumber}:`,
    '',
    ...o.lines.map((line) => `- ${line.productName} x${line.quantity}`),
    '',
    `Total: ${money(o.total)}`,
    `Payment: ${o.paymentMethodLabel}`,
  ].join('\n');

  return (
    <Container maxWidth="md" sx={{ py: { xs: 4, md: 7 } }}>
      <SEOHead title={`Order ${o.orderNumber}`} path={`/order/${o.orderNumber}`} noIndex />

      <Stack spacing={1.5} sx={{ alignItems: 'center', textAlign: 'center', mb: 4 }}>
        <CheckCircleOutlineIcon sx={{ fontSize: 56, color: 'success.main' }} />
        <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.6rem' } }}>
          Thank you
        </Typography>
        <Typography variant="body1">
          Your order number is{' '}
          <Box component="strong" sx={{ fontFamily: 'monospace', fontSize: '1.1em' }}>
            {o.orderNumber}
          </Box>
        </Typography>
        <Stack direction="row" spacing={1} sx={{ mt: 1 }}>
          <Chip label={ORDER_STATUS_LABELS[o.status]} color="primary" />
          <Chip label={PAYMENT_STATUS_LABELS[o.paymentStatus]} variant="outlined" />
        </Stack>
      </Stack>

      <Alert severity="info" sx={{ mb: 3 }}>
        <AlertTitle>What happens next</AlertTitle>
        {settings.checkoutNotice}
      </Alert>

      {o.paymentInstructions && (
        <Alert severity="success" variant="outlined" sx={{ mb: 3 }}>
          <AlertTitle>{o.paymentMethodLabel}</AlertTitle>
          {o.paymentInstructions}
        </Alert>
      )}

      {/* Stated plainly rather than implying an email was sent regardless. */}
      <Typography variant="body2" sx={{ mb: 3 }}>
        {o.confirmationEmailQueued
          ? `A confirmation is on its way to ${o.delivery.email}.`
          : 'No email address was given, so please keep your order number safe — it is how we will identify your order.'}
      </Typography>

      <Grid container spacing={3}>
        <Grid size={{ xs: 12, md: 7 }}>
          <Card sx={{ p: 3 }}>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
              What you ordered
            </Typography>

            <Stack spacing={2} divider={<Divider flexItem />}>
              {o.lines.map((line) => (
                <Stack key={line.productSku} direction="row" spacing={2}>
                  <Box sx={{ width: 64, flexShrink: 0, borderRadius: 1.5, overflow: 'hidden' }}>
                    <ProductImage
                      image={
                        line.imageKey
                          ? { key: line.imageKey, alt: line.productName, width: null, height: null }
                          : null
                      }
                      aspectRatio="1 / 1"
                      sizes="64px"
                    />
                  </Box>
                  <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                    <Typography sx={{ fontWeight: 600 }}>{line.productName}</Typography>
                    <Typography variant="body2">
                      {money(line.unitPrice)} × {line.quantity}
                    </Typography>
                  </Box>
                  <Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap' }}>
                    {money(line.lineTotal)}
                  </Typography>
                </Stack>
              ))}
            </Stack>

            <Divider sx={{ my: 2.5 }} />

            <Stack spacing={1}>
              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography variant="body2">Subtotal</Typography>
                <Typography variant="body2">{money(o.subtotal)}</Typography>
              </Stack>
              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography variant="body2">Delivery</Typography>
                <Typography variant="body2">
                  {Number(o.deliveryCharge) === 0 ? 'Free' : money(o.deliveryCharge)}
                </Typography>
              </Stack>
              <Divider />
              <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                <Typography sx={{ fontWeight: 700 }}>Total</Typography>
                <Typography sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                  {money(o.total)}
                </Typography>
              </Stack>
            </Stack>
          </Card>
        </Grid>

        <Grid size={{ xs: 12, md: 5 }}>
          <Card sx={{ p: 3 }}>
            <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
              Delivering to
            </Typography>
            <Stack spacing={0.5}>
              <Typography sx={{ fontWeight: 600 }}>{o.delivery.name}</Typography>
              <Typography variant="body2">{o.delivery.line1}</Typography>
              {o.delivery.line2 && <Typography variant="body2">{o.delivery.line2}</Typography>}
              <Typography variant="body2">
                {o.delivery.city}, {o.delivery.state} {o.delivery.postalCode}
              </Typography>
              <Typography variant="body2" sx={{ mt: 1 }}>
                {o.delivery.phone}
              </Typography>
              {o.delivery.notes && (
                <Typography variant="body2" sx={{ mt: 1, fontStyle: 'italic' }}>
                  {o.delivery.notes}
                </Typography>
              )}
            </Stack>

            <Divider sx={{ my: 2.5 }} />

            <Typography variant="body2" sx={{ mb: 2 }}>
              Paying by <strong>{o.paymentMethodLabel}</strong>
            </Typography>

            {settings.whatsAppEnabled && (
              <Button
                component="a"
                href={whatsAppLink(settings.whatsAppNumber, whatsAppSummary)}
                target="_blank"
                rel="noopener noreferrer"
                variant="outlined"
                fullWidth
                startIcon={<WhatsAppIcon />}
              >
                Send order details on WhatsApp
              </Button>
            )}

            <Stack spacing={1} sx={{ mt: 2 }}>
              {isSignedIn && (
                <Button component={RouterLink} to="/account" fullWidth>
                  View your orders
                </Button>
              )}
              <Button component={RouterLink} to="/shop" fullWidth>
                Continue shopping
              </Button>
            </Stack>
          </Card>
        </Grid>
      </Grid>
    </Container>
  );
}

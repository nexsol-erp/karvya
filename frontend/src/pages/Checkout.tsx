import { useEffect, useState } from 'react';
import { Link as RouterLink, useNavigate } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Container from '@mui/material/Container';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Radio from '@mui/material/Radio';
import RadioGroup from '@mui/material/RadioGroup';
import FormControlLabel from '@mui/material/FormControlLabel';
import FormControl from '@mui/material/FormControl';
import FormLabel from '@mui/material/FormLabel';
import MenuItem from '@mui/material/MenuItem';
import Link from '@mui/material/Link';

import { SEOHead } from '../components/common/SEOHead';
import { useCart } from '../hooks/useCart';
import { useAuth } from '../hooks/useAuth';
import { formatMoney } from '../lib/format';
import { useSiteSettings } from '../hooks/useSiteSettings';
import { ApiError } from '../api/client';
import { authKeys, listAddresses } from '../api/auth';
import { getPaymentMethods, orderKeys, placeOrder } from '../api/orders';
import type { CartAdjustment } from '../api/cart';

const BLANK_FORM = {
  deliveryName: '',
  deliveryPhone: '',
  deliveryEmail: '',
  addressLine1: '',
  addressLine2: '',
  city: '',
  state: '',
  postalCode: '',
  deliveryNotes: '',
  customerComments: '',
};

export function Checkout() {
  const settings = useSiteSettings();
  const queryClient = useQueryClient();
  const { cart, isLoading: cartLoading } = useCart();
  const { isSignedIn, user } = useAuth();
  const navigate = useNavigate();

  const [form, setForm] = useState(BLANK_FORM);
  const [savedAddressId, setSavedAddressId] = useState<string>('');
  const [paymentMethodCode, setPaymentMethodCode] = useState('');
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [conflicts, setConflicts] = useState<CartAdjustment[]>([]);
  const [submitting, setSubmitting] = useState(false);

  const methods = useQuery({ queryKey: orderKeys.paymentMethods, queryFn: getPaymentMethods });
  const addresses = useQuery({
    queryKey: authKeys.addresses,
    queryFn: listAddresses,
    enabled: isSignedIn,
  });

  // default to the first offered method rather than leaving nothing selected
  useEffect(() => {
    if (!paymentMethodCode && methods.data && methods.data.length > 0) {
      setPaymentMethodCode(methods.data[0].code);
    }
  }, [methods.data, paymentMethodCode]);

  // prefill from the account, and from a default saved address when there is one
  useEffect(() => {
    if (!isSignedIn || !user) return;
    setForm((prev) => ({
      ...prev,
      deliveryName: prev.deliveryName || user.fullName,
      deliveryEmail: prev.deliveryEmail || user.email,
      deliveryPhone: prev.deliveryPhone || (user.phone ?? ''),
    }));
  }, [isSignedIn, user]);

  useEffect(() => {
    if (!addresses.data || savedAddressId) return;
    const preferred = addresses.data.find((a) => a.isDefault);
    if (preferred) setSavedAddressId(String(preferred.id));
  }, [addresses.data, savedAddressId]);

  const set = (key: keyof typeof form) => (e: React.ChangeEvent<HTMLInputElement>) =>
    setForm((prev) => ({ ...prev, [key]: e.target.value }));

  const money = (value: string | number) => formatMoney(value, cart.currency, settings.locale);
  const usingSavedAddress = savedAddressId !== '';
  const selectedMethod = methods.data?.find((m) => m.code === paymentMethodCode);

  async function handleSubmit(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setFieldErrors({});
    setConflicts([]);
    setSubmitting(true);

    try {
      const placed = await placeOrder({
        items: cart.lines.map((line) => ({
          productId: line.productId,
          quantity: line.quantity,
        })),
        savedAddressId: usingSavedAddress ? Number(savedAddressId) : null,
        ...form,
        // a saved address supplies these, but the server still validates the
        // shape of what is sent, so send something valid either way
        deliveryEmail: form.deliveryEmail || undefined,
        paymentMethodCode,
      });

      // the server keeps this delivery address against the account, so the
      // cached list is now behind - without this the next checkout within the
      // stale window would still ask for an address already saved
      queryClient.invalidateQueries({ queryKey: authKeys.addresses });

      navigate(
        `/order/${placed.orderNumber}?token=${encodeURIComponent(placed.accessToken)}`,
        { replace: true },
      );
    } catch (err) {
      if (err instanceof ApiError) {
        const adjustments = (err.problem as { adjustments?: CartAdjustment[] } | null)?.adjustments;
        if (err.status === 409 && adjustments) {
          // the cart moved under them; keep everything they typed
          setConflicts(adjustments);
        } else if (Object.keys(err.fieldErrors).length > 0) {
          setFieldErrors(err.fieldErrors);
          setError('Please check the highlighted fields.');
        } else {
          setError(err.message);
        }
      } else {
        setError('We could not place your order just now. Please try again.');
      }
    } finally {
      setSubmitting(false);
    }
  }

  if (!cartLoading && cart.lines.length === 0) {
    return (
      <Container maxWidth="sm" sx={{ py: { xs: 6, md: 10 }, textAlign: 'center' }}>
        <SEOHead title="Checkout" path="/checkout" noIndex />
        <Typography variant="h1" sx={{ fontSize: '2rem', mb: 2 }}>
          Your cart is empty
        </Typography>
        <Typography variant="body2" sx={{ mb: 3 }}>
          Add an item before checking out.
        </Typography>
        <Button component={RouterLink} to="/shop" variant="contained" size="large">
          Browse the shop
        </Button>
      </Container>
    );
  }

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
      <SEOHead title="Checkout" path="/checkout" noIndex />

      <Typography variant="h1" sx={{ fontSize: { xs: '2rem', md: '2.6rem' }, mb: 4 }}>
        Checkout
      </Typography>

      {conflicts.length > 0 && (
        <Alert severity="warning" sx={{ mb: 3 }}>
          <AlertTitle>Your cart changed while you were checking out</AlertTitle>
          {conflicts.map((a) => (
            <div key={`${a.productId}-${a.kind}`}>{a.message}</div>
          ))}
          <Box sx={{ mt: 1 }}>
            <Link component={RouterLink} to="/cart">
              Review your cart
            </Link>{' '}
            and try again. Nothing has been charged or ordered.
          </Box>
        </Alert>
      )}

      {error && (
        <Alert severity="error" sx={{ mb: 3 }}>
          {error}
        </Alert>
      )}

      <Box component="form" onSubmit={handleSubmit} noValidate>
        <Grid container spacing={{ xs: 3, md: 4 }}>
          <Grid size={{ xs: 12, md: 7 }}>
            <Stack spacing={3}>
              {/* ---- delivery ---- */}
              <Card sx={{ p: 3 }}>
                <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
                  Delivery details
                </Typography>

                {isSignedIn && (addresses.data?.length ?? 0) > 0 && (
                  <TextField
                    select
                    fullWidth
                    label="Deliver to"
                    value={savedAddressId}
                    onChange={(e) => setSavedAddressId(e.target.value)}
                    sx={{ mb: 2.5 }}
                    helperText={
                      usingSavedAddress
                        ? 'Using a saved address'
                        : 'Enter a new address below'
                    }
                  >
                    <MenuItem value="">Enter a new address</MenuItem>
                    {addresses.data?.map((address) => (
                      <MenuItem key={address.id} value={String(address.id)}>
                        {address.label ?? address.recipientName} — {address.line1}, {address.city}
                      </MenuItem>
                    ))}
                  </TextField>
                )}

                <Stack spacing={2}>
                  <TextField
                    label="Full name"
                    value={form.deliveryName}
                    onChange={set('deliveryName')}
                    error={Boolean(fieldErrors.deliveryName)}
                    helperText={fieldErrors.deliveryName}
                    autoComplete="name"
                    required
                    fullWidth
                  />

                  <Grid container spacing={2}>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Mobile / WhatsApp number"
                        value={form.deliveryPhone}
                        onChange={set('deliveryPhone')}
                        error={Boolean(fieldErrors.deliveryPhone)}
                        helperText={fieldErrors.deliveryPhone ?? 'So we can reach you about delivery'}
                        autoComplete="tel"
                        required
                        fullWidth
                      />
                    </Grid>
                    <Grid size={{ xs: 12, sm: 6 }}>
                      <TextField
                        label="Email (optional)"
                        type="email"
                        value={form.deliveryEmail}
                        onChange={set('deliveryEmail')}
                        error={Boolean(fieldErrors.deliveryEmail)}
                        helperText={
                          fieldErrors.deliveryEmail ??
                          'Add one to receive a confirmation by email'
                        }
                        autoComplete="email"
                        fullWidth
                      />
                    </Grid>
                  </Grid>

                  {!usingSavedAddress && (
                    <>
                      <TextField
                        label="Address"
                        value={form.addressLine1}
                        onChange={set('addressLine1')}
                        error={Boolean(fieldErrors.addressLine1)}
                        helperText={fieldErrors.addressLine1}
                        autoComplete="address-line1"
                        required
                        fullWidth
                      />
                      <TextField
                        label="Apartment, landmark (optional)"
                        value={form.addressLine2}
                        onChange={set('addressLine2')}
                        autoComplete="address-line2"
                        fullWidth
                      />
                      <Grid container spacing={2}>
                        <Grid size={{ xs: 12, sm: 5 }}>
                          <TextField
                            label="City"
                            value={form.city}
                            onChange={set('city')}
                            error={Boolean(fieldErrors.city)}
                            helperText={fieldErrors.city}
                            autoComplete="address-level2"
                            required
                            fullWidth
                          />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 4 }}>
                          <TextField
                            label="State"
                            value={form.state}
                            onChange={set('state')}
                            error={Boolean(fieldErrors.state)}
                            helperText={fieldErrors.state}
                            autoComplete="address-level1"
                            required
                            fullWidth
                          />
                        </Grid>
                        <Grid size={{ xs: 12, sm: 3 }}>
                          <TextField
                            label="Postal code"
                            value={form.postalCode}
                            onChange={set('postalCode')}
                            error={Boolean(fieldErrors.postalCode)}
                            helperText={fieldErrors.postalCode}
                            autoComplete="postal-code"
                            required
                            fullWidth
                          />
                        </Grid>
                      </Grid>
                    </>
                  )}

                  <TextField
                    label="Delivery notes (optional)"
                    value={form.deliveryNotes}
                    onChange={set('deliveryNotes')}
                    multiline
                    minRows={2}
                    fullWidth
                  />
                </Stack>
              </Card>

              {/* ---- payment ---- */}
              <Card sx={{ p: 3 }}>
                <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
                  How would you like to pay?
                </Typography>

                {methods.isPending ? (
                  <Typography variant="body2">Loading payment options…</Typography>
                ) : (methods.data?.length ?? 0) === 0 ? (
                  <Alert severity="warning">
                    No payment methods are configured yet, so orders cannot be placed.
                    An administrator can add one in settings.
                  </Alert>
                ) : (
                  <FormControl>
                    <FormLabel id="payment-method-label" sx={{ mb: 1 }}>
                      Payment method
                    </FormLabel>
                    <RadioGroup
                      aria-labelledby="payment-method-label"
                      value={paymentMethodCode}
                      onChange={(e) => setPaymentMethodCode(e.target.value)}
                    >
                      {methods.data?.map((method) => (
                        <FormControlLabel
                          key={method.code}
                          value={method.code}
                          control={<Radio />}
                          label={method.label}
                        />
                      ))}
                    </RadioGroup>
                  </FormControl>
                )}

                {selectedMethod?.instructions && (
                  <Alert severity="info" sx={{ mt: 2 }}>
                    {selectedMethod.instructions}
                  </Alert>
                )}

                <TextField
                  label="Anything else we should know? (optional)"
                  value={form.customerComments}
                  onChange={set('customerComments')}
                  multiline
                  minRows={2}
                  fullWidth
                  sx={{ mt: 2.5 }}
                />
              </Card>
            </Stack>
          </Grid>

          {/* ---- summary ---- */}
          <Grid size={{ xs: 12, md: 5 }}>
            <Card sx={{ p: 3, position: { md: 'sticky' }, top: { md: 96 } }}>
              <Typography variant="h6" component="h2" sx={{ mb: 2 }}>
                Your order
              </Typography>

              <Stack spacing={1.25} sx={{ mb: 2 }}>
                {cart.lines.map((line) => (
                  <Stack
                    key={line.productId}
                    direction="row"
                    spacing={2}
                    sx={{ justifyContent: 'space-between' }}
                  >
                    <Typography variant="body2">
                      {line.name} × {line.quantity}
                    </Typography>
                    <Typography variant="body2" sx={{ fontWeight: 600, whiteSpace: 'nowrap' }}>
                      {money(line.lineTotal)}
                    </Typography>
                  </Stack>
                ))}
              </Stack>

              <Divider sx={{ my: 2 }} />

              <Stack spacing={1}>
                <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                  <Typography variant="body2">Subtotal</Typography>
                  <Typography variant="body2">{money(cart.subtotal)}</Typography>
                </Stack>
                <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                  <Typography variant="body2">Delivery</Typography>
                  <Typography variant="body2">
                    {Number(cart.deliveryCharge) === 0 ? 'Free' : money(cart.deliveryCharge)}
                  </Typography>
                </Stack>
                <Divider />
                <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                  <Typography sx={{ fontWeight: 700 }}>Total</Typography>
                  <Typography sx={{ fontWeight: 700, fontSize: '1.15rem' }}>
                    {money(cart.total)}
                  </Typography>
                </Stack>
              </Stack>

              {/* required by the brief, and shown before the customer commits */}
              {/* administrator-editable; falls back to the standard wording */}
              <Alert severity="info" sx={{ mt: 2.5 }}>
                {settings.checkoutNotice}
              </Alert>

              <Button
                type="submit"
                variant="contained"
                size="large"
                fullWidth
                sx={{ mt: 2.5 }}
                disabled={submitting || cartLoading || !paymentMethodCode}
              >
                {submitting ? 'Placing your order…' : 'Place order'}
              </Button>

              <Button component={RouterLink} to="/cart" fullWidth sx={{ mt: 1 }}>
                Back to cart
              </Button>
            </Card>
          </Grid>
        </Grid>
      </Box>
    </Container>
  );
}

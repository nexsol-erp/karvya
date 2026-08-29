import { useState } from 'react';
import { Link as RouterLink, useParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Button from '@mui/material/Button';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Divider from '@mui/material/Divider';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import Link from '@mui/material/Link';
import Dialog from '@mui/material/Dialog';
import DialogTitle from '@mui/material/DialogTitle';
import DialogContent from '@mui/material/DialogContent';
import DialogActions from '@mui/material/DialogActions';
import FormControlLabel from '@mui/material/FormControlLabel';
import Checkbox from '@mui/material/Checkbox';
import ArrowBackIcon from '@mui/icons-material/ArrowBackIosNew';
import PrintIcon from '@mui/icons-material/PrintOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { ProductImage } from '../../components/common/ProductImage';
import { OrderStatusChip, PaymentStatusChip } from '../../components/admin/StatusChip';
import {
  addOrderNote, adminKeys, getOrder, recordPayment, setOrderStatus, setPaymentStatus,
} from '../../api/admin';
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from '../../api/orders';
import type { OrderStatus, PaymentStatus } from '../../api/orders';
import { ApiError } from '../../api/client';
import { formatMoney } from '../../lib/format';
import { config } from '../../config';

export function AdminOrderDetail() {
  const { orderNumber = '' } = useParams();
  const queryClient = useQueryClient();

  const [error, setError] = useState<string | null>(null);
  const [note, setNote] = useState('');
  const [statusNote, setStatusNote] = useState('');
  const [paymentOpen, setPaymentOpen] = useState(false);
  const [payment, setPayment] = useState({
    methodCode: '', reference: '', amount: '',
    receivedOn: new Date().toISOString().slice(0, 10),
    note: '', markAsPaid: true,
  });

  const order = useQuery({
    queryKey: adminKeys.order(orderNumber),
    queryFn: () => getOrder(orderNumber),
  });

  /** Every mutation lands on the same cache entry, so the page stays coherent. */
  const applyResult = (updated: Awaited<ReturnType<typeof getOrder>>) => {
    queryClient.setQueryData(adminKeys.order(orderNumber), updated);
    queryClient.invalidateQueries({ queryKey: ['admin', 'orders'] });
    queryClient.invalidateQueries({ queryKey: adminKeys.dashboard });
    setError(null);
  };

  const onError = (err: unknown) => {
    setError(err instanceof ApiError ? err.message : 'That change could not be saved.');
  };

  const statusMutation = useMutation({
    mutationFn: (next: OrderStatus) => setOrderStatus(orderNumber, next, statusNote || undefined),
    onSuccess: (updated) => { applyResult(updated); setStatusNote(''); },
    onError,
  });

  const paymentStatusMutation = useMutation({
    mutationFn: (next: PaymentStatus) => setPaymentStatus(orderNumber, next),
    onSuccess: applyResult,
    onError,
  });

  const noteMutation = useMutation({
    mutationFn: () => addOrderNote(orderNumber, note),
    onSuccess: (updated) => { applyResult(updated); setNote(''); },
    onError,
  });

  const paymentMutation = useMutation({
    mutationFn: () => recordPayment(orderNumber, {
      methodCode: payment.methodCode,
      reference: payment.reference || undefined,
      amount: payment.amount,
      receivedOn: payment.receivedOn,
      note: payment.note || undefined,
      markAsPaid: payment.markAsPaid,
    }),
    onSuccess: (updated) => { applyResult(updated); setPaymentOpen(false); },
    onError,
  });

  if (order.isPending) {
    return (
      <Box>
        <Skeleton height={48} width={280} />
        <Skeleton variant="rounded" height={320} sx={{ mt: 2 }} />
      </Box>
    );
  }

  if (order.isError) {
    return (
      <Alert severity="error">
        This order could not be loaded.{' '}
        <Link component={RouterLink} to="/admin/orders">Back to orders</Link>
      </Alert>
    );
  }

  const detail = order.data;
  const o = detail.order;
  const money = (value: string | number) => formatMoney(value, o.currency, config.locale);

  const openPaymentDialog = () => {
    setPayment((prev) => ({
      ...prev,
      methodCode: o.paymentMethodCode,
      // pre-filled with the amount owed, which is what is usually received
      amount: String(o.total),
    }));
    setPaymentOpen(true);
  };

  return (
    <Box>
      <SEOHead title={`Order ${o.orderNumber}`} path={`/admin/orders/${o.orderNumber}`} noIndex />

      <Button component={RouterLink} to="/admin/orders" startIcon={<ArrowBackIcon sx={{ fontSize: 13 }} />} size="small" sx={{ mb: 1 }}>
        Orders
      </Button>

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 2.5 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: '1.7rem', fontFamily: 'monospace' }}>
            {o.orderNumber}
          </Typography>
          <Stack direction="row" spacing={1} sx={{ mt: 1, flexWrap: 'wrap', gap: 1 }}>
            <OrderStatusChip status={o.status} />
            <PaymentStatusChip status={o.paymentStatus} />
            {!detail.registeredCustomer && (
              <Typography variant="body2" sx={{ alignSelf: 'center' }}>Guest order</Typography>
            )}
          </Stack>
        </Box>
        <Button onClick={() => window.print()} startIcon={<PrintIcon />} size="small" variant="outlined">
          Print
        </Button>
      </Stack>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}

      {detail.stockRestoredAt && (
        <Alert severity="info" sx={{ mb: 2 }}>
          This order was cancelled and its stock returned on{' '}
          {new Date(detail.stockRestoredAt).toLocaleString('en-IN')}.
        </Alert>
      )}

      <Grid container spacing={2.5}>
        {/* ---- items and actions ---- */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Stack spacing={2.5}>
            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Items
              </Typography>
              <Stack spacing={1.5} divider={<Divider flexItem />}>
                {o.lines.map((line) => (
                  <Stack key={line.productSku} direction="row" spacing={1.5} sx={{ alignItems: 'center' }}>
                    <Box sx={{ width: 48, flexShrink: 0, borderRadius: 1, overflow: 'hidden' }}>
                      <ProductImage
                        image={line.imageKey
                          ? { key: line.imageKey, alt: line.productName, width: null, height: null }
                          : null}
                        aspectRatio="1 / 1" sizes="48px"
                      />
                    </Box>
                    <Box sx={{ flexGrow: 1, minWidth: 0 }}>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{line.productName}</Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11.5 }}>
                        {line.productSku}
                      </Typography>
                    </Box>
                    <Typography variant="body2" sx={{ whiteSpace: 'nowrap' }}>
                      {money(line.unitPrice)} × {line.quantity}
                    </Typography>
                    <Typography sx={{ fontWeight: 700, whiteSpace: 'nowrap', minWidth: 84, textAlign: 'right' }}>
                      {money(line.lineTotal)}
                    </Typography>
                  </Stack>
                ))}
              </Stack>

              <Divider sx={{ my: 2 }} />
              <Stack spacing={0.75} sx={{ maxWidth: 280, ml: 'auto' }}>
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
                  <Typography sx={{ fontWeight: 700 }}>{money(o.total)}</Typography>
                </Stack>
              </Stack>
            </Card>

            {/* ---- move the order along ---- */}
            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Move this order along
              </Typography>

              {detail.allowedStatuses.length === 0 ? (
                <Typography variant="body2">
                  This order is {ORDER_STATUS_LABELS[o.status].toLowerCase()} — nothing further to do.
                </Typography>
              ) : (
                <>
                  <TextField
                    fullWidth size="small" label="Note (optional)"
                    value={statusNote} onChange={(e) => setStatusNote(e.target.value)}
                    sx={{ mb: 1.5 }}
                  />
                  <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                    {/* only the transitions the server considers legal */}
                    {detail.allowedStatuses.map((next) => (
                      <Button
                        key={next}
                        variant={next === 'CANCELLED' ? 'outlined' : 'contained'}
                        color={next === 'CANCELLED' ? 'inherit' : 'primary'}
                        size="small"
                        disabled={statusMutation.isPending}
                        onClick={() => {
                          if (next === 'CANCELLED'
                            && !window.confirm('Cancel this order and return its stock?')) return;
                          statusMutation.mutate(next);
                        }}
                      >
                        {ORDER_STATUS_LABELS[next]}
                      </Button>
                    ))}
                  </Stack>
                </>
              )}

              <Divider sx={{ my: 2 }} />

              <Typography sx={{ fontSize: 13, fontWeight: 600, mb: 1 }}>Payment</Typography>
              <Stack direction="row" spacing={1} sx={{ flexWrap: 'wrap', gap: 1 }}>
                {detail.allowedPaymentStatuses.map((next) => (
                  <Button
                    key={next} size="small" variant="outlined"
                    disabled={paymentStatusMutation.isPending}
                    onClick={() => paymentStatusMutation.mutate(next)}
                  >
                    Mark {PAYMENT_STATUS_LABELS[next].toLowerCase()}
                  </Button>
                ))}
                <Button
                  size="small" variant="contained" onClick={openPaymentDialog}
                  disabled={o.status === 'CANCELLED'}
                >
                  Record a payment
                </Button>
              </Stack>
            </Card>

            {/* ---- timeline ---- */}
            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                History
              </Typography>
              <Stack spacing={1.25}>
                {o.timeline.map((entry, index) => (
                  <Stack key={index} direction="row" spacing={1.5} sx={{ alignItems: 'baseline' }}>
                    <Typography
                      variant="body2"
                      sx={{ minWidth: 132, fontSize: 12, whiteSpace: 'nowrap' }}
                    >
                      {new Date(entry.changedAt).toLocaleString('en-IN', {
                        day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit',
                      })}
                    </Typography>
                    <Box>
                      <Typography sx={{ fontSize: 14 }}>
                        {entry.field === 'PAYMENT_STATUS' ? 'Payment ' : ''}
                        {entry.from ? `${entry.from} → ` : ''}
                        <strong>{entry.to}</strong>
                      </Typography>
                      {entry.note && (
                        <Typography variant="body2" sx={{ fontSize: 12.5 }}>{entry.note}</Typography>
                      )}
                    </Box>
                  </Stack>
                ))}
              </Stack>
            </Card>
          </Stack>
        </Grid>

        {/* ---- customer, payments, notes ---- */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={2.5}>
            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Delivering to
              </Typography>
              <Stack spacing={0.4}>
                <Typography sx={{ fontWeight: 600 }}>{o.delivery.name}</Typography>
                <Typography variant="body2">{o.delivery.line1}</Typography>
                {o.delivery.line2 && <Typography variant="body2">{o.delivery.line2}</Typography>}
                <Typography variant="body2">
                  {o.delivery.city}, {o.delivery.state} {o.delivery.postalCode}
                </Typography>
                <Link href={`tel:${o.delivery.phone}`} variant="body2" sx={{ mt: 0.75 }}>
                  {o.delivery.phone}
                </Link>
                {o.delivery.email && (
                  <Link href={`mailto:${o.delivery.email}`} variant="body2">{o.delivery.email}</Link>
                )}
                {detail.customerAccountEmail && (
                  <Typography variant="body2" sx={{ mt: 0.75, fontSize: 12 }}>
                    Account: {detail.customerAccountEmail}
                  </Typography>
                )}
              </Stack>

              {o.delivery.notes && (
                <>
                  <Divider sx={{ my: 1.5 }} />
                  <Typography variant="body2" sx={{ fontStyle: 'italic' }}>
                    {o.delivery.notes}
                  </Typography>
                </>
              )}

              {o.customerComments && (
                <>
                  <Divider sx={{ my: 1.5 }} />
                  <Typography sx={{ fontSize: 12, fontWeight: 600, mb: 0.5 }}>
                    Customer comments
                  </Typography>
                  <Typography variant="body2">{o.customerComments}</Typography>
                </>
              )}
            </Card>

            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Payments received
              </Typography>
              {detail.payments.length === 0 ? (
                <Typography variant="body2">
                  Nothing recorded yet. Paying by {o.paymentMethodLabel}.
                </Typography>
              ) : (
                <Stack spacing={1.25} divider={<Divider flexItem />}>
                  {detail.payments.map((p) => (
                    <Box key={p.id}>
                      <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
                        <Typography sx={{ fontWeight: 600 }}>{money(p.amount)}</Typography>
                        <Typography variant="body2">{p.receivedOn}</Typography>
                      </Stack>
                      <Typography variant="body2" sx={{ fontSize: 12 }}>
                        {p.methodCode}
                        {p.reference ? ` · ${p.reference}` : ''}
                      </Typography>
                      {p.note && <Typography variant="body2" sx={{ fontSize: 12 }}>{p.note}</Typography>}
                    </Box>
                  ))}
                </Stack>
              )}
            </Card>

            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 0.5 }}>
                Internal notes
              </Typography>
              <Typography variant="body2" sx={{ fontSize: 12, mb: 1.5 }}>
                Only the team sees these.
              </Typography>

              {detail.internalNotes && (
                <Box
                  sx={{
                    p: 1.25, mb: 1.5, borderRadius: 1.5, bgcolor: 'background.default',
                    whiteSpace: 'pre-wrap', fontSize: 13, maxHeight: 220, overflowY: 'auto',
                  }}
                >
                  {detail.internalNotes}
                </Box>
              )}

              <TextField
                fullWidth size="small" multiline minRows={2} label="Add a note"
                value={note} onChange={(e) => setNote(e.target.value)}
              />
              <Button
                size="small" variant="outlined" sx={{ mt: 1 }}
                disabled={!note.trim() || noteMutation.isPending}
                onClick={() => noteMutation.mutate()}
              >
                Add note
              </Button>
            </Card>
          </Stack>
        </Grid>
      </Grid>

      {/* ---- record a payment ---- */}
      <Dialog open={paymentOpen} onClose={() => setPaymentOpen(false)} fullWidth maxWidth="xs">
        <DialogTitle>Record a payment</DialogTitle>
        <DialogContent>
          <Stack spacing={2} sx={{ mt: 0.5 }}>
            <TextField
              select fullWidth size="small" label="Method"
              value={payment.methodCode}
              onChange={(e) => setPayment({ ...payment, methodCode: e.target.value })}
            >
              {[o.paymentMethodCode, 'CASH_ON_DELIVERY', 'BANK_TRANSFER',
                'UPI_ON_CONFIRMATION', 'PAY_ON_COLLECTION']
                .filter((code, index, all) => all.indexOf(code) === index)
                .map((code) => (
                  <MenuItem key={code} value={code}>{code.replaceAll('_', ' ').toLowerCase()}</MenuItem>
                ))}
            </TextField>
            <TextField
              fullWidth size="small" label="Amount received" value={payment.amount}
              onChange={(e) => setPayment({ ...payment, amount: e.target.value })}
            />
            <TextField
              fullWidth size="small" label="Received on" type="date" value={payment.receivedOn}
              onChange={(e) => setPayment({ ...payment, receivedOn: e.target.value })}
              slotProps={{ inputLabel: { shrink: true } }}
            />
            <TextField
              fullWidth size="small" label="Reference (optional)" value={payment.reference}
              onChange={(e) => setPayment({ ...payment, reference: e.target.value })}
              helperText="A UTR, UPI reference or receipt number"
            />
            <FormControlLabel
              control={
                <Checkbox
                  checked={payment.markAsPaid}
                  onChange={(e) => setPayment({ ...payment, markAsPaid: e.target.checked })}
                />
              }
              // separate, because a part payment is worth recording without
              // settling the order
              label="Mark the order as paid"
            />
          </Stack>
        </DialogContent>
        <DialogActions>
          <Button onClick={() => setPaymentOpen(false)}>Cancel</Button>
          <Button
            variant="contained"
            disabled={!payment.amount || paymentMutation.isPending}
            onClick={() => paymentMutation.mutate()}
          >
            {paymentMutation.isPending ? 'Saving…' : 'Record payment'}
          </Button>
        </DialogActions>
      </Dialog>
    </Box>
  );
}

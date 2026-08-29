import { useQuery } from '@tanstack/react-query';
import { Link as RouterLink } from 'react-router-dom';
import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import Skeleton from '@mui/material/Skeleton';
import Alert from '@mui/material/Alert';
import Divider from '@mui/material/Divider';
import Button from '@mui/material/Button';
import Link from '@mui/material/Link';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';

import { SEOHead } from '../../components/common/SEOHead';
import { OrderStatusChip, PaymentStatusChip } from '../../components/admin/StatusChip';
import { adminKeys, getDashboard } from '../../api/admin';
import { formatMoney } from '../../lib/format';
import { useSiteSettings } from '../../hooks/useSiteSettings';
import { palette } from '../../theme';

/** A headline number with the context that stops it being misread. */
function StatTile({
  label,
  value,
  hint,
  tone = 'neutral',
  to,
}: {
  label: string;
  value: string | number;
  hint?: string;
  tone?: 'neutral' | 'attention' | 'good';
  to?: string;
}) {
  const accent =
    tone === 'attention' ? palette.terracotta : tone === 'good' ? palette.forest : palette.rule;

  return (
    <Card
      sx={{
        p: 2.25,
        height: '100%',
        borderLeft: `3px solid ${accent}`,
        textDecoration: 'none',
        display: 'block',
        transition: 'border-color 160ms ease',
        ...(to ? { '&:hover': { borderColor: palette.terracotta } } : {}),
      }}
      {...(to ? { component: RouterLink, to } : {})}
    >
      <Typography
        sx={{
          fontSize: 11,
          letterSpacing: '0.12em',
          textTransform: 'uppercase',
          color: 'text.secondary',
        }}
      >
        {label}
      </Typography>
      <Typography
        sx={{
          fontSize: '1.9rem',
          fontWeight: 700,
          lineHeight: 1.15,
          mt: 0.5,
          fontVariantNumeric: 'tabular-nums',
          color: tone === 'attention' ? palette.terracotta : 'text.primary',
        }}
      >
        {value}
      </Typography>
      {hint && (
        <Typography variant="body2" sx={{ mt: 0.25, fontSize: 12.5 }}>
          {hint}
        </Typography>
      )}
    </Card>
  );
}

export function AdminDashboard() {
  const settings = useSiteSettings();
  const dashboard = useQuery({ queryKey: adminKeys.dashboard, queryFn: getDashboard });

  if (dashboard.isPending) {
    return (
      <Box>
        <Skeleton height={44} width={220} />
        <Grid container spacing={2} sx={{ mt: 1 }}>
          {Array.from({ length: 4 }).map((_, i) => (
            <Grid key={i} size={{ xs: 12, sm: 6, md: 3 }}>
              <Skeleton variant="rounded" height={108} />
            </Grid>
          ))}
        </Grid>
        <Skeleton variant="rounded" height={280} sx={{ mt: 3 }} />
      </Box>
    );
  }

  if (dashboard.isError) {
    return <Alert severity="error">The dashboard could not be loaded. Please refresh.</Alert>;
  }

  const d = dashboard.data;
  const money = (value: string | number) => formatMoney(value, d.currency, settings.locale);
  const awaitingPayment = d.ordersByPaymentStatus.AWAITING_PAYMENT ?? 0;

  return (
    <Box>
      <SEOHead title="Dashboard" path="/admin" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem', mb: 0.5 }}>
        Dashboard
      </Typography>
      <Typography variant="body2" sx={{ mb: 3 }}>
        What needs attention today.
      </Typography>

      {d.failedNotifications > 0 && (
        <Alert severity="error" sx={{ mb: 2.5 }}>
          {d.failedNotifications} notification{d.failedNotifications === 1 ? '' : 's'} could not be
          delivered after every retry. Check the mail configuration.
        </Alert>
      )}

      <Grid container spacing={2}>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatTile
            label="New orders"
            value={d.ordersNeedingAttention}
            hint="Placed and not yet confirmed"
            tone={d.ordersNeedingAttention > 0 ? 'attention' : 'neutral'}
            to="/admin/orders?status=NEW"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatTile
            label="Awaiting payment"
            value={awaitingPayment}
            hint="Instructions sent, money not in"
            tone={awaitingPayment > 0 ? 'attention' : 'neutral'}
            to="/admin/orders?paymentStatus=AWAITING_PAYMENT"
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatTile
            label="Order value"
            value={money(d.orderValueInWindow)}
            // the window is part of the figure, never implied
            hint={`${d.orderValueWindow} · ${d.ordersInWindow} orders · excludes cancelled`}
          />
        </Grid>
        <Grid size={{ xs: 12, sm: 6, md: 3 }}>
          <StatTile
            label="New enquiries"
            value={d.newEnquiries}
            hint="Unanswered messages"
            tone={d.newEnquiries > 0 ? 'attention' : 'neutral'}
            to="/admin/enquiries?status=NEW"
          />
        </Grid>
      </Grid>

      <Grid container spacing={2.5} sx={{ mt: 0.5 }}>
        {/* ---- recent orders ---- */}
        <Grid size={{ xs: 12, lg: 8 }}>
          <Card sx={{ p: 0, overflow: 'hidden' }}>
            <Stack
              direction="row"
              sx={{ justifyContent: 'space-between', alignItems: 'center', p: 2, pb: 1.5 }}
            >
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem' }}>
                Recent orders
              </Typography>
              <Button size="small" component={RouterLink} to="/admin/orders">
                View all
              </Button>
            </Stack>
            <Divider />

            {d.recentOrders.length === 0 ? (
              <Box sx={{ p: 4, textAlign: 'center' }}>
                <Typography variant="body2">No orders yet.</Typography>
              </Box>
            ) : (
              <Box sx={{ overflowX: 'auto' }}>
                <Table size="small">
                  <TableHead>
                    <TableRow>
                      <TableCell>Order</TableCell>
                      <TableCell>Customer</TableCell>
                      <TableCell>Status</TableCell>
                      <TableCell align="right">Total</TableCell>
                    </TableRow>
                  </TableHead>
                  <TableBody>
                    {d.recentOrders.map((order) => (
                      <TableRow key={order.orderNumber} hover>
                        <TableCell>
                          <Link
                            component={RouterLink}
                            to={`/admin/orders/${order.orderNumber}`}
                            sx={{ fontFamily: 'monospace', fontWeight: 600 }}
                          >
                            {order.orderNumber}
                          </Link>
                        </TableCell>
                        <TableCell>
                          {order.customerName}
                          {!order.registeredCustomer && (
                            <Typography component="span" variant="body2" sx={{ ml: 0.75 }}>
                              (guest)
                            </Typography>
                          )}
                        </TableCell>
                        <TableCell>
                          <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                            <OrderStatusChip status={order.status} />
                            <PaymentStatusChip status={order.paymentStatus} />
                          </Stack>
                        </TableCell>
                        <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                          {money(order.total)}
                        </TableCell>
                      </TableRow>
                    ))}
                  </TableBody>
                </Table>
              </Box>
            )}
          </Card>
        </Grid>

        {/* ---- low stock and enquiries ---- */}
        <Grid size={{ xs: 12, lg: 4 }}>
          <Stack spacing={2.5}>
            <Card sx={{ p: 2 }}>
              <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 1.5 }}>
                Low stock
              </Typography>
              {d.lowStock.length === 0 ? (
                <Typography variant="body2">Everything is comfortably in stock.</Typography>
              ) : (
                <Stack spacing={1} divider={<Divider flexItem />}>
                  {d.lowStock.map((item) => (
                    <Stack
                      key={item.id}
                      direction="row"
                      sx={{ justifyContent: 'space-between', alignItems: 'baseline', gap: 1 }}
                    >
                      <Box sx={{ minWidth: 0 }}>
                        <Typography sx={{ fontSize: 14, fontWeight: 600 }} noWrap>
                          {item.name}
                        </Typography>
                        <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11.5 }}>
                          {item.sku}
                        </Typography>
                      </Box>
                      <Typography
                        sx={{
                          fontWeight: 700,
                          color: item.stockQuantity === 0 ? palette.terracotta : 'text.primary',
                          fontVariantNumeric: 'tabular-nums',
                          whiteSpace: 'nowrap',
                        }}
                      >
                        {item.stockQuantity} left
                      </Typography>
                    </Stack>
                  ))}
                </Stack>
              )}
            </Card>

            <Card sx={{ p: 2 }}>
              <Stack
                direction="row"
                sx={{ justifyContent: 'space-between', alignItems: 'center', mb: 1.5 }}
              >
                <Typography variant="h6" component="h2" sx={{ fontSize: '1rem' }}>
                  Recent enquiries
                </Typography>
                <Button size="small" component={RouterLink} to="/admin/enquiries">
                  All
                </Button>
              </Stack>

              {d.recentEnquiries.length === 0 ? (
                <Typography variant="body2">No messages yet.</Typography>
              ) : (
                <Stack spacing={1.25} divider={<Divider flexItem />}>
                  {d.recentEnquiries.map((enquiry) => (
                    <Box key={enquiry.id} sx={{ minWidth: 0 }}>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }} noWrap>
                        {enquiry.subject}
                      </Typography>
                      <Typography variant="body2" noWrap>
                        {enquiry.name} · {enquiry.email}
                      </Typography>
                    </Box>
                  ))}
                </Stack>
              )}
            </Card>

            {d.pendingNotifications > 0 && (
              <Alert severity="info">
                {d.pendingNotifications} notification
                {d.pendingNotifications === 1 ? ' is' : 's are'} queued for delivery.
              </Alert>
            )}
          </Stack>
        </Grid>
      </Grid>
    </Box>
  );
}

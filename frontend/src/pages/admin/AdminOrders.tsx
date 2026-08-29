import { useState } from 'react';
import { Link as RouterLink, useSearchParams } from 'react-router-dom';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import Link from '@mui/material/Link';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import DownloadIcon from '@mui/icons-material/FileDownloadOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { OrderStatusChip, PaymentStatusChip } from '../../components/admin/StatusChip';
import { EmptyState } from '../../components/common/EmptyState';
import { adminKeys, listOrders, orderExportUrl } from '../../api/admin';
import type { OrderFilters } from '../../api/admin';
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from '../../api/orders';
import type { OrderStatus, PaymentStatus } from '../../api/orders';
import { formatMoney } from '../../lib/format';
import { useSiteSettings } from '../../hooks/useSiteSettings';

const ORDER_STATUSES = Object.keys(ORDER_STATUS_LABELS) as OrderStatus[];
const PAYMENT_STATUSES = Object.keys(PAYMENT_STATUS_LABELS) as PaymentStatus[];

export function AdminOrders() {
  const settings = useSiteSettings();
  const [params, setParams] = useSearchParams();

  const q = params.get('q') ?? '';
  const status = params.get('status') ?? '';
  const paymentStatus = params.get('paymentStatus') ?? '';
  const placedFrom = params.get('placedFrom') ?? '';
  const placedTo = params.get('placedTo') ?? '';
  const page = Number(params.get('page') ?? 0);

  const [searchDraft, setSearchDraft] = useState(q);

  // URL params arrive as plain strings; narrowed here so the query key and the
  // request agree on the type rather than being cast at each call site
  const filters: OrderFilters = {
    q,
    status: status as OrderStatus | '',
    paymentStatus: paymentStatus as PaymentStatus | '',
    placedFrom,
    placedTo,
    page,
    size: 20,
  };

  const orders = useQuery({
    queryKey: adminKeys.orders(filters),
    queryFn: () => listOrders(filters),
    placeholderData: keepPreviousData,
  });

  /** Filters live in the URL, so a filtered view can be shared or bookmarked. */
  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    // any filter change invalidates the page number
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: true });
  };

  const clearAll = () => {
    setSearchDraft('');
    setParams(new URLSearchParams(), { replace: true });
  };

  const money = (value: string | number, currency: string) =>
    formatMoney(value, currency, settings.locale);

  const rows = orders.data?.content ?? [];
  const filtersApplied = Boolean(q || status || paymentStatus || placedFrom || placedTo);

  return (
    <Box>
      <SEOHead title="Orders" path="/admin/orders" noIndex />

      <Stack
        direction={{ xs: 'column', sm: 'row' }}
        spacing={1.5}
        sx={{ justifyContent: 'space-between', alignItems: { sm: 'center' }, mb: 2.5 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
            Orders
          </Typography>
          <Typography variant="body2">
            {orders.isPending
              ? 'Loading…'
              : `${orders.data?.totalElements ?? 0} matching`}
          </Typography>
        </Box>

        <Button
          component="a"
          href={orderExportUrl({ placedFrom, placedTo, status: (status || undefined) as OrderStatus })}
          startIcon={<DownloadIcon />}
          variant="outlined"
          size="small"
        >
          Export CSV
        </Button>
      </Stack>

      <Card sx={{ p: 2, mb: 2.5 }}>
        <Box
          component="form"
          onSubmit={(e) => {
            e.preventDefault();
            setFilter('q', searchDraft.trim());
          }}
        >
          <Grid container spacing={1.5} sx={{ alignItems: 'center' }}>
            <Grid size={{ xs: 12, md: 3 }}>
              <TextField
                fullWidth
                size="small"
                label="Search"
                placeholder="Order number, name, phone or email"
                value={searchDraft}
                onChange={(e) => setSearchDraft(e.target.value)}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                select fullWidth size="small" label="Status"
                value={status}
                onChange={(e) => setFilter('status', e.target.value)}
              >
                <MenuItem value="">Any status</MenuItem>
                {ORDER_STATUSES.map((s) => (
                  <MenuItem key={s} value={s}>{ORDER_STATUS_LABELS[s]}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                select fullWidth size="small" label="Payment"
                value={paymentStatus}
                onChange={(e) => setFilter('paymentStatus', e.target.value)}
              >
                <MenuItem value="">Any payment</MenuItem>
                {PAYMENT_STATUSES.map((s) => (
                  <MenuItem key={s} value={s}>{PAYMENT_STATUS_LABELS[s]}</MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                fullWidth size="small" label="From" type="date"
                value={placedFrom}
                onChange={(e) => setFilter('placedFrom', e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
            <Grid size={{ xs: 6, md: 2 }}>
              <TextField
                fullWidth size="small" label="To" type="date"
                value={placedTo}
                onChange={(e) => setFilter('placedTo', e.target.value)}
                slotProps={{ inputLabel: { shrink: true } }}
              />
            </Grid>
            <Grid size={{ xs: 12, md: 1 }}>
              <Stack direction="row" spacing={1}>
                <Button type="submit" variant="contained" size="small" fullWidth>
                  Find
                </Button>
              </Stack>
            </Grid>
          </Grid>

          {filtersApplied && (
            <Button onClick={clearAll} size="small" sx={{ mt: 1 }}>
              Clear filters
            </Button>
          )}
        </Box>
      </Card>

      {orders.isError && (
        <Alert severity="error" sx={{ mb: 2 }}>
          Orders could not be loaded. Please refresh.
        </Alert>
      )}

      <Card sx={{ overflow: 'hidden' }}>
        {orders.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 6 }).map((_, i) => (
              <Skeleton key={i} height={44} />
            ))}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState
              title="No orders match"
              description={
                filtersApplied
                  ? 'Try widening the dates, or clear the filters.'
                  : 'Orders will appear here as they come in.'
              }
              actionLabel={filtersApplied ? 'Clear filters' : undefined}
              onAction={filtersApplied ? clearAll : undefined}
            />
          </Box>
        ) : (
          <>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Order</TableCell>
                    <TableCell>Placed</TableCell>
                    <TableCell>Customer</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Items</TableCell>
                    <TableCell align="right">Total</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((order) => (
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
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {new Date(order.placedAt).toLocaleDateString('en-IN', {
                          day: 'numeric', month: 'short', year: 'numeric',
                        })}
                      </TableCell>
                      <TableCell>
                        <Box sx={{ minWidth: 0 }}>
                          <Typography sx={{ fontSize: 14 }}>{order.customerName}</Typography>
                          <Typography variant="body2" sx={{ fontSize: 12 }}>
                            {order.customerPhone}
                            {!order.registeredCustomer && ' · guest'}
                          </Typography>
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5} sx={{ flexWrap: 'wrap', gap: 0.5 }}>
                          <OrderStatusChip status={order.status} />
                          <PaymentStatusChip status={order.paymentStatus} />
                        </Stack>
                      </TableCell>
                      <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {order.itemCount}
                      </TableCell>
                      <TableCell
                        align="right"
                        sx={{ fontVariantNumeric: 'tabular-nums', whiteSpace: 'nowrap', fontWeight: 600 }}
                      >
                        {money(order.total, order.currency)}
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>

            <TablePagination
              component="div"
              count={orders.data?.totalElements ?? 0}
              page={page}
              rowsPerPage={20}
              rowsPerPageOptions={[20]}
              onPageChange={(_, next) => setFilter('page', String(next))}
            />
          </>
        )}
      </Card>
    </Box>
  );
}

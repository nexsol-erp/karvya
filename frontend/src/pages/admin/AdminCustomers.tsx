import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useMutation, useQuery, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import Chip from '@mui/material/Chip';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import TablePagination from '@mui/material/TablePagination';
import Alert from '@mui/material/Alert';
import Skeleton from '@mui/material/Skeleton';
import Link from '@mui/material/Link';

import { SEOHead } from '../../components/common/SEOHead';
import { EmptyState } from '../../components/common/EmptyState';
import {
  adminKeys, listCustomers, sendCustomerPasswordReset, setCustomerEnabled,
} from '../../api/admin';
import { ApiError } from '../../api/client';

export function AdminCustomers() {
  const [params, setParams] = useSearchParams();
  const queryClient = useQueryClient();
  const [message, setMessage] = useState<{ tone: 'success' | 'error'; text: string } | null>(null);

  const q = params.get('q') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [searchDraft, setSearchDraft] = useState(q);

  const customers = useQuery({
    queryKey: adminKeys.customers(q, page),
    queryFn: () => listCustomers(q, page),
    placeholderData: keepPreviousData,
  });

  const refresh = () => queryClient.invalidateQueries({ queryKey: ['admin', 'customers'] });

  const enabledMutation = useMutation({
    mutationFn: ({ id, enabled }: { id: number; enabled: boolean }) =>
      setCustomerEnabled(id, enabled),
    onSuccess: (_, variables) => {
      refresh();
      setMessage({
        tone: 'success',
        text: variables.enabled
          ? 'That account can sign in again.'
          : 'That account can no longer sign in. Their orders are untouched.',
      });
    },
    onError: (err) =>
      setMessage({
        tone: 'error',
        text: err instanceof ApiError ? err.message : 'That change could not be saved.',
      }),
  });

  const resetMutation = useMutation({
    mutationFn: (id: number) => sendCustomerPasswordReset(id),
    onSuccess: (result) => setMessage({ tone: 'success', text: result.status }),
    onError: (err) =>
      setMessage({
        tone: 'error',
        text: err instanceof ApiError ? err.message : 'The reset link could not be sent.',
      }),
  });

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: true });
  };

  const rows = customers.data?.content ?? [];

  return (
    <Box>
      <SEOHead title="Customers" path="/admin/customers" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>Customers</Typography>
      <Typography variant="body2" sx={{ mb: 2.5 }}>
        {customers.isPending ? 'Loading…' : `${customers.data?.totalElements ?? 0} registered`}
      </Typography>

      {message && (
        <Alert severity={message.tone} sx={{ mb: 2 }} onClose={() => setMessage(null)}>
          {message.text}
        </Alert>
      )}

      <Card sx={{ p: 2, mb: 2.5 }}>
        <Box component="form" onSubmit={(e) => { e.preventDefault(); setFilter('q', searchDraft.trim()); }}>
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5}>
            <TextField
              size="small" label="Search" placeholder="Name, email or phone"
              value={searchDraft} onChange={(e) => setSearchDraft(e.target.value)}
              sx={{ flexGrow: 1 }}
            />
            <Button type="submit" variant="contained" size="small">Find</Button>
          </Stack>
        </Box>
      </Card>

      <Card sx={{ overflow: 'hidden' }}>
        {customers.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} height={48} />)}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState
              title="No customers match"
              description="Accounts appear here once people register."
            />
          </Box>
        ) : (
          <>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell>Customer</TableCell>
                    <TableCell>Member since</TableCell>
                    <TableCell>Last signed in</TableCell>
                    <TableCell>Access</TableCell>
                    <TableCell align="right">Actions</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((customer) => (
                    <TableRow key={customer.id} hover>
                      <TableCell>
                        <Typography sx={{ fontSize: 14, fontWeight: 600 }}>
                          {customer.fullName}
                        </Typography>
                        <Link href={`mailto:${customer.email}`} variant="body2">
                          {customer.email}
                        </Link>
                        {customer.phone && (
                          <Typography variant="body2" sx={{ fontSize: 12 }}>{customer.phone}</Typography>
                        )}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {new Date(customer.memberSince).toLocaleDateString('en-IN', {
                          month: 'short', year: 'numeric',
                        })}
                      </TableCell>
                      <TableCell sx={{ whiteSpace: 'nowrap' }}>
                        {customer.lastLoginAt
                          ? new Date(customer.lastLoginAt).toLocaleDateString('en-IN', {
                              day: 'numeric', month: 'short', year: 'numeric',
                            })
                          : 'Never'}
                      </TableCell>
                      <TableCell>
                        <Stack direction="row" spacing={0.5}>
                          <Chip
                            size="small"
                            label={customer.enabled ? 'Active' : 'Disabled'}
                            color={customer.enabled ? 'success' : 'default'}
                            variant={customer.enabled ? 'outlined' : 'filled'}
                          />
                          {customer.locked && (
                            <Chip size="small" label="Locked out" color="warning" variant="outlined" />
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell align="right">
                        <Stack direction="row" spacing={0.5} sx={{ justifyContent: 'flex-end' }}>
                          {/* the administrator never sees or sets the password */}
                          <Button
                            size="small"
                            disabled={!customer.enabled || resetMutation.isPending}
                            onClick={() => resetMutation.mutate(customer.id)}
                          >
                            Send reset link
                          </Button>
                          <Button
                            size="small"
                            color={customer.enabled ? 'inherit' : 'primary'}
                            disabled={enabledMutation.isPending}
                            onClick={() => {
                              if (customer.enabled
                                && !window.confirm('Stop this customer signing in? Their orders are kept.')) return;
                              enabledMutation.mutate({ id: customer.id, enabled: !customer.enabled });
                            }}
                          >
                            {customer.enabled ? 'Disable' : 'Enable'}
                          </Button>
                        </Stack>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>

            <TablePagination
              component="div"
              count={customers.data?.totalElements ?? 0}
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

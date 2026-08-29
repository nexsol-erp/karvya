import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
import EditIcon from '@mui/icons-material/EditOutlined';
import DeleteIcon from '@mui/icons-material/DeleteOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { EmptyState } from '../../components/common/EmptyState';
import { ApiError } from '../../api/client';
import { adminKeys, deleteVendor, getVendor, listVendors, saveVendor } from '../../api/admin';

const BLANK = {
  name: '',
  contactName: '',
  email: '',
  phone: '',
  address: '',
  deliveryTime: '',
  conditions: '',
  active: true,
};

type Form = typeof BLANK;

/**
 * Suppliers.
 *
 * <p>Kept out of the storefront entirely. What a piece costs to buy is the
 * shop's margin, and a supplier's phone number is not a shopper's business -
 * so none of this appears on any public endpoint. It exists so that whoever is
 * fulfilling an order can see who to reorder from without looking it up
 * somewhere else.
 */
export function AdminVendors() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<number | null>(null);
  const [form, setForm] = useState<Form>(BLANK);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const vendors = useQuery({ queryKey: adminKeys.vendors, queryFn: listVendors });

  function reset() {
    setEditing(null);
    setForm(BLANK);
    setOpen(false);
    setFieldErrors({});
  }

  function refresh() {
    queryClient.invalidateQueries({ queryKey: adminKeys.vendors });
    // a product form shows the supplier list, and the count on this page
    // depends on what products point where
    queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
  }

  function report(err: unknown, fallback: string) {
    if (err instanceof ApiError) {
      const fields = err.fieldErrors;
      if (Object.keys(fields).length > 0) {
        setFieldErrors(fields);
        setError('Please check the highlighted fields.');
      } else {
        setError(err.message);
      }
    } else {
      setError(fallback);
    }
  }

  const save = useMutation({
    mutationFn: () => saveVendor(editing, form),
    onSuccess: () => {
      refresh();
      setNotice(editing === null ? 'Supplier added.' : 'Supplier saved.');
      reset();
    },
    onError: (err) => report(err, 'That supplier could not be saved.'),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteVendor(id),
    onSuccess: () => {
      refresh();
      setNotice('Supplier removed.');
    },
    // the server refuses while products still point at it, and says how many
    onError: (err) => report(err, 'That supplier could not be removed.'),
  });

  async function startEdit(id: number) {
    setError(null);
    setNotice(null);
    setFieldErrors({});
    try {
      const vendor = await getVendor(id);
      setForm({
        name: vendor.name,
        contactName: vendor.contactName ?? '',
        email: vendor.email ?? '',
        phone: vendor.phone ?? '',
        address: vendor.address ?? '',
        deliveryTime: vendor.deliveryTime ?? '',
        conditions: vendor.conditions ?? '',
        active: vendor.active,
      });
      setEditing(id);
      setOpen(true);
    } catch {
      setError('That supplier could not be loaded.');
    }
  }

  const set =
    (key: keyof Form) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const rows = vendors.data ?? [];

  return (
    <Box>
      <SEOHead title="Suppliers" path="/admin/vendors" noIndex />

      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
            Suppliers
          </Typography>
          <Typography variant="body2">
            Who each product is bought from. Never shown to customers.
          </Typography>
        </Box>
        <Button
          variant="contained"
          size="small"
          onClick={() => {
            reset();
            setOpen(true);
          }}
        >
          Add a supplier
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mt: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mt: 2 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      {open && (
        <Card sx={{ p: 2.5, mt: 2.5 }}>
          <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 2 }}>
            {editing === null ? 'New supplier' : 'Edit supplier'}
          </Typography>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Name" value={form.name} onChange={set('name')}
                error={Boolean(fieldErrors.name)} helperText={fieldErrors.name}
                required fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Contact person" value={form.contactName} onChange={set('contactName')}
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Email" value={form.email} onChange={set('email')}
                error={Boolean(fieldErrors.email)} helperText={fieldErrors.email}
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Phone" value={form.phone} onChange={set('phone')}
                error={Boolean(fieldErrors.phone)} helperText={fieldErrors.phone}
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                label="Address" value={form.address} onChange={set('address')}
                multiline minRows={2} fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Usual delivery time" value={form.deliveryTime}
                onChange={set('deliveryTime')}
                error={Boolean(fieldErrors.deliveryTime)}
                helperText={fieldErrors.deliveryTime ?? 'In their words, e.g. "2 to 3 weeks"'}
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <Box sx={{ display: 'flex', alignItems: 'center', height: '100%' }}>
                <FormControlLabel
                  control={
                    <Switch
                      checked={form.active}
                      onChange={(_, checked) => setForm((p) => ({ ...p, active: checked }))}
                    />
                  }
                  label="Currently supplying"
                />
              </Box>
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                label="Terms and conditions" value={form.conditions} onChange={set('conditions')}
                multiline minRows={2} fullWidth size="small"
                helperText="Payment terms, minimum order, anything worth remembering"
              />
            </Grid>
          </Grid>

          <Stack direction="row" spacing={1} sx={{ mt: 2.5 }}>
            <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>
              {save.isPending ? 'Saving…' : editing === null ? 'Add supplier' : 'Save changes'}
            </Button>
            <Button onClick={reset}>Cancel</Button>
          </Stack>
        </Card>
      )}

      <Card sx={{ mt: 2.5, overflow: 'hidden' }}>
        {vendors.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} height={52} />)}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState
              title="No suppliers yet"
              description="Add one, then choose it on a product to record where that piece comes from."
            />
          </Box>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell>Supplier</TableCell>
                  <TableCell>Contact</TableCell>
                  <TableCell>Delivery</TableCell>
                  <TableCell align="right">Products</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((vendor) => (
                  <TableRow key={vendor.id} hover>
                    <TableCell>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{vendor.name}</Typography>
                      {!vendor.active && (
                        <Chip size="small" variant="outlined" label="Inactive"
                              sx={{ height: 18, fontSize: 10 }} />
                      )}
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{vendor.contactName ?? '—'}</Typography>
                      <Typography variant="body2" sx={{ fontSize: 12 }}>
                        {[vendor.phone, vendor.email].filter(Boolean).join(' · ') || '—'}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      <Typography variant="body2">{vendor.deliveryTime ?? '—'}</Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {vendor.productCount}
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => startEdit(vendor.id)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          vendor.productCount > 0
                            ? 'Products still come from here — deactivate instead'
                            : 'Remove'
                        }
                      >
                        <span>
                          <IconButton
                            size="small"
                            disabled={vendor.productCount > 0 || remove.isPending}
                            onClick={() => remove.mutate(vendor.id)}
                          >
                            <DeleteIcon fontSize="small" />
                          </IconButton>
                        </span>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Card>

      <Divider sx={{ my: 3 }} />
      <Typography variant="body2">
        A supplier that still sources products cannot be removed — the products
        would silently lose where they came from. Deactivate it instead: it stays
        on its existing products and stops being offered for new ones.
      </Typography>
    </Box>
  );
}

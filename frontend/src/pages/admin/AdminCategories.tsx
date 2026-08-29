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
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
import EditIcon from '@mui/icons-material/EditOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { EmptyState } from '../../components/common/EmptyState';
import { ApiError } from '../../api/client';
import {
  adminKeys,
  listCategories,
  saveCategory,
  setCategoryActive,
  type AdminCategory,
} from '../../api/admin';

const BLANK = { name: '', slug: '', description: '', displayOrder: '0', active: true };

type Form = typeof BLANK;

/**
 * Categories, which decide how the shop is browsed.
 *
 * <p>A category is customer-facing: its name is a heading and its slug is in
 * the URL, so a rename after launch breaks whatever links to it. Deactivating
 * is offered instead of deleting for the same reason - and because a category
 * that still holds products cannot be removed without stranding them.
 */
export function AdminCategories() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<number | null>(null);
  const [form, setForm] = useState<Form>(BLANK);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const categories = useQuery({ queryKey: adminKeys.categories, queryFn: listCategories });

  function reset() {
    setEditing(null);
    setForm(BLANK);
    setOpen(false);
    setFieldErrors({});
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

  function refresh() {
    queryClient.invalidateQueries({ queryKey: adminKeys.categories });
    // the storefront reads the same list for its shop navigation
    queryClient.invalidateQueries({ queryKey: ['categories'] });
  }

  const save = useMutation({
    mutationFn: () =>
      saveCategory(editing, { ...form, displayOrder: Number(form.displayOrder) || 0 }),
    onSuccess: () => {
      refresh();
      setNotice(editing === null ? 'Category created.' : 'Category saved.');
      reset();
    },
    onError: (err) => report(err, 'That category could not be saved.'),
  });

  const toggle = useMutation({
    mutationFn: ({ id, active }: { id: number; active: boolean }) => setCategoryActive(id, active),
    onSuccess: () => {
      refresh();
      setError(null);
    },
    onError: (err) => report(err, 'That could not be changed.'),
  });

  function startEdit(category: AdminCategory) {
    setError(null);
    setNotice(null);
    setFieldErrors({});
    setForm({
      name: category.name,
      slug: category.slug,
      description: category.description ?? '',
      displayOrder: String(category.displayOrder),
      active: category.active,
    });
    setEditing(category.id);
    setOpen(true);
  }

  const set =
    (key: keyof Form) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const rows = categories.data ?? [];

  return (
    <Box>
      <SEOHead title="Categories" path="/admin/categories" noIndex />

      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}
      >
        <Box>
          <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
            Categories
          </Typography>
          <Typography variant="body2">How the shop is browsed.</Typography>
        </Box>
        <Button
          variant="contained"
          size="small"
          onClick={() => {
            reset();
            setOpen(true);
          }}
        >
          New category
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
            {editing === null ? 'New category' : 'Edit category'}
          </Typography>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 7 }}>
              <TextField
                label="Name" value={form.name} onChange={set('name')}
                error={Boolean(fieldErrors.name)} helperText={fieldErrors.name}
                required fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 5 }}>
              <TextField
                label="Display order" value={form.displayOrder} onChange={set('displayOrder')}
                helperText="Lower numbers come first"
                fullWidth size="small" inputMode="numeric"
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                label="Slug" value={form.slug} onChange={set('slug')}
                error={Boolean(fieldErrors.slug)}
                helperText={
                  fieldErrors.slug ??
                  (editing === null
                    ? 'Leave empty to derive it from the name'
                    : 'This is in the shop URL — changing it breaks existing links')
                }
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12 }}>
              <TextField
                label="Description" value={form.description} onChange={set('description')}
                multiline minRows={2} fullWidth size="small"
              />
            </Grid>
          </Grid>

          <Stack direction="row" spacing={1} sx={{ mt: 2.5 }}>
            <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>
              {save.isPending ? 'Saving…' : editing === null ? 'Create category' : 'Save changes'}
            </Button>
            <Button onClick={reset}>Cancel</Button>
          </Stack>
        </Card>
      )}

      <Card sx={{ mt: 2.5, overflow: 'hidden' }}>
        {categories.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} height={52} />)}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState title="No categories" description="Create one to group your products." />
          </Box>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 80 }}>Order</TableCell>
                  <TableCell>Category</TableCell>
                  <TableCell align="right">Products</TableCell>
                  <TableCell align="center">Shown</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((category) => (
                  <TableRow key={category.id} hover>
                    <TableCell sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {category.displayOrder}
                    </TableCell>
                    <TableCell>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }}>
                        {category.name}
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11.5 }}>
                        /shop/{category.slug}
                      </Typography>
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {category.productCount}
                    </TableCell>
                    <TableCell align="center">
                      <Tooltip
                        title={
                          category.active
                            ? 'Hide from the shop'
                            : 'Show in the shop'
                        }
                      >
                        <span>
                          <Switch
                            size="small"
                            checked={category.active}
                            disabled={toggle.isPending}
                            onChange={(_, active) => toggle.mutate({ id: category.id, active })}
                          />
                        </span>
                      </Tooltip>
                    </TableCell>
                    <TableCell align="right">
                      {!category.active && category.productCount > 0 && (
                        <Chip
                          size="small" color="warning" variant="outlined"
                          label={`${category.productCount} hidden`}
                          sx={{ height: 18, fontSize: 10, mr: 1 }}
                        />
                      )}
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => startEdit(category)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                    </TableCell>
                  </TableRow>
                ))}
              </TableBody>
            </Table>
          </Box>
        )}
      </Card>

      <Typography variant="body2" sx={{ mt: 2 }}>
        Hiding a category removes it from the shop along with everything in it.
        The products keep their own status — they are simply not reachable by
        browsing while their category is hidden.
      </Typography>
    </Box>
  );
}

import { useState } from 'react';
import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Skeleton from '@mui/material/Skeleton';
import Table from '@mui/material/Table';
import TableBody from '@mui/material/TableBody';
import TableCell from '@mui/material/TableCell';
import TableHead from '@mui/material/TableHead';
import TableRow from '@mui/material/TableRow';
import Switch from '@mui/material/Switch';
import Tooltip from '@mui/material/Tooltip';
import EditIcon from '@mui/icons-material/EditOutlined';
import DeleteIcon from '@mui/icons-material/DeleteOutlined';

import { SEOHead } from '../../components/common/SEOHead';
import { EmptyState } from '../../components/common/EmptyState';
import { NumberField } from '../../components/common/NumberField';
import { ApiError } from '../../api/client';
import {
  adminKeys,
  deleteAttribute,
  listAttributes,
  listCategories,
  saveAttribute,
  type AttributeRow,
} from '../../api/admin';

const BLANK = {
  label: '',
  slug: '',
  categoryId: '',
  helpText: '',
  displayOrder: '0',
  active: true,
};

type Form = typeof BLANK;

/**
 * The fields a product should have, decided here rather than in the code.
 *
 * <p>Scoped to a category, so one shop can sell more than one kind of thing: a
 * book is asked for its ISBN and a bird house for its material, and neither is
 * asked for the other's. Selling something new is then a category and a few
 * definitions - no migration, no deployment.
 */
export function AdminAttributes() {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState<AttributeRow | null>(null);
  const [form, setForm] = useState<Form>(BLANK);
  const [open, setOpen] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});

  const attributes = useQuery({ queryKey: adminKeys.attributes, queryFn: listAttributes });
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
    queryClient.invalidateQueries({ queryKey: adminKeys.attributes });
    // the product form asks for whatever is defined here
    queryClient.invalidateQueries({ queryKey: ['admin', 'product'] });
  }

  const save = useMutation({
    mutationFn: () =>
      saveAttribute(editing?.id ?? null, {
        ...form,
        categoryId: form.categoryId === '' ? null : Number(form.categoryId),
        displayOrder: Number(form.displayOrder) || 0,
      }),
    onSuccess: () => {
      refresh();
      setNotice(editing === null ? 'Attribute defined.' : 'Attribute saved.');
      reset();
    },
    onError: (err) => report(err, 'That attribute could not be saved.'),
  });

  const remove = useMutation({
    mutationFn: (id: number) => deleteAttribute(id),
    onSuccess: () => {
      refresh();
      setNotice('Attribute removed.');
    },
    onError: (err) => report(err, 'That attribute could not be removed.'),
  });

  function startEdit(attribute: AttributeRow) {
    setError(null);
    setNotice(null);
    setFieldErrors({});
    setForm({
      label: attribute.label,
      slug: attribute.slug,
      categoryId: attribute.categoryId === null ? '' : String(attribute.categoryId),
      helpText: attribute.helpText ?? '',
      displayOrder: String(attribute.displayOrder),
      active: attribute.active,
    });
    setEditing(attribute);
    setOpen(true);
  }

  const set =
    (key: keyof Form) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const rows = attributes.data ?? [];

  return (
    <Box>
      <SEOHead title="Attributes" path="/admin/attributes" noIndex />

      <Stack direction="row" sx={{ justifyContent: 'space-between', alignItems: 'center', gap: 2 }}>
        <Box>
          <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
            Attributes
          </Typography>
          <Typography variant="body2">
            The fields a product is asked for, and shown on its page.
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
          New attribute
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
            {editing === null ? 'New attribute' : `Edit ${editing.label}`}
          </Typography>

          <Grid container spacing={2}>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                label="Label" value={form.label} onChange={set('label')}
                error={Boolean(fieldErrors.label)}
                helperText={fieldErrors.label ?? 'What the field is called, e.g. ISBN'}
                required fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 6 }}>
              <TextField
                select label="Applies to" value={form.categoryId} onChange={set('categoryId')}
                helperText="Which products are asked for it"
                fullWidth size="small"
              >
                <MenuItem value="">Every product</MenuItem>
                {(categories.data ?? []).map((category) => (
                  <MenuItem key={category.id} value={String(category.id)}>
                    {category.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
            <Grid size={{ xs: 12, sm: 8 }}>
              <TextField
                label="Help text" value={form.helpText} onChange={set('helpText')}
                helperText="Shown under the field on the product form"
                fullWidth size="small"
              />
            </Grid>
            <Grid size={{ xs: 12, sm: 4 }}>
              <NumberField
                label="Display order" value={form.displayOrder}
                onChange={(v) => setForm((p) => ({ ...p, displayOrder: v }))}
                helperText="Lower numbers come first"
                fullWidth size="small"
              />
            </Grid>
          </Grid>

          {editing !== null && (
            <Alert severity="info" variant="outlined" sx={{ mt: 2 }}>
              The internal name stays <strong>{editing.slug}</strong>. Renaming the
              label is safe; the name is what the recorded answers are keyed by, so
              changing it would orphan every value already entered.
            </Alert>
          )}

          <Stack direction="row" spacing={1} sx={{ mt: 2.5 }}>
            <Button variant="contained" onClick={() => save.mutate()} disabled={save.isPending}>
              {save.isPending ? 'Saving…' : editing === null ? 'Define attribute' : 'Save changes'}
            </Button>
            <Button onClick={reset}>Cancel</Button>
          </Stack>
        </Card>
      )}

      <Card sx={{ mt: 2.5, overflow: 'hidden' }}>
        {attributes.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 3 }).map((_, i) => <Skeleton key={i} height={52} />)}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState
              title="No attributes yet"
              description="Define one, and every product in that category will be asked for it."
            />
          </Box>
        ) : (
          <Box sx={{ overflowX: 'auto' }}>
            <Table size="small">
              <TableHead>
                <TableRow>
                  <TableCell sx={{ width: 70 }}>Order</TableCell>
                  <TableCell>Field</TableCell>
                  <TableCell>Applies to</TableCell>
                  <TableCell align="right">Answered</TableCell>
                  <TableCell align="center">Active</TableCell>
                  <TableCell align="right" />
                </TableRow>
              </TableHead>
              <TableBody>
                {rows.map((attribute) => (
                  <TableRow key={attribute.id} hover>
                    <TableCell sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {attribute.displayOrder}
                    </TableCell>
                    <TableCell>
                      <Typography sx={{ fontSize: 14, fontWeight: 600 }}>
                        {attribute.label}
                      </Typography>
                      <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11.5 }}>
                        {attribute.slug}
                      </Typography>
                    </TableCell>
                    <TableCell>
                      {attribute.categoryName ?? (
                        <Chip size="small" variant="outlined" label="Every product"
                              sx={{ height: 18, fontSize: 10 }} />
                      )}
                    </TableCell>
                    <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                      {attribute.productCount}
                    </TableCell>
                    <TableCell align="center">
                      <Switch
                        size="small"
                        checked={attribute.active}
                        onChange={(_, active) => {
                          setForm({
                            label: attribute.label,
                            slug: attribute.slug,
                            categoryId:
                              attribute.categoryId === null ? '' : String(attribute.categoryId),
                            helpText: attribute.helpText ?? '',
                            displayOrder: String(attribute.displayOrder),
                            active,
                          });
                          setEditing(attribute);
                          save.mutate();
                        }}
                      />
                    </TableCell>
                    <TableCell align="right">
                      <Tooltip title="Edit">
                        <IconButton size="small" onClick={() => startEdit(attribute)}>
                          <EditIcon fontSize="small" />
                        </IconButton>
                      </Tooltip>
                      <Tooltip
                        title={
                          attribute.productCount > 0
                            ? 'Products have answered this — deactivate instead'
                            : 'Remove'
                        }
                      >
                        <span>
                          <IconButton
                            size="small"
                            disabled={attribute.productCount > 0 || remove.isPending}
                            onClick={() => remove.mutate(attribute.id)}
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

      <Alert severity="info" variant="outlined" sx={{ mt: 2.5 }}>
        <AlertTitle>Selling something new</AlertTitle>
        Create a category for it, give it a name for the creator field if one
        applies — &ldquo;Author&rdquo; for books, &ldquo;Artist&rdquo; for records — then define its
        attributes here. Products in that category are asked for exactly those.
      </Alert>
    </Box>
  );
}

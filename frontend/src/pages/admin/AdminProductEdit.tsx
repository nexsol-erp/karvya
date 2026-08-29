import { useEffect, useRef, useState } from 'react';
import { Link as RouterLink, useNavigate, useParams } from 'react-router-dom';
import { useQuery, useQueryClient } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Tooltip from '@mui/material/Tooltip';
import Chip from '@mui/material/Chip';
import Alert from '@mui/material/Alert';
import AlertTitle from '@mui/material/AlertTitle';
import Divider from '@mui/material/Divider';
import Skeleton from '@mui/material/Skeleton';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import ArrowUpwardIcon from '@mui/icons-material/ArrowUpward';
import ArrowDownwardIcon from '@mui/icons-material/ArrowDownward';
import StarIcon from '@mui/icons-material/Star';
import StarOutlineIcon from '@mui/icons-material/StarBorder';
import DeleteOutlineIcon from '@mui/icons-material/DeleteOutlined';
import UploadIcon from '@mui/icons-material/UploadFile';

import { SEOHead } from '../../components/common/SEOHead';
import { ProductImage } from '../../components/common/ProductImage';
import { NumberField } from '../../components/common/NumberField';
import { ApiError } from '../../api/client';
import {
  adminKeys,
  deleteProductImage,
  getProduct,
  listCategories,
  listVendors,
  reorderProductImages,
  saveProduct,
  uploadProductImage,
} from '../../api/admin';
import type { AdminProductDetail, AdminProductImage, ProductStatus } from '../../api/admin';
import { palette } from '../../theme';

const STATUSES: ProductStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'];

/** Formats the server accepts. WebP is absent: nothing here can resize it. */
const ACCEPTED = 'image/jpeg,image/png';

const BLANK = {
  sku: '',
  name: '',
  slug: '',
  categoryId: '',
  shortDescription: '',
  description: '',
  price: '',
  material: '',
  colour: '',
  dimensions: '',
  careInstructions: '',
  stockQuantity: '0',
  lowStockThreshold: '3',
  featured: false,
  status: 'DRAFT' as ProductStatus,
  placeholderContent: false,
  vendorId: '',
  vendorPrice: '',
  vendorDeliveryTime: '',
};

type Form = typeof BLANK;

function toForm(product: AdminProductDetail): Form {
  return {
    sku: product.sku,
    name: product.name,
    slug: product.slug,
    categoryId: String(product.categoryId),
    shortDescription: product.shortDescription ?? '',
    description: product.description ?? '',
    price: String(product.price),
    material: product.material ?? '',
    colour: product.colour ?? '',
    dimensions: product.dimensions ?? '',
    careInstructions: product.careInstructions ?? '',
    stockQuantity: String(product.stockQuantity),
    lowStockThreshold: String(product.lowStockThreshold),
    featured: product.featured,
    status: product.status,
    placeholderContent: product.placeholderContent,
    vendorId: product.vendorId == null ? '' : String(product.vendorId),
    vendorPrice: product.vendorPrice == null ? '' : String(product.vendorPrice),
    vendorDeliveryTime: product.vendorDeliveryTime ?? '',
  };
}

/**
 * Create and edit a product, and manage its photographs.
 *
 * <p>The gallery only appears once the product exists. Photographs are attached
 * to a product id, so there is nothing to attach them to until it has been
 * saved - offering the control first and then losing the files would be worse
 * than saying so.
 */
export function AdminProductEdit() {
  const { id } = useParams();
  const productId = id ? Number(id) : null;
  const isNew = productId === null;

  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const fileInput = useRef<HTMLInputElement>(null);

  const [form, setForm] = useState<Form>(BLANK);
  const [version, setVersion] = useState<number | null>(null);
  const [images, setImages] = useState<AdminProductImage[]>([]);
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
  const [error, setError] = useState<string | null>(null);
  const [notice, setNotice] = useState<string | null>(null);
  const [saving, setSaving] = useState(false);
  const [busyImage, setBusyImage] = useState(false);
  const [altText, setAltText] = useState('');

  const categories = useQuery({ queryKey: adminKeys.categories, queryFn: listCategories });
  const vendors = useQuery({ queryKey: adminKeys.vendors, queryFn: listVendors });
  const existing = useQuery({
    queryKey: adminKeys.product(productId ?? 0),
    queryFn: () => getProduct(productId as number),
    enabled: !isNew,
  });

  useEffect(() => {
    if (!existing.data) return;
    setForm(toForm(existing.data));
    setVersion(existing.data.version);
    setImages(existing.data.images);
  }, [existing.data]);

  const set =
    (key: keyof Form) =>
    (event: React.ChangeEvent<HTMLInputElement>) =>
      setForm((prev) => ({ ...prev, [key]: event.target.value }));

  const toggle = (key: keyof Form) => (_: unknown, checked: boolean) =>
    setForm((prev) => ({ ...prev, [key]: checked }));

  /** Keeps the freshly returned detail and the cached list in step. */
  function absorb(detail: AdminProductDetail) {
    setVersion(detail.version);
    setImages(detail.images);
    queryClient.setQueryData(adminKeys.product(detail.id), detail);
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

  async function handleSave(event: React.FormEvent) {
    event.preventDefault();
    setError(null);
    setNotice(null);
    setFieldErrors({});
    setSaving(true);

    try {
      const saved = await saveProduct(productId, {
        ...form,
        categoryId: form.categoryId === '' ? null : Number(form.categoryId),
        stockQuantity: Number(form.stockQuantity),
        lowStockThreshold: Number(form.lowStockThreshold),
        // empty means "we make this ourselves", which has to be expressible
        vendorId: form.vendorId === '' ? null : Number(form.vendorId),
        vendorPrice: form.vendorPrice.trim() === '' ? null : form.vendorPrice.trim(),
        // the slug is derived from the name when left empty
        slug: form.slug.trim(),
        // carries the optimistic lock, so a save over someone else's is refused
        version,
      });

      absorb(saved);

      if (isNew) {
        // straight to the edit screen, where photographs can be added
        navigate(`/admin/products/${saved.id}`, { replace: true });
        setNotice('Product created. You can add photographs below.');
      } else {
        setNotice('Saved.');
      }
    } catch (err) {
      report(err, 'That could not be saved. Please try again.');
    } finally {
      setSaving(false);
    }
  }

  async function handleUpload(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // clear immediately so choosing the same file again still fires a change
    event.target.value = '';
    if (!file || productId === null) return;

    setError(null);
    setNotice(null);
    setBusyImage(true);
    try {
      absorb(await uploadProductImage(productId, file, altText.trim()));
      setAltText('');
      setNotice('Photograph added.');
    } catch (err) {
      report(err, 'That photograph could not be uploaded.');
    } finally {
      setBusyImage(false);
    }
  }

  async function applyOrder(next: AdminProductImage[], primaryId: number) {
    if (productId === null) return;
    setError(null);
    setBusyImage(true);
    try {
      absorb(await reorderProductImages(productId, next.map((image) => image.id), primaryId));
    } catch (err) {
      report(err, 'The gallery order could not be changed.');
    } finally {
      setBusyImage(false);
    }
  }

  function move(index: number, delta: number) {
    const next = [...images];
    const target = index + delta;
    if (target < 0 || target >= next.length) return;
    [next[index], next[target]] = [next[target], next[index]];
    applyOrder(next, next.find((image) => image.primary)?.id ?? next[0].id);
  }

  async function removeImage(imageId: number) {
    if (productId === null) return;
    setError(null);
    setBusyImage(true);
    try {
      absorb(await deleteProductImage(productId, imageId));
      setNotice('Photograph removed.');
    } catch (err) {
      report(err, 'That photograph could not be removed.');
    } finally {
      setBusyImage(false);
    }
  }

  if (!isNew && existing.isPending) {
    return (
      <Box>
        <Skeleton height={44} width={260} />
        <Skeleton variant="rounded" height={420} sx={{ mt: 2 }} />
      </Box>
    );
  }

  if (!isNew && existing.isError) {
    return (
      <Alert severity="error">
        That product could not be loaded.{' '}
        <RouterLink to="/admin/products">Back to products</RouterLink>
      </Alert>
    );
  }

  const heading = isNew ? 'New product' : form.name || 'Product';

  return (
    <Box>
      <SEOHead title={heading} path="/admin/products" noIndex />

      <Stack
        direction="row"
        sx={{ justifyContent: 'space-between', alignItems: 'flex-start', gap: 2, mb: 2.5 }}
      >
        <Box sx={{ minWidth: 0 }}>
          <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
            {heading}
          </Typography>
          {!isNew && (
            <Typography variant="body2" sx={{ fontFamily: 'monospace' }}>
              {form.sku}
            </Typography>
          )}
        </Box>
        <Button component={RouterLink} to="/admin/products" size="small">
          Back to products
        </Button>
      </Stack>

      {error && (
        <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>
          {error}
        </Alert>
      )}
      {notice && (
        <Alert severity="success" sx={{ mb: 2 }} onClose={() => setNotice(null)}>
          {notice}
        </Alert>
      )}

      <Grid container spacing={2.5}>
        {/* ---- details ---- */}
        <Grid size={{ xs: 12, lg: 7 }}>
          <Card sx={{ p: 2.5 }} component="form" onSubmit={handleSave} noValidate>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 2 }}>
              Details
            </Typography>

            <Stack spacing={2}>
              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    label="SKU" value={form.sku} onChange={set('sku')}
                    error={Boolean(fieldErrors.sku)} helperText={fieldErrors.sku}
                    required fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    select label="Category" value={form.categoryId} onChange={set('categoryId')}
                    error={Boolean(fieldErrors.categoryId)} helperText={fieldErrors.categoryId}
                    required fullWidth
                  >
                    {(categories.data ?? []).map((category) => (
                      <MenuItem key={category.id} value={String(category.id)}>
                        {category.name}
                      </MenuItem>
                    ))}
                  </TextField>
                </Grid>
              </Grid>

              <TextField
                label="Name" value={form.name} onChange={set('name')}
                error={Boolean(fieldErrors.name)} helperText={fieldErrors.name}
                required fullWidth
              />

              <TextField
                label="Slug" value={form.slug} onChange={set('slug')}
                error={Boolean(fieldErrors.slug)}
                helperText={fieldErrors.slug ?? 'Leave empty to derive it from the name'}
                fullWidth
              />

              <TextField
                label="Short description" value={form.shortDescription}
                onChange={set('shortDescription')}
                error={Boolean(fieldErrors.shortDescription)}
                helperText={fieldErrors.shortDescription ?? 'One line, shown on cards'}
                fullWidth
              />

              <TextField
                label="Description" value={form.description} onChange={set('description')}
                error={Boolean(fieldErrors.description)} helperText={fieldErrors.description}
                multiline minRows={4} fullWidth
              />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 4 }}>
                  <NumberField
                    label="Price" value={form.price} decimal
                    onChange={(v) => setForm((p) => ({ ...p, price: v }))}
                    error={Boolean(fieldErrors.price)} helperText={fieldErrors.price}
                    required fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                  <NumberField
                    label="Stock" value={form.stockQuantity}
                    onChange={(v) => setForm((p) => ({ ...p, stockQuantity: v }))}
                    error={Boolean(fieldErrors.stockQuantity)} helperText={fieldErrors.stockQuantity}
                    required fullWidth
                  />
                </Grid>
                <Grid size={{ xs: 6, sm: 4 }}>
                  <NumberField
                    label="Low stock at" value={form.lowStockThreshold}
                    onChange={(v) => setForm((p) => ({ ...p, lowStockThreshold: v }))}
                    error={Boolean(fieldErrors.lowStockThreshold)}
                    helperText={fieldErrors.lowStockThreshold}
                    required fullWidth
                  />
                </Grid>
              </Grid>

              <Divider />

              <Grid container spacing={2}>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField label="Material" value={form.material} onChange={set('material')} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField label="Colour" value={form.colour} onChange={set('colour')} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField label="Dimensions" value={form.dimensions} onChange={set('dimensions')} fullWidth />
                </Grid>
                <Grid size={{ xs: 12, sm: 6 }}>
                  <TextField
                    select label="Status" value={form.status} onChange={set('status')} fullWidth
                  >
                    {STATUSES.map((status) => (
                      <MenuItem key={status} value={status}>{status}</MenuItem>
                    ))}
                  </TextField>
                </Grid>
              </Grid>

              <TextField
                label="Care instructions" value={form.careInstructions}
                onChange={set('careInstructions')} multiline minRows={2} fullWidth
              />

              <Divider />

              <Box>
                <Typography variant="h6" component="h3" sx={{ fontSize: '0.95rem' }}>
                  Supplier
                </Typography>
                <Typography variant="body2" sx={{ mb: 1.5 }}>
                  Internal only. None of this reaches the shop — it appears on the
                  order page so you know who to reorder from.
                </Typography>

                <Grid container spacing={2}>
                  <Grid size={{ xs: 12, sm: 6 }}>
                    <TextField
                      select label="Bought from" value={form.vendorId}
                      onChange={set('vendorId')} fullWidth
                      helperText={
                        (vendors.data?.length ?? 0) === 0
                          ? 'No suppliers yet — add one under Suppliers'
                          : 'Leave empty if you make this yourself'
                      }
                    >
                      <MenuItem value="">We make it ourselves</MenuItem>
                      {(vendors.data ?? [])
                        // an inactive supplier stays selectable on products that
                        // already use it, but is not offered for new ones
                        .filter((v) => v.active || String(v.id) === form.vendorId)
                        .map((vendor) => (
                          <MenuItem key={vendor.id} value={String(vendor.id)}>
                            {vendor.name}{vendor.active ? '' : ' (inactive)'}
                          </MenuItem>
                        ))}
                    </TextField>
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <NumberField
                      label="Price paid" value={form.vendorPrice} decimal
                      onChange={(v) => setForm((p) => ({ ...p, vendorPrice: v }))}
                      error={Boolean(fieldErrors.vendorPrice)}
                      helperText={fieldErrors.vendorPrice ?? 'What it costs you'}
                      fullWidth
                    />
                  </Grid>
                  <Grid size={{ xs: 6, sm: 3 }}>
                    <TextField
                      label="Delivery time" value={form.vendorDeliveryTime}
                      onChange={set('vendorDeliveryTime')}
                      error={Boolean(fieldErrors.vendorDeliveryTime)}
                      helperText={fieldErrors.vendorDeliveryTime ?? 'Only if it differs'}
                      fullWidth
                    />
                  </Grid>
                </Grid>
              </Box>

              <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1} sx={{ flexWrap: 'wrap' }}>
                <FormControlLabel
                  control={<Switch checked={form.featured} onChange={toggle('featured')} />}
                  label="Featured on the home page"
                />
                <FormControlLabel
                  control={
                    <Switch checked={form.placeholderContent} onChange={toggle('placeholderContent')} />
                  }
                  label="Copy is still placeholder"
                />
              </Stack>

              <Box>
                <Button type="submit" variant="contained" size="large" disabled={saving}>
                  {saving ? 'Saving…' : isNew ? 'Create product' : 'Save changes'}
                </Button>
              </Box>
            </Stack>
          </Card>
        </Grid>

        {/* ---- photographs ---- */}
        <Grid size={{ xs: 12, lg: 5 }}>
          <Card sx={{ p: 2.5 }}>
            <Typography variant="h6" component="h2" sx={{ fontSize: '1rem', mb: 0.5 }}>
              Photographs
            </Typography>
            <Typography variant="body2" sx={{ mb: 2 }}>
              {images.length === 0
                ? 'None yet.'
                : `${images.length} — the starred one leads the gallery.`}
            </Typography>

            {isNew ? (
              <Alert severity="info">
                <AlertTitle>Save the product first</AlertTitle>
                Photographs are stored against the product, so there is nothing to
                attach them to until it exists.
              </Alert>
            ) : (
              <>
                <Stack spacing={1.5} sx={{ mb: 2 }}>
                  <TextField
                    label="Description for screen readers"
                    value={altText}
                    onChange={(e) => setAltText(e.target.value)}
                    helperText="Describe what the photograph shows. Defaults to the product name."
                    size="small"
                    fullWidth
                  />
                  <Box>
                    <input
                      ref={fileInput}
                      type="file"
                      accept={ACCEPTED}
                      onChange={handleUpload}
                      hidden
                    />
                    <Button
                      variant="outlined"
                      startIcon={<UploadIcon />}
                      onClick={() => fileInput.current?.click()}
                      disabled={busyImage}
                    >
                      {busyImage ? 'Working…' : 'Add a photograph'}
                    </Button>
                    <Typography variant="body2" sx={{ mt: 0.75 }}>
                      JPEG or PNG, at least 200px a side, up to 8&nbsp;MB.
                    </Typography>
                  </Box>
                </Stack>

                <Stack spacing={1.5} divider={<Divider flexItem />}>
                  {images.map((image, index) => (
                    <Stack
                      key={image.id}
                      direction="row"
                      spacing={1.5}
                      sx={{ alignItems: 'center' }}
                    >
                      <Box sx={{ width: 64, flexShrink: 0, borderRadius: 1, overflow: 'hidden' }}>
                        <ProductImage
                          image={{
                            key: image.storageKey,
                            alt: image.altText,
                            width: image.width,
                            height: image.height,
                            formats: image.formats,
                          }}
                          aspectRatio="1 / 1"
                          sizes="64px"
                        />
                      </Box>

                      <Box sx={{ minWidth: 0, flexGrow: 1 }}>
                        <Typography sx={{ fontSize: 13.5 }} noWrap>
                          {image.altText}
                        </Typography>
                        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', mt: 0.25 }}>
                          {image.primary && (
                            <Chip
                              size="small"
                              label="Leads gallery"
                              sx={{ height: 18, fontSize: 10, color: palette.forest }}
                              variant="outlined"
                            />
                          )}
                          <Typography variant="body2" sx={{ fontSize: 11.5 }}>
                            {image.width ?? '?'}×{image.height ?? '?'}
                          </Typography>
                        </Stack>
                      </Box>

                      <Stack direction="row">
                        <Tooltip title={image.primary ? 'Already leads the gallery' : 'Make it lead'}>
                          <span>
                            <IconButton
                              size="small"
                              disabled={busyImage || image.primary}
                              onClick={() => applyOrder(images, image.id)}
                            >
                              {image.primary ? (
                                <StarIcon fontSize="small" sx={{ color: palette.terracotta }} />
                              ) : (
                                <StarOutlineIcon fontSize="small" />
                              )}
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Move up">
                          <span>
                            <IconButton
                              size="small"
                              disabled={busyImage || index === 0}
                              onClick={() => move(index, -1)}
                            >
                              <ArrowUpwardIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Move down">
                          <span>
                            <IconButton
                              size="small"
                              disabled={busyImage || index === images.length - 1}
                              onClick={() => move(index, 1)}
                            >
                              <ArrowDownwardIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                        <Tooltip title="Remove">
                          <span>
                            <IconButton
                              size="small"
                              disabled={busyImage}
                              onClick={() => removeImage(image.id)}
                            >
                              <DeleteOutlineIcon fontSize="small" />
                            </IconButton>
                          </span>
                        </Tooltip>
                      </Stack>
                    </Stack>
                  ))}
                </Stack>
              </>
            )}
          </Card>
        </Grid>
      </Grid>
    </Box>
  );
}

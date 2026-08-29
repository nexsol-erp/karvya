import { useState } from 'react';
import { useSearchParams } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient, keepPreviousData } from '@tanstack/react-query';
import Box from '@mui/material/Box';
import Card from '@mui/material/Card';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
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

import { SEOHead } from '../../components/common/SEOHead';
import { ProductImage } from '../../components/common/ProductImage';
import { EmptyState } from '../../components/common/EmptyState';
import { adminKeys, listProducts, setProductStatus } from '../../api/admin';
import type { ProductStatus } from '../../api/admin';
import { ApiError } from '../../api/client';
import { formatMoney } from '../../lib/format';
import { config } from '../../config';
import { palette } from '../../theme';

const STATUSES: ProductStatus[] = ['DRAFT', 'ACTIVE', 'INACTIVE', 'ARCHIVED'];

const STATUS_TONE: Record<ProductStatus, string> = {
  DRAFT: palette.stone,
  ACTIVE: palette.forest,
  INACTIVE: palette.coir,
  ARCHIVED: palette.charcoalMuted,
};

export function AdminProducts() {
  const [params, setParams] = useSearchParams();
  const queryClient = useQueryClient();
  const [error, setError] = useState<string | null>(null);

  const q = params.get('q') ?? '';
  const status = params.get('status') ?? '';
  const page = Number(params.get('page') ?? 0);
  const [searchDraft, setSearchDraft] = useState(q);

  const products = useQuery({
    queryKey: adminKeys.products(q, status, page),
    queryFn: () => listProducts(q, status, page),
    placeholderData: keepPreviousData,
  });

  const statusMutation = useMutation({
    mutationFn: ({ id, next }: { id: number; next: ProductStatus }) => setProductStatus(id, next),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['admin', 'products'] });
      queryClient.invalidateQueries({ queryKey: adminKeys.dashboard });
      setError(null);
    },
    onError: (err) =>
      setError(err instanceof ApiError ? err.message : 'That change could not be saved.'),
  });

  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    if (key !== 'page') next.delete('page');
    setParams(next, { replace: true });
  };

  const rows = products.data?.content ?? [];
  const money = (value: string | number) => formatMoney(value, config.currency, config.locale);

  return (
    <Box>
      <SEOHead title="Products" path="/admin/products" noIndex />

      <Typography variant="h1" sx={{ fontSize: '1.9rem' }}>
        Products
      </Typography>
      <Typography variant="body2" sx={{ mb: 2.5 }}>
        {products.isPending ? 'Loading…' : `${products.data?.totalElements ?? 0} in the catalogue`}
      </Typography>

      {error && <Alert severity="error" sx={{ mb: 2 }} onClose={() => setError(null)}>{error}</Alert>}

      <Card sx={{ p: 2, mb: 2.5 }}>
        <Box
          component="form"
          onSubmit={(e) => { e.preventDefault(); setFilter('q', searchDraft.trim()); }}
        >
          <Stack direction={{ xs: 'column', sm: 'row' }} spacing={1.5} sx={{ alignItems: 'center' }}>
            <TextField
              size="small" label="Search" placeholder="Name or description"
              value={searchDraft} onChange={(e) => setSearchDraft(e.target.value)}
              sx={{ flexGrow: 1, width: { xs: '100%', sm: 'auto' } }}
            />
            <TextField
              select size="small" label="Status" value={status}
              onChange={(e) => setFilter('status', e.target.value)}
              sx={{ minWidth: 160, width: { xs: '100%', sm: 'auto' } }}
            >
              <MenuItem value="">Any status</MenuItem>
              {STATUSES.map((s) => <MenuItem key={s} value={s}>{s}</MenuItem>)}
            </TextField>
            <Button type="submit" variant="contained" size="small">Find</Button>
          </Stack>
        </Box>
      </Card>

      <Card sx={{ overflow: 'hidden' }}>
        {products.isPending ? (
          <Box sx={{ p: 2 }}>
            {Array.from({ length: 5 }).map((_, i) => <Skeleton key={i} height={56} />)}
          </Box>
        ) : rows.length === 0 ? (
          <Box sx={{ p: 3 }}>
            <EmptyState title="No products match" description="Try a different search or status." />
          </Box>
        ) : (
          <>
            <Box sx={{ overflowX: 'auto' }}>
              <Table size="small">
                <TableHead>
                  <TableRow>
                    <TableCell sx={{ width: 56 }} />
                    <TableCell>Product</TableCell>
                    <TableCell align="right">Price</TableCell>
                    <TableCell align="right">Stock</TableCell>
                    <TableCell>Status</TableCell>
                    <TableCell align="right">Change to</TableCell>
                  </TableRow>
                </TableHead>
                <TableBody>
                  {rows.map((product) => (
                    <TableRow key={product.id} hover>
                      <TableCell>
                        <Box sx={{ width: 40, borderRadius: 1, overflow: 'hidden' }}>
                          <ProductImage
                            image={product.primaryImageKey
                              ? { key: product.primaryImageKey, alt: product.name, width: null, height: null }
                              : null}
                            aspectRatio="1 / 1" sizes="40px"
                          />
                        </Box>
                      </TableCell>
                      <TableCell>
                        <Typography sx={{ fontSize: 14, fontWeight: 600 }}>{product.name}</Typography>
                        <Stack direction="row" spacing={0.75} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
                          <Typography variant="body2" sx={{ fontFamily: 'monospace', fontSize: 11.5 }}>
                            {product.sku}
                          </Typography>
                          {product.featured && (
                            <Chip size="small" label="Featured" variant="outlined" sx={{ height: 18, fontSize: 10 }} />
                          )}
                          {product.placeholderContent && (
                            <Chip
                              size="small" label="Placeholder copy"
                              sx={{ height: 18, fontSize: 10, bgcolor: 'rgba(163,59,46,0.1)', color: palette.terracotta }}
                            />
                          )}
                        </Stack>
                      </TableCell>
                      <TableCell align="right" sx={{ fontVariantNumeric: 'tabular-nums' }}>
                        {money(product.price)}
                      </TableCell>
                      <TableCell
                        align="right"
                        sx={{
                          fontVariantNumeric: 'tabular-nums',
                          fontWeight: product.lowStock ? 700 : 400,
                          color: product.lowStock ? palette.terracotta : 'inherit',
                        }}
                      >
                        {product.stockQuantity}
                      </TableCell>
                      <TableCell>
                        <Chip
                          size="small" label={product.status}
                          sx={{
                            fontWeight: 600, fontSize: 11,
                            color: STATUS_TONE[product.status],
                            border: `1px solid ${STATUS_TONE[product.status]}`,
                            bgcolor: 'transparent',
                          }}
                        />
                      </TableCell>
                      <TableCell align="right">
                        <TextField
                          select size="small" value=""
                          onChange={(e) =>
                            statusMutation.mutate({ id: product.id, next: e.target.value as ProductStatus })}
                          sx={{ minWidth: 118 }}
                          slotProps={{ select: { displayEmpty: true } }}
                        >
                          <MenuItem value="" disabled>Set status</MenuItem>
                          {STATUSES.filter((s) => s !== product.status).map((s) => (
                            <MenuItem key={s} value={s}>{s}</MenuItem>
                          ))}
                        </TextField>
                      </TableCell>
                    </TableRow>
                  ))}
                </TableBody>
              </Table>
            </Box>

            <TablePagination
              component="div"
              count={products.data?.totalElements ?? 0}
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

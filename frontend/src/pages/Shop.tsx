import { useMemo, useState } from 'react';
import { useSearchParams, useParams } from 'react-router-dom';
import { useQuery, keepPreviousData } from '@tanstack/react-query';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Grid from '@mui/material/Grid';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import TextField from '@mui/material/TextField';
import MenuItem from '@mui/material/MenuItem';
import Button from '@mui/material/Button';
import FormControlLabel from '@mui/material/FormControlLabel';
import Switch from '@mui/material/Switch';
import Alert from '@mui/material/Alert';
import InputAdornment from '@mui/material/InputAdornment';
import SearchIcon from '@mui/icons-material/Search';

import { catalogKeys, searchProducts, getCategories } from '../api/catalog';
import { ProductGrid } from '../components/catalog/ProductGrid';
import { EmptyState } from '../components/common/EmptyState';
import { SEOHead } from '../components/common/SEOHead';
import type { ProductSort, ProductSummary } from '../api/types';

const SORTS: { value: ProductSort; label: string }[] = [
  { value: 'RELEVANCE', label: 'Relevance' },
  { value: 'NEWEST', label: 'Newest' },
  { value: 'PRICE_ASC', label: 'Price: low to high' },
  { value: 'PRICE_DESC', label: 'Price: high to low' },
];

const PAGE_SIZE = 12;

/**
 * The shop.
 *
 * <p>Every filter lives in the URL rather than in component state, so a
 * filtered view can be linked, bookmarked and reached by the back button.
 * Pages accumulate into one list for a load-more flow, which keeps the reader
 * in place instead of jumping them to the top on every page change.
 */
export function Shop() {
  const { categorySlug } = useParams();
  const [params, setParams] = useSearchParams();
  const [pagesLoaded, setPagesLoaded] = useState(1);

  const q = params.get('q') ?? '';
  const sort = (params.get('sort') as ProductSort | null) ?? 'RELEVANCE';
  const inStock = params.get('inStock') === 'true';
  const minPrice = params.get('minPrice') ?? '';
  const maxPrice = params.get('maxPrice') ?? '';
  const category = categorySlug ?? params.get('category') ?? '';

  const [searchDraft, setSearchDraft] = useState(q);

  const query = useMemo(
    () => ({
      q: q || undefined,
      category: category || undefined,
      sort,
      inStock: inStock || undefined,
      minPrice: minPrice ? Number(minPrice) : undefined,
      maxPrice: maxPrice ? Number(maxPrice) : undefined,
      page: 0,
      size: PAGE_SIZE * pagesLoaded,
    }),
    [q, category, sort, inStock, minPrice, maxPrice, pagesLoaded],
  );

  const products = useQuery({
    queryKey: catalogKeys.products(query),
    queryFn: () => searchProducts(query),
    placeholderData: keepPreviousData,
  });

  const categories = useQuery({
    queryKey: catalogKeys.categories,
    queryFn: getCategories,
  });

  /** Writes one filter to the URL and resets accumulated pages. */
  const setFilter = (key: string, value: string) => {
    const next = new URLSearchParams(params);
    if (value) next.set(key, value);
    else next.delete(key);
    setParams(next, { replace: true });
    setPagesLoaded(1);
  };

  const clearAll = () => {
    setSearchDraft('');
    setParams(new URLSearchParams(), { replace: true });
    setPagesLoaded(1);
  };

  const items: ProductSummary[] = products.data?.content ?? [];
  const total = products.data?.totalElements ?? 0;
  const hasMore = products.data?.hasNext ?? false;
  const filtersApplied = Boolean(q || category || inStock || minPrice || maxPrice);

  const activeCategory = categories.data?.find((c) => c.slug === category);
  const heading = activeCategory?.name ?? 'Shop';

  return (
    <Container maxWidth="lg" sx={{ py: { xs: 4, md: 7 } }}>
      <SEOHead
        title={heading}
        description={
          activeCategory?.description ??
          'Browse handwoven coir bird houses and nesting shelters.'
        }
        path={categorySlug ? `/shop/${categorySlug}` : '/shop'}
      />

      <Typography variant="h1" sx={{ fontSize: { xs: '2.2rem', md: '2.9rem' }, mb: 1 }}>
        {heading}
      </Typography>
      <Typography variant="body2" sx={{ mb: 4 }}>
        {products.isPending
          ? 'Loading pieces…'
          : `${total} ${total === 1 ? 'piece' : 'pieces'}`}
      </Typography>

      {/* ---------------- filters ---------------- */}
      <Box
        component="form"
        onSubmit={(e) => {
          e.preventDefault();
          setFilter('q', searchDraft.trim());
        }}
        sx={{
          p: { xs: 2, md: 2.5 },
          mb: 4,
          borderRadius: 3,
          border: 1,
          borderColor: 'divider',
          bgcolor: 'background.paper',
        }}
      >
        <Grid container spacing={2} sx={{ alignItems: 'center' }}>
          <Grid size={{ xs: 12, md: 4 }}>
            <TextField
              fullWidth
              size="small"
              label="Search"
              value={searchDraft}
              onChange={(e) => setSearchDraft(e.target.value)}
              slotProps={{
                input: {
                  endAdornment: (
                    <InputAdornment position="end">
                      <SearchIcon fontSize="small" color="disabled" />
                    </InputAdornment>
                  ),
                },
              }}
            />
          </Grid>

          {!categorySlug && (
            <Grid size={{ xs: 12, sm: 6, md: 2.5 }}>
              <TextField
                select
                fullWidth
                size="small"
                label="Category"
                value={category}
                onChange={(e) => setFilter('category', e.target.value)}
              >
                <MenuItem value="">All categories</MenuItem>
                {categories.data?.map((c) => (
                  <MenuItem key={c.slug} value={c.slug}>
                    {c.name}
                  </MenuItem>
                ))}
              </TextField>
            </Grid>
          )}

          <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
            <TextField
              fullWidth
              size="small"
              label="Min price"
              type="number"
              value={minPrice}
              onChange={(e) => setFilter('minPrice', e.target.value)}
              slotProps={{ htmlInput: { min: 0, inputMode: 'numeric' } }}
            />
          </Grid>

          <Grid size={{ xs: 6, sm: 3, md: 1.5 }}>
            <TextField
              fullWidth
              size="small"
              label="Max price"
              type="number"
              value={maxPrice}
              onChange={(e) => setFilter('maxPrice', e.target.value)}
              slotProps={{ htmlInput: { min: 0, inputMode: 'numeric' } }}
            />
          </Grid>

          <Grid size={{ xs: 12, sm: 6, md: 2.5 }}>
            <TextField
              select
              fullWidth
              size="small"
              label="Sort by"
              value={sort}
              onChange={(e) => setFilter('sort', e.target.value)}
            >
              {SORTS.map((s) => (
                <MenuItem key={s.value} value={s.value}>
                  {s.label}
                </MenuItem>
              ))}
            </TextField>
          </Grid>

          <Grid size={{ xs: 12 }}>
            <Stack direction="row" spacing={2} sx={{ alignItems: 'center', flexWrap: 'wrap' }}>
              <FormControlLabel
                control={
                  <Switch
                    checked={inStock}
                    onChange={(e) => setFilter('inStock', e.target.checked ? 'true' : '')}
                  />
                }
                label="In stock only"
              />
              <Button type="submit" variant="contained" size="small">
                Search
              </Button>
              {filtersApplied && (
                <Button onClick={clearAll} size="small">
                  Clear filters
                </Button>
              )}
            </Stack>
          </Grid>
        </Grid>
      </Box>

      {/* ---------------- results ---------------- */}
      {products.isError && (
        <Alert severity="error" sx={{ mb: 3 }}>
          The catalogue could not be loaded. Please refresh to try again.
        </Alert>
      )}

      {!products.isPending && items.length === 0 ? (
        <EmptyState
          title="Nothing matches those filters"
          description={
            filtersApplied
              ? 'Try widening the price range, or clear the filters to see everything.'
              : 'There are no pieces in the catalogue yet.'
          }
          actionLabel={filtersApplied ? 'Clear filters' : undefined}
          onAction={filtersApplied ? clearAll : undefined}
        />
      ) : (
        <ProductGrid products={items} loading={products.isPending} skeletonCount={PAGE_SIZE} />
      )}

      {hasMore && (
        <Stack sx={{ alignItems: 'center', mt: 5 }}>
          <Button
            variant="outlined"
            size="large"
            onClick={() => setPagesLoaded((n) => n + 1)}
            disabled={products.isFetching}
          >
            {products.isFetching ? 'Loading…' : 'Load more'}
          </Button>
        </Stack>
      )}
    </Container>
  );
}

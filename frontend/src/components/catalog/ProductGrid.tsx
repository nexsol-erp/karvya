import Grid from '@mui/material/Grid';
import Card from '@mui/material/Card';
import Skeleton from '@mui/material/Skeleton';
import Stack from '@mui/material/Stack';
import Box from '@mui/material/Box';

import { ProductCard } from './ProductCard';
import type { ProductSummary } from '../../api/types';

interface Props {
  products: ProductSummary[];
  loading?: boolean;
  /** How many skeleton cards to show while the first page loads. */
  skeletonCount?: number;
  /** Images in the first row load eagerly; this says how many that is. */
  priorityCount?: number;
}

function CardSkeleton() {
  return (
    <Card sx={{ height: '100%' }}>
      <Skeleton variant="rectangular" sx={{ aspectRatio: '4 / 5', height: 'auto', width: '100%' }} />
      <Stack spacing={1} sx={{ p: 2.25 }}>
        <Skeleton width="75%" height={26} />
        <Skeleton width="95%" height={18} />
        <Skeleton width="55%" height={18} />
        <Box sx={{ height: 8 }} />
        <Stack direction="row" sx={{ justifyContent: 'space-between' }}>
          <Skeleton width={82} height={26} />
          <Skeleton width={72} height={26} />
        </Stack>
      </Stack>
    </Card>
  );
}

/**
 * The catalogue grid. Skeletons mirror the real card's proportions exactly, so
 * nothing jumps when the data arrives.
 */
export function ProductGrid({
  products,
  loading = false,
  skeletonCount = 8,
  priorityCount = 4,
}: Props) {
  if (loading) {
    return (
      <Grid container spacing={{ xs: 2, md: 3 }}>
        {Array.from({ length: skeletonCount }).map((_, i) => (
          <Grid key={i} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
            <CardSkeleton />
          </Grid>
        ))}
      </Grid>
    );
  }

  return (
    <Grid container spacing={{ xs: 2, md: 3 }}>
      {products.map((product, index) => (
        <Grid key={product.id} size={{ xs: 12, sm: 6, md: 4, lg: 3 }}>
          <ProductCard product={product} priority={index < priorityCount} />
        </Grid>
      ))}
    </Grid>
  );
}

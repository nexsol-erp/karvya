import Card from '@mui/material/Card';
import CardActionArea from '@mui/material/CardActionArea';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Typography from '@mui/material/Typography';
import { Link as RouterLink } from 'react-router-dom';

import { ProductImage } from '../common/ProductImage';
import { StockBadge } from '../common/StockBadge';
import { formatMoney } from '../../lib/format';
import { config } from '../../config';
import type { ProductSummary } from '../../api/types';

interface Props {
  product: ProductSummary;
  /** Set on the first row so those images are not lazily loaded. */
  priority?: boolean;
}

/** One product on the shop grid. The whole card is a single link target. */
export function ProductCard({ product, priority = false }: Props) {
  return (
    <Card sx={{ height: '100%', display: 'flex', flexDirection: 'column', overflow: 'hidden' }}>
      <CardActionArea
        component={RouterLink}
        to={`/product/${product.slug}`}
        sx={{ height: '100%', display: 'flex', flexDirection: 'column', alignItems: 'stretch' }}
      >
        <Box sx={{ overflow: 'hidden', bgcolor: 'background.default' }}>
          <ProductImage
            image={product.image}
            aspectRatio="4 / 5"
            priority={priority}
            sizes="(max-width: 600px) 100vw, (max-width: 900px) 50vw, (max-width: 1200px) 33vw, 320px"
            sx={{
              transition: 'transform 420ms cubic-bezier(0.2, 0.6, 0.2, 1)',
              '.MuiCardActionArea-root:hover &': { transform: 'scale(1.035)' },
            }}
          />
        </Box>

        <Stack spacing={1} sx={{ p: 2.25, flexGrow: 1 }}>
          <Typography variant="h6" component="h3" sx={{ fontSize: '1.02rem', lineHeight: 1.35 }}>
            {product.name}
          </Typography>

          {product.shortDescription && (
            <Typography
              variant="body2"
              sx={{
                display: '-webkit-box',
                WebkitLineClamp: 2,
                WebkitBoxOrient: 'vertical',
                overflow: 'hidden',
              }}
            >
              {product.shortDescription}
            </Typography>
          )}

          <Box sx={{ flexGrow: 1 }} />

          <Stack direction="row" spacing={1} sx={{ alignItems: 'center', justifyContent: 'space-between' }}>
            <Typography component="p" sx={{ fontWeight: 700, fontSize: '1.05rem' }}>
              {formatMoney(product.price, config.currency, config.locale)}
            </Typography>
            <StockBadge inStock={product.inStock} quantity={product.stockQuantity} />
          </Stack>
        </Stack>
      </CardActionArea>
    </Card>
  );
}

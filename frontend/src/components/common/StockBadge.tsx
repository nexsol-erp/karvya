import Chip from '@mui/material/Chip';

interface Props {
  inStock: boolean;
  quantity: number;
  /** Below this, the remaining count is shown rather than a plain "In stock". */
  lowStockAt?: number;
  size?: 'small' | 'medium';
}

/**
 * Availability, stated plainly.
 *
 * <p>Colour is not the only signal: the label itself says what the state is,
 * so the badge still reads correctly in greyscale or to anyone who cannot
 * distinguish the green from the grey.
 */
export function StockBadge({ inStock, quantity, lowStockAt = 3, size = 'small' }: Props) {
  if (!inStock) {
    // text.secondary, not text.disabled: whether a piece is available is
    // information the customer needs, so it has to meet 4.5:1 rather than the
    // lower bar an inactive control is allowed
    return (
      <Chip
        size={size}
        label="Out of stock"
        variant="outlined"
        sx={{ color: 'text.secondary', borderColor: 'text.secondary' }}
      />
    );
  }

  const low = quantity <= lowStockAt;
  return (
    <Chip
      size={size}
      color="success"
      variant={low ? 'filled' : 'outlined'}
      label={low ? `Only ${quantity} left` : 'In stock'}
    />
  );
}

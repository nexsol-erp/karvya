import Chip from '@mui/material/Chip';
import type { ChipProps } from '@mui/material/Chip';

import type { OrderStatus, PaymentStatus } from '../../api/orders';
import { ORDER_STATUS_LABELS, PAYMENT_STATUS_LABELS } from '../../api/orders';
import { palette } from '../../theme';

/**
 * Order state as a chip.
 *
 * <p>Each state gets a distinct shape as well as a colour - filled for the
 * ones needing action, outlined for the settled ones - so the list still reads
 * correctly in greyscale or to anyone who cannot separate the hues.
 */
const ORDER_STYLES: Record<OrderStatus, { color: string; filled: boolean }> = {
  NEW: { color: palette.terracotta, filled: true },
  CONFIRMED: { color: palette.coir, filled: true },
  PROCESSING: { color: palette.coconut, filled: false },
  SHIPPED: { color: palette.forest, filled: false },
  DELIVERED: { color: palette.forest, filled: true },
  CANCELLED: { color: palette.stone, filled: false },
};

const PAYMENT_STYLES: Record<PaymentStatus, { color: string; filled: boolean }> = {
  PENDING: { color: palette.stone, filled: false },
  AWAITING_PAYMENT: { color: palette.terracotta, filled: false },
  PAID_OFFLINE: { color: palette.forest, filled: true },
  REFUNDED: { color: palette.charcoalMuted, filled: false },
};

interface Props extends Omit<ChipProps, 'label' | 'color'> {
  status: OrderStatus;
}

export function OrderStatusChip({ status, ...rest }: Props) {
  const style = ORDER_STYLES[status];
  return (
    <Chip
      size="small"
      label={ORDER_STATUS_LABELS[status]}
      sx={{
        fontWeight: 600,
        color: style.filled ? '#fff' : style.color,
        bgcolor: style.filled ? style.color : 'transparent',
        border: `1px solid ${style.color}`,
      }}
      {...rest}
    />
  );
}

export function PaymentStatusChip({ status, ...rest }: Omit<ChipProps, 'label' | 'color'> & { status: PaymentStatus }) {
  const style = PAYMENT_STYLES[status];
  return (
    <Chip
      size="small"
      label={PAYMENT_STATUS_LABELS[status]}
      sx={{
        fontWeight: 600,
        color: style.filled ? '#fff' : style.color,
        bgcolor: style.filled ? style.color : 'transparent',
        border: `1px solid ${style.color}`,
      }}
      {...rest}
    />
  );
}

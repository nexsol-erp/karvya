import { apiFetch } from './client';
import type { CartAdjustment, CartLineInput } from './cart';
import type { PageResponse } from './types';

export type OrderStatus =
  | 'NEW' | 'CONFIRMED' | 'PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED';

export type PaymentStatus = 'PENDING' | 'AWAITING_PAYMENT' | 'PAID_OFFLINE' | 'REFUNDED';

export interface OrderLine {
  productName: string;
  productSku: string;
  productSlug: string | null;
  unitPrice: string | number;
  quantity: number;
  lineTotal: string | number;
  imageKey: string | null;
}

export interface OrderDelivery {
  name: string;
  phone: string;
  email: string | null;
  line1: string;
  line2: string | null;
  city: string;
  state: string;
  postalCode: string;
  notes: string | null;
}

export interface TimelineEntry {
  field: string;
  from: string | null;
  to: string;
  note: string | null;
  changedAt: string;
}

export interface OrderDetail {
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  paymentMethodCode: string;
  paymentMethodLabel: string;
  paymentInstructions: string | null;
  currency: string;
  subtotal: string | number;
  deliveryCharge: string | number;
  total: string | number;
  delivery: OrderDelivery;
  customerComments: string | null;
  lines: OrderLine[];
  timeline: TimelineEntry[];
  placedAt: string;
  confirmationEmailQueued: boolean;
}

export interface OrderSummary {
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  currency: string;
  total: string | number;
  itemCount: number;
  firstItemName: string | null;
  firstItemImageKey: string | null;
  placedAt: string;
}

export interface PlacedOrder {
  orderNumber: string;
  /** Returned once. Needed to view a guest confirmation. */
  accessToken: string;
  detail: OrderDetail;
}

export interface PaymentMethodOption {
  code: string;
  label: string;
  instructions: string | null;
}

export interface CheckoutInput {
  items: CartLineInput[];
  savedAddressId?: number | null;
  deliveryName: string;
  deliveryPhone: string;
  deliveryEmail?: string;
  addressLine1: string;
  addressLine2?: string;
  city: string;
  state: string;
  postalCode: string;
  deliveryNotes?: string;
  customerComments?: string;
  paymentMethodCode: string;
}

/** A 409 from checkout carries what changed, so the page can be specific. */
export interface CheckoutConflict {
  adjustments: CartAdjustment[];
}

export const orderKeys = {
  paymentMethods: ['payment-methods'] as const,
  confirmation: (orderNumber: string, token: string | null) =>
    ['order', orderNumber, token] as const,
  myOrders: (page: number) => ['account', 'orders', page] as const,
  myOrder: (orderNumber: string) => ['account', 'orders', orderNumber] as const,
};

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

export const getPaymentMethods = (): Promise<PaymentMethodOption[]> =>
  apiFetch('/payment-methods');

export const placeOrder = (input: CheckoutInput): Promise<PlacedOrder> =>
  apiFetch('/orders', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', ...csrfHeader() },
    body: JSON.stringify(input),
  });

/** Guest confirmation. The token is what authorises it, not the order number. */
export const getOrderByToken = (orderNumber: string, token: string): Promise<OrderDetail> =>
  apiFetch(`/orders/${encodeURIComponent(orderNumber)}?token=${encodeURIComponent(token)}`);

export const getMyOrder = (orderNumber: string): Promise<OrderDetail> =>
  apiFetch(`/account/orders/${encodeURIComponent(orderNumber)}`);

export const getMyOrders = (page = 0, size = 10): Promise<PageResponse<OrderSummary>> =>
  apiFetch(`/account/orders?page=${page}&size=${size}`);

export const ORDER_STATUS_LABELS: Record<OrderStatus, string> = {
  NEW: 'New',
  CONFIRMED: 'Confirmed',
  PROCESSING: 'Processing',
  SHIPPED: 'Shipped',
  DELIVERED: 'Delivered',
  CANCELLED: 'Cancelled',
};

export const PAYMENT_STATUS_LABELS: Record<PaymentStatus, string> = {
  PENDING: 'Payment pending',
  AWAITING_PAYMENT: 'Awaiting payment',
  PAID_OFFLINE: 'Paid',
  REFUNDED: 'Refunded',
};

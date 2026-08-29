import { apiFetch } from './client';
import type { ImageRef } from './types';

export interface CartLine {
  productId: number;
  sku: string;
  slug: string;
  name: string;
  unitPrice: string | number;
  quantity: number;
  lineTotal: string | number;
  availableStock: number;
  image: ImageRef | null;
}

export type AdjustmentKind =
  | 'REMOVED_UNAVAILABLE'
  | 'REMOVED_OUT_OF_STOCK'
  | 'QUANTITY_REDUCED';

export interface CartAdjustment {
  productId: number;
  productName: string;
  kind: AdjustmentKind;
  message: string;
}

export interface CartView {
  lines: CartLine[];
  adjustments: CartAdjustment[];
  itemCount: number;
  subtotal: string | number;
  deliveryCharge: string | number;
  total: string | number;
  currency: string;
  freeDeliveryThreshold: string | number | null;
  amountToFreeDelivery: string | number | null;
}

/** What the browser stores and sends: identifiers and quantities only. */
export interface CartLineInput {
  productId: number;
  quantity: number;
}

export const cartKeys = {
  guest: (items: CartLineInput[]) => ['cart', 'guest', items] as const,
  server: ['account', 'cart'] as const,
};

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

const jsonHeaders = () => ({ 'Content-Type': 'application/json', ...csrfHeader() });

/** Prices a visitor cart. Stores nothing; the browser keeps the cart. */
export const validateCart = (items: CartLineInput[]): Promise<CartView> =>
  apiFetch('/cart/validate', {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ items }),
  });

export const getServerCart = (): Promise<CartView> => apiFetch('/account/cart');

export const setServerCartItem = (productId: number, quantity: number): Promise<CartView> =>
  apiFetch(`/account/cart/items/${productId}?quantity=${quantity}`, {
    method: 'PUT',
    headers: csrfHeader(),
  });

export const removeServerCartItem = (productId: number): Promise<CartView> =>
  apiFetch(`/account/cart/items/${productId}`, { method: 'DELETE', headers: csrfHeader() });

export const clearServerCart = (): Promise<CartView> =>
  apiFetch('/account/cart', { method: 'DELETE', headers: csrfHeader() });

/** Folds the browser cart into the account cart after signing in. */
export const mergeGuestCart = (items: CartLineInput[]): Promise<CartView> =>
  apiFetch('/account/cart/merge', {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ items }),
  });

export const EMPTY_CART: CartView = {
  lines: [],
  adjustments: [],
  itemCount: 0,
  subtotal: 0,
  deliveryCharge: 0,
  total: 0,
  currency: 'INR',
  freeDeliveryThreshold: null,
  amountToFreeDelivery: null,
};

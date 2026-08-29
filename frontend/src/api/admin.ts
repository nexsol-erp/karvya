import { apiFetch } from './client';
import type { PageResponse } from './types';
import type { OrderDetail, OrderStatus, OrderSummary, PaymentStatus } from './orders';

// ---- dashboard --------------------------------------------------------------

export interface LowStockItem {
  id: number;
  sku: string;
  name: string;
  stockQuantity: number;
  threshold: number;
}

export interface AdminOrderRow {
  id: number;
  orderNumber: string;
  status: OrderStatus;
  paymentStatus: PaymentStatus;
  customerName: string;
  customerPhone: string;
  customerEmail: string | null;
  registeredCustomer: boolean;
  currency: string;
  total: string | number;
  itemCount: number;
  placedAt: string;
}

export interface EnquiryView {
  id: number;
  name: string;
  email: string;
  phone: string | null;
  subject: string;
  message: string;
  status: 'NEW' | 'IN_PROGRESS' | 'RESOLVED';
  internalNote: string | null;
  handledBy: string | null;
  createdAt: string;
  updatedAt: string;
}

export interface Dashboard {
  ordersByStatus: Record<string, number>;
  ordersByPaymentStatus: Record<string, number>;
  ordersNeedingAttention: number;
  /** Always shown beside the figure, so "total value" is never read as revenue. */
  orderValueWindow: string;
  orderValueInWindow: string | number;
  ordersInWindow: number;
  currency: string;
  recentOrders: AdminOrderRow[];
  lowStock: LowStockItem[];
  pendingNotifications: number;
  failedNotifications: number;
  newEnquiries: number;
  recentEnquiries: EnquiryView[];
}

// ---- orders -----------------------------------------------------------------

export interface OfflinePaymentView {
  id: number;
  methodCode: string;
  reference: string | null;
  amount: string | number;
  receivedOn: string;
  note: string | null;
  recordedBy: string;
  createdAt: string;
}

export interface AdminOrderDetail {
  order: OrderDetail;
  internalNotes: string | null;
  registeredCustomer: boolean;
  customerAccountEmail: string | null;
  stockRestoredAt: string | null;
  payments: OfflinePaymentView[];
  /** Only the transitions currently legal, so the UI offers nothing else. */
  allowedStatuses: OrderStatus[];
  allowedPaymentStatuses: PaymentStatus[];
}

export interface OrderFilters {
  q?: string;
  status?: OrderStatus | '';
  paymentStatus?: PaymentStatus | '';
  placedFrom?: string;
  placedTo?: string;
  page?: number;
  size?: number;
}

// ---- products ---------------------------------------------------------------

export type ProductStatus = 'DRAFT' | 'ACTIVE' | 'INACTIVE' | 'ARCHIVED';

export interface AdminProductRow {
  id: number;
  sku: string;
  slug: string;
  name: string;
  categoryName: string;
  price: string | number;
  stockQuantity: number;
  lowStock: boolean;
  featured: boolean;
  status: ProductStatus;
  placeholderContent: boolean;
  primaryImageKey: string | null;
  updatedAt: string;
}

export interface AdminProductImage {
  id: number;
  /** A base name, not a file: renditions hang off it as {key}-{width}.{format}. */
  storageKey: string;
  altText: string;
  width: number | null;
  height: number | null;
  displayOrder: number;
  primary: boolean;
  /** Which renditions exist. Uploads are JPEG only; seeded photographs have all three. */
  formats: string[];
}

export interface AdminProductDetail {
  id: number;
  sku: string;
  slug: string;
  name: string;
  categoryId: number;
  categoryName: string;
  shortDescription: string | null;
  description: string | null;
  price: string | number;
  material: string | null;
  colour: string | null;
  dimensions: string | null;
  careInstructions: string | null;
  stockQuantity: number;
  lowStockThreshold: number;
  featured: boolean;
  status: ProductStatus;
  placeholderContent: boolean;
  /** Sent back on save so a stale edit is refused rather than overwriting. */
  version: number;
  images: AdminProductImage[];
  createdAt: string;
  updatedAt: string;
  updatedBy: string | null;
}

export interface AdminCategory {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  displayOrder: number;
  active: boolean;
  productCount: number;
}

// ---- settings and customers -------------------------------------------------

export type SettingType =
  | 'STRING' | 'TEXT' | 'INTEGER' | 'DECIMAL' | 'BOOLEAN' | 'URL' | 'HTML' | 'JSON'
  | 'COLOUR' | 'FONT' | 'SECRET';

export interface SettingView {
  key: string;
  value: string | null;
  valueType: SettingType;
  description: string | null;
  /** Seeded copy still saying [PLACEHOLDER] - visibly wrong to a customer. */
  placeholder: boolean;
  /** Empty. Not wrong on the page, but silently removes whatever it drives. */
  unset: boolean;
}

export interface CustomerRow {
  id: number;
  email: string;
  fullName: string;
  phone: string | null;
  enabled: boolean;
  locked: boolean;
  lastLoginAt: string | null;
  memberSince: string;
}

export interface CustomerDetail {
  customer: CustomerRow;
  roles: string[];
  orderCount: number;
  recentOrders: OrderSummary[];
}

// ---- plumbing ---------------------------------------------------------------

export const adminKeys = {
  dashboard: ['admin', 'dashboard'] as const,
  orders: (filters: OrderFilters) => ['admin', 'orders', filters] as const,
  order: (orderNumber: string) => ['admin', 'order', orderNumber] as const,
  products: (q: string, status: string, page: number) =>
    ['admin', 'products', q, status, page] as const,
  product: (id: number) => ['admin', 'product', id] as const,
  categories: ['admin', 'categories'] as const,
  settings: ['admin', 'settings'] as const,
  customers: (q: string, page: number) => ['admin', 'customers', q, page] as const,
  customer: (id: number) => ['admin', 'customer', id] as const,
  enquiries: (status: string, q: string, page: number) =>
    ['admin', 'enquiries', status, q, page] as const,
};

function csrfHeader(): Record<string, string> {
  const match = document.cookie.match(/(?:^|;\s*)XSRF-TOKEN=([^;]*)/);
  return match ? { 'X-XSRF-TOKEN': decodeURIComponent(match[1]) } : {};
}

const jsonHeaders = () => ({ 'Content-Type': 'application/json', ...csrfHeader() });

function query(params: Record<string, unknown>): string {
  const search = new URLSearchParams();
  for (const [key, value] of Object.entries(params)) {
    if (value === undefined || value === null || value === '') continue;
    search.set(key, String(value));
  }
  const q = search.toString();
  return q ? `?${q}` : '';
}

// ---- calls ------------------------------------------------------------------

export const getDashboard = (): Promise<Dashboard> => apiFetch('/admin/dashboard');

export const listOrders = (filters: OrderFilters): Promise<PageResponse<AdminOrderRow>> =>
  apiFetch(`/admin/orders${query({ ...filters })}`);

export const getOrder = (orderNumber: string): Promise<AdminOrderDetail> =>
  apiFetch(`/admin/orders/${encodeURIComponent(orderNumber)}`);

export const setOrderStatus = (
  orderNumber: string,
  status: OrderStatus,
  note?: string,
): Promise<AdminOrderDetail> =>
  apiFetch(`/admin/orders/${encodeURIComponent(orderNumber)}/status`, {
    method: 'PATCH',
    headers: jsonHeaders(),
    body: JSON.stringify({ status, note }),
  });

export const setPaymentStatus = (
  orderNumber: string,
  paymentStatus: PaymentStatus,
  note?: string,
): Promise<AdminOrderDetail> =>
  apiFetch(`/admin/orders/${encodeURIComponent(orderNumber)}/payment-status`, {
    method: 'PATCH',
    headers: jsonHeaders(),
    body: JSON.stringify({ paymentStatus, note }),
  });

export const recordPayment = (
  orderNumber: string,
  input: {
    methodCode: string;
    reference?: string;
    amount: string;
    receivedOn: string;
    note?: string;
    markAsPaid: boolean;
  },
): Promise<AdminOrderDetail> =>
  apiFetch(`/admin/orders/${encodeURIComponent(orderNumber)}/payments`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify(input),
  });

export const addOrderNote = (orderNumber: string, note: string): Promise<AdminOrderDetail> =>
  apiFetch(`/admin/orders/${encodeURIComponent(orderNumber)}/notes`, {
    method: 'POST',
    headers: jsonHeaders(),
    body: JSON.stringify({ note }),
  });

/** The export URL, opened directly so the browser handles the download. */
export const orderExportUrl = (filters: Pick<OrderFilters, 'placedFrom' | 'placedTo' | 'status'>) =>
  `/api/v1/admin/orders/export.csv${query({ ...filters })}`;

export const listProducts = (
  q: string,
  status: string,
  page: number,
): Promise<PageResponse<AdminProductRow>> =>
  apiFetch(`/admin/products${query({ q, status, page, size: 20 })}`);

export const getProduct = (id: number): Promise<AdminProductDetail> =>
  apiFetch(`/admin/products/${id}`);

export const saveProduct = (
  id: number | null,
  body: Record<string, unknown>,
): Promise<AdminProductDetail> =>
  apiFetch(id === null ? '/admin/products' : `/admin/products/${id}`, {
    method: id === null ? 'POST' : 'PUT',
    headers: jsonHeaders(),
    body: JSON.stringify(body),
  });

export const setProductStatus = (id: number, status: ProductStatus): Promise<AdminProductDetail> =>
  apiFetch(`/admin/products/${id}/status`, {
    method: 'PATCH',
    headers: jsonHeaders(),
    body: JSON.stringify({ status }),
  });

export const productUsage = (id: number): Promise<{ hasBeenOrdered: boolean }> =>
  apiFetch(`/admin/products/${id}/usage`);

export const uploadProductImage = (
  id: number,
  file: File,
  altText: string,
): Promise<AdminProductDetail> => {
  const form = new FormData();
  form.append('file', file);
  form.append('altText', new Blob([altText], { type: 'text/plain' }));
  // no Content-Type: the browser must set the multipart boundary itself
  return apiFetch(`/admin/products/${id}/images`, {
    method: 'POST',
    headers: csrfHeader(),
    body: form,
  });
};

export const reorderProductImages = (
  id: number,
  imageIds: number[],
  primaryImageId: number,
): Promise<AdminProductDetail> =>
  apiFetch(`/admin/products/${id}/images/order`, {
    method: 'PUT',
    headers: jsonHeaders(),
    body: JSON.stringify({ imageIds, primaryImageId }),
  });

export const deleteProductImage = (id: number, imageId: number): Promise<AdminProductDetail> =>
  apiFetch(`/admin/products/${id}/images/${imageId}`, {
    method: 'DELETE',
    headers: csrfHeader(),
  });

export const listCategories = (): Promise<AdminCategory[]> => apiFetch('/admin/categories');

export const listSettings = (): Promise<SettingView[]> => apiFetch('/admin/settings');

export const saveSettings = (values: Record<string, string>): Promise<SettingView[]> =>
  apiFetch('/admin/settings', {
    method: 'PUT',
    headers: jsonHeaders(),
    body: JSON.stringify({ values }),
  });

export interface MailTestResult {
  sent: boolean;
  recipient: string;
  source: string;
  error?: string;
}

export const sendTestEmail = (): Promise<MailTestResult> =>
  apiFetch('/admin/settings/mail/test', { method: 'POST', headers: jsonHeaders() });

export const listCustomers = (q: string, page: number): Promise<PageResponse<CustomerRow>> =>
  apiFetch(`/admin/customers${query({ q, page, size: 20 })}`);

export const getCustomer = (id: number): Promise<CustomerDetail> =>
  apiFetch(`/admin/customers/${id}`);

export const setCustomerEnabled = (id: number, enabled: boolean): Promise<CustomerRow> =>
  apiFetch(`/admin/customers/${id}/enabled?enabled=${enabled}`, {
    method: 'PATCH',
    headers: csrfHeader(),
  });

export const sendCustomerPasswordReset = (id: number): Promise<{ status: string }> =>
  apiFetch(`/admin/customers/${id}/password-reset`, {
    method: 'POST',
    headers: csrfHeader(),
  });

export const listEnquiries = (
  status: string,
  q: string,
  page: number,
): Promise<PageResponse<EnquiryView>> =>
  apiFetch(`/admin/enquiries${query({ status, q, page, size: 20 })}`);

export const setEnquiryStatus = (
  id: number,
  status: EnquiryView['status'],
  internalNote?: string,
): Promise<EnquiryView> =>
  apiFetch(`/admin/enquiries/${id}/status`, {
    method: 'PATCH',
    headers: jsonHeaders(),
    body: JSON.stringify({ status, internalNote }),
  });

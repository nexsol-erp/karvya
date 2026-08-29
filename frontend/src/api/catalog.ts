import { apiFetch, toQueryString } from './client';
import type {
  CategorySummary,
  PageResponse,
  ProductDetail,
  ProductQueryParams,
  ProductSummary,
} from './types';

export const catalogKeys = {
  all: ['catalog'] as const,
  products: (params: ProductQueryParams) => ['catalog', 'products', params] as const,
  product: (slug: string) => ['catalog', 'product', slug] as const,
  related: (slug: string) => ['catalog', 'product', slug, 'related'] as const,
  categories: ['catalog', 'categories'] as const,
};

export function searchProducts(params: ProductQueryParams): Promise<PageResponse<ProductSummary>> {
  return apiFetch(`/products${toQueryString({ ...params })}`);
}

export function getProduct(slug: string): Promise<ProductDetail> {
  return apiFetch(`/products/${encodeURIComponent(slug)}`);
}

export function getRelatedProducts(slug: string): Promise<ProductSummary[]> {
  return apiFetch(`/products/${encodeURIComponent(slug)}/related`);
}

export function getCategories(): Promise<CategorySummary[]> {
  return apiFetch('/categories');
}

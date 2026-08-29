/** Mirrors the backend DTOs under /api/v1. Kept hand-written and small. */

export interface ImageRef {
  key: string;
  alt: string;
  width: number | null;
  height: number | null;
}

export interface ProductSummary {
  id: number;
  sku: string;
  slug: string;
  name: string;
  shortDescription: string | null;
  price: string | number;
  inStock: boolean;
  stockQuantity: number;
  featured: boolean;
  categorySlug: string;
  categoryName: string;
  image: ImageRef | null;
}

export interface ProductDetail extends Omit<ProductSummary, 'image'> {
  description: string | null;
  material: string | null;
  colour: string | null;
  dimensions: string | null;
  careInstructions: string | null;
  placeholderContent: boolean;
  images: ImageRef[];
}

export interface CategorySummary {
  id: number;
  name: string;
  slug: string;
  description: string | null;
  imageKey: string | null;
  productCount: number;
}

export interface PageResponse<T> {
  content: T[];
  page: number;
  size: number;
  totalElements: number;
  totalPages: number;
  hasNext: boolean;
}

export type ProductSort = 'RELEVANCE' | 'NEWEST' | 'PRICE_ASC' | 'PRICE_DESC';

export interface ProductQueryParams {
  q?: string;
  category?: string;
  minPrice?: number;
  maxPrice?: number;
  featured?: boolean;
  inStock?: boolean;
  sort?: ProductSort;
  page?: number;
  size?: number;
}

/** RFC 7807 problem document, as returned by the backend for every error. */
export interface ProblemDetail {
  type: string;
  title: string;
  status: number;
  detail: string;
  instance?: string;
  timestamp?: string;
  errors?: Record<string, string>;
}

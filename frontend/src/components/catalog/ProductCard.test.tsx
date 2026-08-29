import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';

import { ProductCard } from './ProductCard';
import { renderWithProviders } from '../../test/render';
import type { ProductSummary } from '../../api/types';

const product: ProductSummary = {
  id: 5,
  sku: 'KV-BH-05',
  slug: 'twin-entrance-coir-bird-house',
  name: 'Twin Entrance Coir Bird House',
  shortDescription: 'Wide coir bird house with two copper-rimmed entrances.',
  price: '1650.00',
  inStock: true,
  stockQuantity: 5,
  featured: true,
  categorySlug: 'bird-houses-and-nests',
  categoryName: 'Bird Houses & Nests',
  image: {
    key: 'products/kv-bh-05/a',
    alt: 'Wide coir bird house with two copper-rimmed entrances',
    width: 1856,
    height: 1958,
  },
};

describe('ProductCard', () => {
  it('shows the name, the formatted price and the availability', () => {
    renderWithProviders(<ProductCard product={product} />);

    expect(screen.getByRole('heading', { name: product.name })).toBeInTheDocument();
    // en-IN grouping puts the separator after the first digit: 1,650
    expect(screen.getByText(/1,650/)).toBeInTheDocument();
    expect(screen.getByText('In stock')).toBeInTheDocument();
  });

  it('links to the product page by slug', () => {
    renderWithProviders(<ProductCard product={product} />);

    expect(screen.getByRole('link')).toHaveAttribute(
      'href',
      '/product/twin-entrance-coir-bird-house',
    );
  });

  it('warns when stock is nearly gone rather than just saying in stock', () => {
    renderWithProviders(<ProductCard product={{ ...product, stockQuantity: 2 }} />);

    expect(screen.getByText('Only 2 left')).toBeInTheDocument();
    expect(screen.queryByText('In stock')).not.toBeInTheDocument();
  });

  it('says out of stock when there is none', () => {
    renderWithProviders(
      <ProductCard product={{ ...product, inStock: false, stockQuantity: 0 }} />,
    );

    expect(screen.getByText('Out of stock')).toBeInTheDocument();
  });

  it('describes the photograph for screen readers and lazy-loads it by default', () => {
    renderWithProviders(<ProductCard product={product} />);

    const img = screen.getByRole('img', { name: product.image!.alt });
    expect(img).toHaveAttribute('loading', 'lazy');
    // intrinsic dimensions must be present or the grid reflows as images arrive
    expect(img).toHaveAttribute('width', '1856');
    expect(img).toHaveAttribute('height', '1958');
  });

  it('loads eagerly when marked as priority', () => {
    renderWithProviders(<ProductCard product={product} priority />);

    expect(screen.getByRole('img', { name: product.image!.alt })).toHaveAttribute(
      'loading',
      'eager',
    );
  });

  it('offers a fallback when a product has no photograph', () => {
    renderWithProviders(<ProductCard product={{ ...product, image: null }} />);

    expect(screen.getByRole('img', { name: 'No photograph available' })).toBeInTheDocument();
  });
});

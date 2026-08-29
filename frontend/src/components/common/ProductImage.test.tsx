import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';

import { ProductImage } from './ProductImage';
import { renderWithProviders } from '../../test/render';

const image = {
  key: 'products/kv-bh-01/a',
  alt: 'Coir nest house with a deep red roof',
  width: 1856,
  height: 1958,
};

describe('ProductImage', () => {
  it('offers every width the pipeline emits, in the fallback format', () => {
    renderWithProviders(<ProductImage image={image} sizes="320px" />);

    const srcSet = screen.getByRole('img').getAttribute('srcset') ?? '';
    for (const width of [480, 800, 1280, 1856]) {
      expect(srcSet).toContain(`/media/products/kv-bh-01/a-${width}.jpg ${width}w`);
    }
  });

  it('prefers AVIF, then WebP, before falling back to JPEG', () => {
    const { container } = renderWithProviders(<ProductImage image={image} sizes="320px" />);

    const types = Array.from(container.querySelectorAll('source')).map((s) =>
      s.getAttribute('type'),
    );
    // order is the browser's selection order, so it must run best-first
    expect(types).toEqual(['image/avif', 'image/webp']);
    expect(screen.getByRole('img').getAttribute('src')).toMatch(/\.jpg$/);
  });

  it('passes the sizes hint to every source so the browser can choose', () => {
    const { container } = renderWithProviders(
      <ProductImage image={image} sizes="(max-width: 600px) 100vw, 320px" />,
    );

    for (const source of container.querySelectorAll('source')) {
      expect(source.getAttribute('sizes')).toBe('(max-width: 600px) 100vw, 320px');
    }
  });

  it('renders a labelled placeholder instead of a broken image when there is none', () => {
    const { container } = renderWithProviders(<ProductImage image={null} sizes="320px" />);

    expect(screen.getByRole('img', { name: 'No photograph available' })).toBeInTheDocument();
    expect(container.querySelector('img')).toBeNull();
  });
});

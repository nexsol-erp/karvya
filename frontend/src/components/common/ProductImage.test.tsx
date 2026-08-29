import { describe, it, expect } from 'vitest';
import { screen } from '@testing-library/react';

import { ProductImage } from './ProductImage';
import { renderWithProviders } from '../../test/render';

/** Seeded through the offline pipeline, so every format exists. */
const image = {
  key: 'products/kv-bh-01/a',
  alt: 'Coir nest house with a deep red roof',
  width: 1856,
  height: 1958,
  formats: ['avif', 'webp', 'jpg'],
};

/** Uploaded through the admin, which can only encode JPEG. */
const uploaded = { ...image, key: 'products/kv-bh-01/uploaded', formats: ['jpg'] };

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

  /**
   * A browser picks a <source> on its type alone and never checks the file is
   * there, so an AVIF source for a photograph that has none shows as broken
   * rather than falling through to the JPEG.
   */
  it('offers no source at all for a photograph that only has JPEG', () => {
    const { container } = renderWithProviders(<ProductImage image={uploaded} sizes="320px" />);

    expect(container.querySelectorAll('source')).toHaveLength(0);

    const srcSet = screen.getByRole('img').getAttribute('srcset') ?? '';
    expect(srcSet).toContain('/media/products/kv-bh-01/uploaded-800.jpg 800w');
    expect(srcSet).not.toContain('.avif');
    expect(srcSet).not.toContain('.webp');
  });

  it('falls back to JPEG alone when the formats are not known', () => {
    // some references are built from a bare storage key - an admin thumbnail,
    // an order line - and JPEG is the one rendition every photograph has
    const { key, alt, width, height } = image;
    const { container } = renderWithProviders(
      <ProductImage image={{ key, alt, width, height }} sizes="320px" />,
    );

    expect(container.querySelectorAll('source')).toHaveLength(0);
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

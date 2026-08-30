import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';

import { Landing } from './Landing';
import { renderWithProviders } from '../test/render';
import type { SiteSettings } from '../hooks/useSiteSettings';

const useSiteSettings = vi.hoisted(() => vi.fn());
vi.mock('../hooks/useSiteSettings', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useSiteSettings')>()),
  useSiteSettings,
}));

const base: SiteSettings = {
  storeName: 'Karvya',
  tagline: '',
  whatsAppNumber: '',
  contactEmail: '',
  businessAddress: '',
  currency: 'INR',
  locale: 'en-IN',
  checkoutNotice: '',
  heroHeading: '',
  heroSubheading: '',
  storyHeading: '',
  storyBody: '',
  whyHandmadeBody: '',
  materialsBody: '',
  instagram: '',
  facebook: '',
  youtube: '',
  shippingPolicy: '',
  returnsPolicy: '',
  privacyPolicy: '',
  appearance: {},
  whatsAppEnabled: false,
  isLoading: false,
};

const given = (overrides: Partial<SiteSettings>) =>
  useSiteSettings.mockReturnValue({ ...base, ...overrides });

describe('Landing hero', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('leads with the heading an administrator wrote', () => {
    given({ heroHeading: 'Books worth keeping', heroSubheading: 'Chosen one at a time.' });
    renderWithProviders(<Landing />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Books worth keeping');
    expect(screen.getByText('Chosen one at a time.')).toBeInTheDocument();
  });

  /**
   * Falling back to the shop's own name rather than to a description of what it
   * sells: anything more specific would be a guess, and the first line of the
   * first page is the worst place to guess wrong.
   */
  it('falls back to the shop name when no heading is written', () => {
    given({ heroHeading: '', storeName: 'Karvya' });
    renderWithProviders(<Landing />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Karvya');
  });

  it('leaves out the supporting line rather than inventing one', () => {
    given({ heroHeading: 'Books worth keeping', heroSubheading: '' });
    renderWithProviders(<Landing />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Books worth keeping');
    // the line the hero used to carry regardless of what the shop sold. The
    // sections further down are still hardcoded and are not what this checks.
    expect(screen.queryByText(/wound by hand from natural coconut fibre/i)).toBeNull();
  });

  it('treats placeholder copy as unwritten', () => {
    given({ heroHeading: '[PLACEHOLDER] Write a headline', storeName: 'Karvya' });
    renderWithProviders(<Landing />);

    expect(screen.getByRole('heading', { level: 1 })).toHaveTextContent('Karvya');
    expect(screen.queryByText(/PLACEHOLDER/)).toBeNull();
  });
});

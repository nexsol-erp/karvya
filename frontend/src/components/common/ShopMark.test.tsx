import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';

import { ShopMark } from './ShopMark';
import { renderWithProviders } from '../../test/render';
import type { SiteSettings } from '../../hooks/useSiteSettings';

const useSiteSettings = vi.hoisted(() => vi.fn());
vi.mock('../../hooks/useSiteSettings', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../hooks/useSiteSettings')>()),
  useSiteSettings,
}));

const base = {
  storeName: 'Karvya',
  tagline: 'Handwoven coir craft',
  logoKey: '',
} as unknown as SiteSettings;

const given = (overrides: Partial<SiteSettings>) =>
  useSiteSettings.mockReturnValue({ ...base, ...overrides });

describe('ShopMark', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('shows the uploaded logo when there is one', () => {
    given({ logoKey: 'branding/abc' });
    renderWithProviders(<ShopMark height={40} />);

    const img = screen.getByRole('img', { name: 'Karvya' });
    expect(img).toHaveAttribute('src', '/media/branding/abc.png');
  });

  /**
   * The mark is the link back to the home page. An empty alt would leave a
   * screen reader announcing an unnamed link at the top of every page.
   */
  it('names the shop in the alt text rather than leaving it decorative', () => {
    given({ logoKey: 'branding/abc', storeName: 'Karvya Books' });
    renderWithProviders(<ShopMark height={40} />);

    expect(screen.getByRole('img', { name: 'Karvya Books' })).toBeInTheDocument();
  });

  it('falls back to the shop name when no logo is set', () => {
    given({ logoKey: '' });
    renderWithProviders(<ShopMark height={40} />);

    expect(screen.queryByRole('img')).toBeNull();
    expect(screen.getByText('Karvya')).toBeInTheDocument();
  });

  it('shows the tagline only where it was asked for', () => {
    given({ logoKey: '' });
    const { unmount } = renderWithProviders(<ShopMark height={40} />);
    expect(screen.queryByText('Handwoven coir craft')).toBeNull();
    unmount();

    renderWithProviders(<ShopMark height={40} showTagline />);
    expect(screen.getByText('Handwoven coir craft')).toBeInTheDocument();
  });
});

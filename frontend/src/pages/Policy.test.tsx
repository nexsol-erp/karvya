import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';

import { Policy } from './Policy';
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
  logoKey: '',
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

describe('Policy pages', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('renders the policy as the formatting it was written in', () => {
    given({
      returnsPolicy: '<p>Returns accepted within <strong>14 days</strong>.</p><ul><li>Unused</li></ul>',
    });
    renderWithProviders(<Policy kind="returns" />);

    expect(screen.getByRole('heading', { name: 'Returns', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('14 days')).toBeInTheDocument();
    expect(screen.getByRole('list')).toBeInTheDocument();
    expect(screen.getByRole('listitem')).toHaveTextContent('Unused');
  });

  it('says so plainly when the policy has not been written', () => {
    given({ shippingPolicy: '' });
    renderWithProviders(<Policy kind="shipping" />);

    expect(screen.getByText(/not published yet/i)).toBeInTheDocument();
    // and names the setting, so whoever reads it knows where to go
    expect(screen.getByText('policy.shipping')).toBeInTheDocument();
  });

  /**
   * Seeded copy still carrying the marker is a note to the shop owner, not
   * something to show a shopper as though it were the policy.
   */
  it('treats placeholder copy as unwritten', () => {
    given({ privacyPolicy: '[PLACEHOLDER] Describe what customer data you collect.' });
    renderWithProviders(<Policy kind="privacy" />);

    expect(screen.getByText(/not published yet/i)).toBeInTheDocument();
    expect(screen.queryByText(/PLACEHOLDER/)).toBeNull();
  });

  it('is a distinct page per policy', () => {
    given({ privacyPolicy: '<p>We keep your delivery address.</p>' });
    renderWithProviders(<Policy kind="privacy" />);

    expect(screen.getByRole('heading', { name: 'Privacy', level: 1 })).toBeInTheDocument();
    expect(screen.getByText('We keep your delivery address.')).toBeInTheDocument();
  });
});

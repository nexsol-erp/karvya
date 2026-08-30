import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';

import { Footer } from './Footer';
import { renderWithProviders } from '../../test/render';
import type { SiteSettings } from '../../hooks/useSiteSettings';

const useSiteSettings = vi.hoisted(() => vi.fn());
vi.mock('../../hooks/useSiteSettings', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../hooks/useSiteSettings')>()),
  useSiteSettings,
}));

const base: SiteSettings = {
  storeName: 'Karvya',
  tagline: 'Handwoven coir craft',
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

describe('Footer policy links', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('links only the policies that have been written', () => {
    given({ returnsPolicy: '<p>No returns.</p>', privacyPolicy: '' });
    renderWithProviders(<Footer />);

    expect(screen.getByRole('link', { name: 'Returns' })).toHaveAttribute('href', '/returns');
    expect(screen.queryByRole('link', { name: 'Privacy' })).toBeNull();
    expect(screen.queryByRole('link', { name: 'Delivery' })).toBeNull();
  });

  it('says none are published rather than showing an empty column', () => {
    given({});
    renderWithProviders(<Footer />);

    expect(screen.getByText(/none published yet/i)).toBeInTheDocument();
  });

  /** Seeded copy is a note to the owner, not a policy to link a shopper to. */
  it('does not link a policy that is still placeholder copy', () => {
    given({ shippingPolicy: '[PLACEHOLDER] Describe your delivery timelines.' });
    renderWithProviders(<Footer />);

    expect(screen.queryByRole('link', { name: 'Delivery' })).toBeNull();
  });
});

describe('Footer address', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('shows the address with its line breaks intact', () => {
    given({ businessAddress: '12 Weavers Lane\nAlappuzha\nKerala 688001' });
    renderWithProviders(<Footer />);

    const address = screen.getByText(/12 Weavers Lane/);
    expect(address).toBeInTheDocument();
    // an address written over several lines should not be collapsed into one
    expect(address).toHaveStyle({ whiteSpace: 'pre-line' });
  });

  it('shows nothing when no address is configured', () => {
    given({});
    renderWithProviders(<Footer />);

    expect(screen.queryByText(/Weavers Lane/)).toBeNull();
  });
});

describe('Footer social links', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('shows only the networks that have an address', () => {
    given({ instagram: 'https://instagram.com/karvya', youtube: 'https://youtube.com/@karvya' });
    renderWithProviders(<Footer />);

    expect(screen.getByRole('link', { name: 'Karvya on Instagram' })).toHaveAttribute(
      'href',
      'https://instagram.com/karvya',
    );
    expect(screen.getByRole('link', { name: 'Karvya on YouTube' })).toBeInTheDocument();
    // facebook was never configured, so it is not offered
    expect(screen.queryByRole('link', { name: /Facebook/ })).toBeNull();
  });

  it('shows none at all when none are configured', () => {
    given({});
    renderWithProviders(<Footer />);

    expect(screen.queryByRole('link', { name: /Instagram|Facebook|YouTube/ })).toBeNull();
  });

  /**
   * An href is the one place a stored string becomes executable. The server
   * types these as URLs and refuses anything but http and https, so this should
   * never trigger - which is exactly why it is worth a test rather than a
   * comment.
   */
  it('refuses to render an address that is not a web address', () => {
    given({ instagram: 'javascript:alert(1)', facebook: 'https://facebook.com/karvya' });
    renderWithProviders(<Footer />);

    expect(screen.queryByRole('link', { name: /Instagram/ })).toBeNull();
    expect(screen.getByRole('link', { name: 'Karvya on Facebook' })).toBeInTheDocument();
  });

  it('opens them in a new tab without handing over the referrer', () => {
    given({ instagram: 'https://instagram.com/karvya' });
    renderWithProviders(<Footer />);

    const link = screen.getByRole('link', { name: 'Karvya on Instagram' });
    expect(link).toHaveAttribute('target', '_blank');
    expect(link.getAttribute('rel')).toContain('noopener');
  });
});

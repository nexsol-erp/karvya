import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen } from '@testing-library/react';

import { OurStory } from './OurStory';
import { renderWithProviders } from '../test/render';
import type { SiteSettings } from '../hooks/useSiteSettings';

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

const useSiteSettings = vi.hoisted(() => vi.fn());

vi.mock('../hooks/useSiteSettings', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useSiteSettings')>()),
  useSiteSettings,
}));

function given(overrides: Partial<SiteSettings>) {
  useSiteSettings.mockReturnValue({ ...base, ...overrides });
}

describe('OurStory', () => {
  beforeEach(() => useSiteSettings.mockReset());

  it('renders the story an administrator wrote', () => {
    given({
      storyHeading: 'Woven in Alappuzha',
      storyBody: 'We have made coir pieces since 1998.\n\nEvery roof is dyed by hand.',
    });

    renderWithProviders(<OurStory />);

    expect(screen.getByRole('heading', { name: 'Woven in Alappuzha' })).toBeInTheDocument();
    expect(screen.getByText('We have made coir pieces since 1998.')).toBeInTheDocument();
    // a blank line is how anyone separates paragraphs in a plain text box
    expect(screen.getByText('Every roof is dyed by hand.')).toBeInTheDocument();
    expect(screen.queryByText(/awaiting its final copy/i)).toBeNull();
  });

  /**
   * The marker is a note to the shop owner about what to write. Printing it at
   * a shopper is worse than showing them less.
   */
  it('never shows seeded placeholder text to a customer', () => {
    given({
      storyBody: '[PLACEHOLDER] Describe who makes these pieces and how.',
      whyHandmadeBody: '[PLACEHOLDER] Explain what handmade means.',
      materialsBody: '[PLACEHOLDER] Describe the materials you work with.',
    });

    renderWithProviders(<OurStory />);

    expect(screen.queryByText(/PLACEHOLDER/i)).toBeNull();
    expect(screen.getByText(/awaiting its final copy/i)).toBeInTheDocument();
  });

  it('falls back to what is true of every piece when the story is unwritten', () => {
    given({ storyBody: '' });

    renderWithProviders(<OurStory />);

    expect(screen.getByText(/drawn from coconut husk/i)).toBeInTheDocument();
  });

  it('hides a section that has not been written rather than leaving a bare heading', () => {
    given({ storyBody: 'A real story.', whyHandmadeBody: '', materialsBody: '' });

    renderWithProviders(<OurStory />);

    expect(screen.queryByRole('heading', { name: /why handmade/i })).toBeNull();
    expect(screen.queryByRole('heading', { name: /natural materials/i })).toBeNull();
  });

  it('shows the optional sections once they are written', () => {
    given({
      storyBody: 'A real story.',
      whyHandmadeBody: 'Because a machine cannot judge the fibre.',
      materialsBody: 'Coir, jute cord, and nothing else.',
    });

    renderWithProviders(<OurStory />);

    expect(screen.getByRole('heading', { name: /why handmade/i })).toBeInTheDocument();
    expect(screen.getByText('Because a machine cannot judge the fibre.')).toBeInTheDocument();
    expect(screen.getByText('Coir, jute cord, and nothing else.')).toBeInTheDocument();
  });
});

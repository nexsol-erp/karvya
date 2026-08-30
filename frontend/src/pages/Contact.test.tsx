import { describe, it, expect, vi, beforeEach } from 'vitest';
import { screen, fireEvent } from '@testing-library/react';
import userEvent from '@testing-library/user-event';

import { Contact } from './Contact';
import { renderWithProviders } from '../test/render';
import { ApiError } from '../api/client';

const submitEnquiry = vi.hoisted(() => vi.fn());
vi.mock('../api/enquiries', () => ({ submitEnquiry }));

const useSiteSettings = vi.hoisted(() => vi.fn());
vi.mock('../hooks/useSiteSettings', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../hooks/useSiteSettings')>()),
  useSiteSettings,
}));

const settings = {
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

/**
 * Filled with one change event per field rather than a keystroke at a time.
 * Nothing here is testing what typing does - that is NumberField's job - and
 * typing every character makes these slow enough to time out on a busy machine.
 */
function fillIn() {
  const type = (label: RegExp, value: string) =>
    fireEvent.change(screen.getByLabelText(label), { target: { value } });

  type(/your name/i, 'Asha Menon');
  type(/email address/i, 'asha@example.com');
  type(/^subject/i, 'A question');
  type(/^message/i, 'Do you ship to Kochi?');
}

describe('Contact form', () => {
  beforeEach(() => {
    submitEnquiry.mockReset();
    useSiteSettings.mockReturnValue(settings);
  });

  it('sends what was typed and confirms it', async () => {
    submitEnquiry.mockResolvedValue(undefined);
    renderWithProviders(<Contact />);

    fillIn();
    await userEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(submitEnquiry).toHaveBeenCalledWith(
      expect.objectContaining({
        name: 'Asha Menon',
        email: 'asha@example.com',
        subject: 'A question',
        message: 'Do you ship to Kochi?',
      }),
    );
    expect(await screen.findByText(/your message has been sent/i)).toBeInTheDocument();
  });

  /** The honeypot is only useful if a person never fills it, so it starts empty. */
  it('sends the honeypot empty', async () => {
    submitEnquiry.mockResolvedValue(undefined);
    renderWithProviders(<Contact />);

    fillIn();
    await userEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(submitEnquiry).toHaveBeenCalledWith(expect.objectContaining({ website: '' }));
  });

  it('marks the fields the server rejected', async () => {
    submitEnquiry.mockRejectedValue(
      new ApiError(400, { errors: { email: 'Enter a valid email address' } } as never, 'bad'),
    );
    renderWithProviders(<Contact />);

    fillIn();
    await userEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText('Enter a valid email address')).toBeInTheDocument();
    expect(screen.getByLabelText(/email address/i)).toHaveAttribute('aria-invalid', 'true');
  });

  /**
   * Being throttled is not a fault the sender can fix by trying harder, so it
   * says what happened rather than "something went wrong".
   */
  it('explains a rate limit instead of blaming the sender', async () => {
    submitEnquiry.mockRejectedValue(new ApiError(429, null, 'too many'));
    renderWithProviders(<Contact />);

    fillIn();
    await userEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText(/several messages in a short time/i)).toBeInTheDocument();
  });

  it('keeps what was typed when sending fails, so it is not lost', async () => {
    submitEnquiry.mockRejectedValue(new ApiError(500, null, 'boom'));
    renderWithProviders(<Contact />);

    fillIn();
    await userEvent.click(screen.getByRole('button', { name: /send message/i }));

    expect(await screen.findByText(/could not be sent just now/i)).toBeInTheDocument();
    expect(screen.getByLabelText(/^message/i)).toHaveValue('Do you ship to Kochi?');
  });

  it('offers the direct channels only when they are configured', () => {
    useSiteSettings.mockReturnValue({
      ...settings,
      whatsAppEnabled: true,
      whatsAppNumber: '919746800113',
      contactEmail: 'hello@karvya.in',
    });
    renderWithProviders(<Contact />);

    expect(screen.getByRole('link', { name: /chat on whatsapp/i })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'hello@karvya.in' })).toBeInTheDocument();
    // and the form is still there, because it is the reliable one
    expect(screen.getByRole('button', { name: /send message/i })).toBeInTheDocument();
  });
});

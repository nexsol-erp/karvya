import { apiFetch } from './client';

/**
 * Storefront configuration as the server holds it.
 *
 * <p>Every field is nullable: an administrator may not have filled it in, and
 * the storefront hides the feature rather than showing an invented value.
 */
export interface PublicSettings {
  storeName: string | null;
  tagline: string | null;
  /** Already normalised to digits, country code first, no leading 00. */
  whatsAppNumber: string | null;
  contactEmail: string | null;
  businessAddress: string | null;
  currency: string | null;
  locale: string | null;
  checkoutNotice: string | null;

  colourPrimary: string | null;
  colourSecondary: string | null;
  colourBackground: string | null;
  colourSurface: string | null;
  colourText: string | null;
  fontHeading: string | null;
  fontBody: string | null;
  cornerRadius: string | null;

  heroHeading: string | null;
  heroSubheading: string | null;
  storyHeading: string | null;
  storyBody: string | null;
  whyHandmadeBody: string | null;
  materialsBody: string | null;

  instagram: string | null;
  facebook: string | null;
  youtube: string | null;

  shippingPolicy: string | null;
  returnsPolicy: string | null;
  privacyPolicy: string | null;

  deliveryCharge: string | number | null;
  freeDeliveryThreshold: string | number | null;
}

export const settingsKeys = {
  public: ['settings', 'public'] as const,
};

export const getPublicSettings = (): Promise<PublicSettings> => apiFetch('/settings/public');

/**
 * Build-time configuration.
 *
 * These are fallbacks only. Once the public settings endpoint lands, the store
 * name, WhatsApp number and contact details come from the database so an
 * administrator can change them without a rebuild. Nothing here is a business
 * fact - every value is either a placeholder or empty, and the UI hides the
 * feature rather than showing an invented one.
 */
export const config = {
  storeName: import.meta.env.VITE_STORE_NAME || 'Karvya',
  tagline: import.meta.env.VITE_STORE_TAGLINE || 'Handwoven coir craft',

  /** Digits only, country code first. Empty means the feature stays hidden. */
  whatsAppNumber: import.meta.env.VITE_WHATSAPP_NUMBER || '',
  contactEmail: import.meta.env.VITE_CONTACT_EMAIL || '',

  currency: import.meta.env.VITE_CURRENCY || 'INR',
  locale: import.meta.env.VITE_LOCALE || 'en-IN',
} as const;

/** True only when a real number has been configured. */
export const whatsAppEnabled = (): boolean => config.whatsAppNumber.replace(/\D/g, '').length >= 8;

export const siteUrl = (): string =>
  typeof window === 'undefined' ? '' : window.location.origin;

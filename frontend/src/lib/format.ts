/**
 * Money formatting.
 *
 * Currency and locale default to INR and en-IN but are configurable through
 * site settings, so nothing here hard-codes a rupee sign. Prices arrive from
 * the API as strings to preserve decimal scale; parsing happens in one place.
 */

const DEFAULT_CURRENCY = 'INR';
const DEFAULT_LOCALE = 'en-IN';

export function formatMoney(
  amount: string | number,
  currency: string = DEFAULT_CURRENCY,
  locale: string = DEFAULT_LOCALE,
): string {
  const value = typeof amount === 'string' ? Number.parseFloat(amount) : amount;
  if (!Number.isFinite(value)) return '';

  return new Intl.NumberFormat(locale, {
    style: 'currency',
    currency,
    minimumFractionDigits: 0,
    maximumFractionDigits: 2,
  }).format(value);
}

/**
 * Builds the WhatsApp deep link. The number comes from site settings or the
 * environment - never hard-coded - and the message is URL-encoded, which
 * matters because product names and URLs both contain characters that would
 * otherwise truncate the text.
 */
export function whatsAppLink(number: string, message: string): string {
  const digits = number.replace(/\D/g, '');
  return `https://wa.me/${digits}?text=${encodeURIComponent(message)}`;
}

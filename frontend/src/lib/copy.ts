/**
 * Helpers for copy an administrator writes in site settings.
 *
 * <p>Seeded settings carry a `[PLACEHOLDER]` marker describing what to write.
 * That marker is a note to the shop owner, not to a customer, so a page must be
 * able to tell "written" from "still the prompt" and show less rather than show
 * the word PLACEHOLDER to a shopper.
 */

const PLACEHOLDER = '[PLACEHOLDER';

/** True when a setting holds copy a customer should actually read. */
export function isWritten(value: string | null | undefined): boolean {
  return Boolean(value && value.trim() !== '' && !value.includes(PLACEHOLDER));
}

/**
 * Splits written copy into paragraphs on blank lines.
 *
 * <p>Settings are plain text, so an owner separates paragraphs the way anyone
 * would - by leaving a blank line. Without this the whole thing renders as one
 * unbroken block however it was typed. Single newlines are left alone, since
 * those are usually wrapping rather than a new paragraph.
 */
export function paragraphs(value: string | null | undefined): string[] {
  if (!value) return [];
  return value
    .split(/\n\s*\n/)
    .map((part) => part.trim())
    .filter((part) => part !== '');
}

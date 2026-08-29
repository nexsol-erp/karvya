/**
 * The typefaces an administrator may choose, and how to load them.
 *
 * <p>Kept in step with ThemeFonts.java, which validates the choice on save.
 * A name that got past the server but not this map would leave the shop in a
 * default sans-serif with nothing to explain why, so anything unrecognised
 * falls back to the built-in pair rather than being passed through.
 *
 * <p>Weights are listed per family because Google's API rejects the whole URL
 * if any requested weight does not exist for that family - Libre Baskerville
 * has 400 and 700 and nothing between - which would drop every font on the
 * page, not just the one that was wrong.
 */
export interface FontDefinition {
  /** The full CSS stack, including a fallback matched to the shape of the web font. */
  stack: string;
  weights: number[];
  kind: 'serif' | 'sans';
}

export const FONTS: Record<string, FontDefinition> = {
  Fraunces: {
    stack: '"Fraunces", "Iowan Old Style", Georgia, serif',
    weights: [400, 600, 700],
    kind: 'serif',
  },
  'Playfair Display': {
    stack: '"Playfair Display", "Iowan Old Style", Georgia, serif',
    weights: [400, 600, 700],
    kind: 'serif',
  },
  Lora: {
    stack: '"Lora", "Iowan Old Style", Georgia, serif',
    weights: [400, 600, 700],
    kind: 'serif',
  },
  'Cormorant Garamond': {
    stack: '"Cormorant Garamond", Garamond, Georgia, serif',
    weights: [400, 600, 700],
    kind: 'serif',
  },
  'Libre Baskerville': {
    stack: '"Libre Baskerville", Baskerville, Georgia, serif',
    weights: [400, 700],
    kind: 'serif',
  },
  Karla: {
    stack: '"Karla", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
  Inter: {
    stack: '"Inter", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
  'Work Sans': {
    stack: '"Work Sans", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
  'Nunito Sans': {
    stack: '"Nunito Sans", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
  'Source Sans 3': {
    stack: '"Source Sans 3", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
  'DM Sans': {
    stack: '"DM Sans", "Helvetica Neue", Arial, sans-serif',
    weights: [400, 500, 600, 700],
    kind: 'sans',
  },
};

export const DEFAULT_HEADING_FONT = 'Fraunces';
export const DEFAULT_BODY_FONT = 'Karla';

export const FONT_NAMES = Object.keys(FONTS);

export function fontStack(family: string | null | undefined, fallback: string): string {
  const chosen = family && FONTS[family] ? family : fallback;
  return FONTS[chosen].stack;
}

/**
 * The stylesheet URL for the families in use.
 *
 * <p>Returns null when the pair is the one already linked in index.html, so
 * the common case adds no second request.
 */
export function googleFontsHref(heading: string, body: string): string | null {
  if (heading === DEFAULT_HEADING_FONT && body === DEFAULT_BODY_FONT) {
    return null;
  }

  const families = Array.from(new Set([heading, body])).filter((name) => FONTS[name]);
  if (families.length === 0) return null;

  const params = families
    .map((name) => `family=${name.replace(/ /g, '+')}:wght@${FONTS[name].weights.join(';')}`)
    .join('&');

  return `https://fonts.googleapis.com/css2?${params}&display=swap`;
}

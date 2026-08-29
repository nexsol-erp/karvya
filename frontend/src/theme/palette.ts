/**
 * Colours sampled from the product photographs themselves - the coir body, the
 * dyed roof, and the plaster backdrops the pieces were shot against. Deriving
 * the palette from the objects is what keeps the storefront looking like it
 * belongs to them rather than to a template.
 */
export const palette = {
  /** Warm plaster, taken from the studio backdrop. Page ground. */
  plaster: '#F0E7D8',
  /** A half-step lighter than plaster, for cards lifted off the ground. */
  ivory: '#FBF7F0',
  /** Pale coir, the lit side of the undyed fibre. */
  coirLight: '#D9B486',
  /** Mid coir, the dominant tone across the catalogue. */
  coir: '#A8743F',
  /** Coconut husk, the deepest fibre tone. */
  coconut: '#8A5A32',
  /** The dyed roof red. The single accent - nothing else competes with it. */
  terracotta: '#A33B2E',
  /** Darker terracotta for hover and pressed states. */
  terracottaDeep: '#832E23',
  /** Reserved for in-stock and success. Semantic, never decorative. */
  forest: '#4C5B47',
  /** Body copy. Above 4.5:1 on plaster and on ivory. */
  charcoal: '#33322E',
  /** Secondary copy and captions. */
  charcoalMuted: '#6B6357',
  /** Hairlines and dividers. */
  rule: '#E0D4C0',
  /**
   * Out-of-stock and disabled states.
   *
   * Darkened from #9A9186, which measured 2.91:1 on ivory. That is acceptable
   * for a genuinely inactive control but not for the out-of-stock badge, which
   * is information a customer needs. This clears 4.5:1 on both grounds.
   */
  stone: '#70675C',
} as const;

export type PaletteKey = keyof typeof palette;

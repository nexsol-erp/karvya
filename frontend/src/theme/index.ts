import { createTheme, darken, getContrastRatio, alpha } from '@mui/material/styles';
import { palette } from './palette';
import { DEFAULT_BODY_FONT, DEFAULT_HEADING_FONT, fontStack } from './fonts';

/** What an administrator can change from the Appearance screen. */
export interface ThemeOverrides {
  colourPrimary?: string | null;
  colourSecondary?: string | null;
  colourBackground?: string | null;
  colourSurface?: string | null;
  colourText?: string | null;
  fontHeading?: string | null;
  fontBody?: string | null;
  cornerRadius?: number | null;
}

/** A hex colour, or the built-in default when the setting is empty or malformed. */
function colour(value: string | null | undefined, fallback: string): string {
  return value && /^#[0-9a-fA-F]{6}$/.test(value) ? value : fallback;
}

/**
 * Black or white, whichever is legible on the given background.
 *
 * <p>Chosen rather than configured. An administrator picking a pale accent
 * would otherwise get white text on it and an unreadable button, and that is
 * not a trade-off worth exposing - there is only one right answer for each
 * background.
 */
function readableOn(background: string): string {
  return getContrastRatio(background, '#FFFFFF') >= 4.5 ? '#FFFFFF' : palette.charcoal;
}

/**
 * The storefront theme.
 *
 * <p>Terracotta is the only accent and appears on primary actions alone.
 * Forest green is kept for in-stock and success so that semantic colour never
 * collides with brand colour - a green button and an "in stock" badge would
 * otherwise say the same thing in two different registers.
 *
 * <p>Colours and fonts come from site settings. Everything derived from them -
 * the hover shade, muted text, rules, the focus ring - is computed here rather
 * than configured, so an owner changes five values and the rest stays coherent.
 */
export function buildTheme(overrides: ThemeOverrides = {}) {
  const primary = colour(overrides.colourPrimary, palette.terracotta);
  const secondary = colour(overrides.colourSecondary, palette.coir);
  const background = colour(overrides.colourBackground, palette.plaster);
  const surface = colour(overrides.colourSurface, palette.ivory);
  const text = colour(overrides.colourText, palette.charcoal);

  const display = fontStack(overrides.fontHeading, DEFAULT_HEADING_FONT);
  const body = fontStack(overrides.fontBody, DEFAULT_BODY_FONT);

  const radius = Math.min(32, Math.max(0, overrides.cornerRadius ?? 12));

  // muted text and rules are the chosen colours faded toward each other, so a
  // change of palette does not leave the old greys behind
  const textMuted = alpha(text, 0.72);
  const textFaint = alpha(text, 0.55);
  const rule = alpha(text, 0.14);

  return createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: primary,
      dark: darken(primary, 0.18),
      contrastText: readableOn(primary),
    },
    secondary: {
      main: secondary,
      dark: darken(secondary, 0.18),
      contrastText: readableOn(secondary),
    },
    success: {
      main: palette.forest,
    },
    background: {
      default: background,
      paper: surface,
    },
    text: {
      primary: text,
      secondary: textMuted,
      disabled: textFaint,
    },
    divider: rule,
  },

  shape: { borderRadius: radius },

  typography: {
    fontFamily: body,
    h1: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1.06 },
    h2: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.015em', lineHeight: 1.12 },
    h3: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.01em', lineHeight: 1.2 },
    h4: { fontFamily: display, fontWeight: 600, lineHeight: 1.25 },
    h5: { fontFamily: body, fontWeight: 700 },
    h6: { fontFamily: body, fontWeight: 700 },
    subtitle1: { color: textMuted },
    body1: { lineHeight: 1.65 },
    body2: { lineHeight: 1.6, color: textMuted },
    button: { textTransform: 'none', fontWeight: 600, letterSpacing: '0.01em' },
    overline: { letterSpacing: '0.16em', fontWeight: 600, fontSize: '0.7rem' },
  },

  components: {
    MuiCssBaseline: {
      styleOverrides: {
        // Motion is opt-out for anyone who has asked their system to reduce it.
        '@media (prefers-reduced-motion: reduce)': {
          '*': {
            animationDuration: '0.01ms !important',
            transitionDuration: '0.01ms !important',
            scrollBehavior: 'auto !important',
          },
        },
        body: { backgroundColor: background },
        // A visible focus ring everywhere, not just where MUI happens to add one.
        ':focus-visible': {
          outline: `2px solid ${primary}`,
          outlineOffset: '3px',
        },
      },
    },

    MuiButton: {
      defaultProps: { disableElevation: true },
      styleOverrides: {
        // v9 dropped the variant+colour override keys. The darker hover comes
        // from palette.primary.dark, which is set to terracottaDeep above.
        root: { borderRadius: 999, paddingInline: 22, paddingBlock: 10 },
        outlined: { borderColor: rule },
      },
    },

    MuiCard: {
      styleOverrides: {
        root: {
          border: `1px solid ${rule}`,
          boxShadow: '0 1px 2px rgba(51,50,46,0.04), 0 10px 28px -18px rgba(51,50,46,0.28)',
        },
      },
    },

    MuiChip: {
      styleOverrides: {
        root: { fontWeight: 600, letterSpacing: '0.01em' },
      },
    },

    MuiLink: {
      defaultProps: { underline: 'hover' },
      styleOverrides: { root: { color: primary } },
    },

    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'transparent' },
    },
  },
  });
}

/** The built-in look, used before settings arrive and by tests. */
export const theme = buildTheme();

export { palette };

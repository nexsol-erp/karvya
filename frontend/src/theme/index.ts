import { createTheme } from '@mui/material/styles';
import { palette } from './palette';

const display = '"Fraunces", "Iowan Old Style", Georgia, serif';
const body = '"Karla", "Helvetica Neue", Arial, sans-serif';

/**
 * The storefront theme.
 *
 * <p>Terracotta is the only accent and appears on primary actions alone.
 * Forest green is kept for in-stock and success so that semantic colour never
 * collides with brand colour - a green button and an "in stock" badge would
 * otherwise say the same thing in two different registers.
 */
export const theme = createTheme({
  palette: {
    mode: 'light',
    primary: {
      main: palette.terracotta,
      dark: palette.terracottaDeep,
      contrastText: '#FFFFFF',
    },
    secondary: {
      main: palette.coir,
      dark: palette.coconut,
      contrastText: '#FFFFFF',
    },
    success: {
      main: palette.forest,
    },
    background: {
      default: palette.plaster,
      paper: palette.ivory,
    },
    text: {
      primary: palette.charcoal,
      secondary: palette.charcoalMuted,
      disabled: palette.stone,
    },
    divider: palette.rule,
  },

  shape: { borderRadius: 12 },

  typography: {
    fontFamily: body,
    h1: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.02em', lineHeight: 1.06 },
    h2: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.015em', lineHeight: 1.12 },
    h3: { fontFamily: display, fontWeight: 600, letterSpacing: '-0.01em', lineHeight: 1.2 },
    h4: { fontFamily: display, fontWeight: 600, lineHeight: 1.25 },
    h5: { fontFamily: body, fontWeight: 700 },
    h6: { fontFamily: body, fontWeight: 700 },
    subtitle1: { color: palette.charcoalMuted },
    body1: { lineHeight: 1.65 },
    body2: { lineHeight: 1.6, color: palette.charcoalMuted },
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
        body: { backgroundColor: palette.plaster },
        // A visible focus ring everywhere, not just where MUI happens to add one.
        ':focus-visible': {
          outline: `2px solid ${palette.terracotta}`,
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
        outlined: { borderColor: palette.rule },
      },
    },

    MuiCard: {
      styleOverrides: {
        root: {
          border: `1px solid ${palette.rule}`,
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
      styleOverrides: { root: { color: palette.terracotta } },
    },

    MuiAppBar: {
      defaultProps: { elevation: 0, color: 'transparent' },
    },
  },
});

export { palette };

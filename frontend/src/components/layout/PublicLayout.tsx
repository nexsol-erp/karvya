import Box from '@mui/material/Box';
import Link from '@mui/material/Link';
import { Outlet } from 'react-router-dom';

import { Header } from './Header';
import { Footer } from './Footer';
import { FloatingWhatsApp } from './FloatingWhatsApp';

/**
 * Shell for every public page.
 *
 * <p>Opens with a skip link, which is the first thing a keyboard or screen
 * reader user meets. It is visually hidden until focused, then appears in
 * place - the pattern that actually works, as opposed to a link that is
 * permanently off-screen and therefore never reachable.
 */
export function PublicLayout() {
  return (
    <Box sx={{ minHeight: '100dvh', display: 'flex', flexDirection: 'column' }}>
      <Link
        href="#main"
        sx={{
          position: 'absolute',
          left: 12,
          top: -60,
          zIndex: 2000,
          px: 2,
          py: 1.25,
          borderRadius: 2,
          bgcolor: 'primary.main',
          color: 'primary.contrastText',
          fontWeight: 700,
          textDecoration: 'none',
          transition: 'top 160ms ease',
          '&:focus': { top: 12 },
        }}
      >
        Skip to content
      </Link>

      <Header />

      <Box component="main" id="main" sx={{ flexGrow: 1 }}>
        <Outlet />
      </Box>

      <Footer />
      <FloatingWhatsApp />
    </Box>
  );
}

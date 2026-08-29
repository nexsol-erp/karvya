import { useState } from 'react';
import AppBar from '@mui/material/AppBar';
import Toolbar from '@mui/material/Toolbar';
import Container from '@mui/material/Container';
import Box from '@mui/material/Box';
import Stack from '@mui/material/Stack';
import Button from '@mui/material/Button';
import IconButton from '@mui/material/IconButton';
import Drawer from '@mui/material/Drawer';
import List from '@mui/material/List';
import ListItemButton from '@mui/material/ListItemButton';
import ListItemText from '@mui/material/ListItemText';
import Typography from '@mui/material/Typography';
import MenuIcon from '@mui/icons-material/Menu';
import CloseIcon from '@mui/icons-material/Close';
import ShoppingBagOutlinedIcon from '@mui/icons-material/ShoppingBagOutlined';
import Badge from '@mui/material/Badge';
import { Link as RouterLink, NavLink, useLocation } from 'react-router-dom';

import { useSiteSettings } from '../../hooks/useSiteSettings';
import { useCart } from '../../hooks/useCart';

/**
 * Navigation.
 *
 * <p>Only routes that exist are listed. Cart joins this array when the cart
 * lands - a link to an unbuilt page is worse than no link. Account points at
 * the account area either way: a signed-out visitor is sent to sign in and
 * returned here afterwards.
 */
const NAV = [
  { label: 'Home', to: '/' },
  { label: 'Shop', to: '/shop' },
  { label: 'Our Story', to: '/our-story' },
  { label: 'Contact', to: '/contact' },
  { label: 'Account', to: '/account' },
];

export function Header() {
  const [open, setOpen] = useState(false);
  const location = useLocation();
  const { cart } = useCart();
  const settings = useSiteSettings();

  return (
    <AppBar
      position="sticky"
      sx={{
        bgcolor: 'rgba(240, 231, 216, 0.88)',
        backdropFilter: 'blur(10px)',
        borderBottom: 1,
        borderColor: 'divider',
      }}
    >
      <Container maxWidth="lg">
        <Toolbar disableGutters sx={{ minHeight: { xs: 62, md: 74 }, gap: 2 }}>
          <Box
            component={RouterLink}
            to="/"
            sx={{ textDecoration: 'none', color: 'text.primary', mr: 'auto' }}
          >
            <Typography
              component="span"
              sx={{
                fontFamily: '"Fraunces", Georgia, serif',
                fontWeight: 600,
                fontSize: { xs: '1.4rem', md: '1.6rem' },
                letterSpacing: '-0.01em',
                display: 'block',
                lineHeight: 1.1,
              }}
            >
              {settings.storeName}
            </Typography>
            <Typography
              component="span"
              sx={{
                fontSize: 11,
                letterSpacing: '0.14em',
                textTransform: 'uppercase',
                color: 'text.secondary',
                display: { xs: 'none', sm: 'block' },
              }}
            >
              {settings.tagline}
            </Typography>
          </Box>

          <Stack direction="row" spacing={0.5} sx={{ display: { xs: 'none', md: 'flex' } }}>
            {NAV.map((item) => {
              const active =
                item.to === '/' ? location.pathname === '/' : location.pathname.startsWith(item.to);
              return (
                <Button
                  key={item.to}
                  component={NavLink}
                  to={item.to}
                  sx={{
                    color: active ? 'primary.main' : 'text.primary',
                    fontWeight: active ? 700 : 600,
                    px: 1.75,
                  }}
                >
                  {item.label}
                </Button>
              );
            })}
          </Stack>

          <IconButton
            component={RouterLink}
            to="/cart"
            aria-label={
              cart.itemCount > 0
                ? `Cart, ${cart.itemCount} ${cart.itemCount === 1 ? 'item' : 'items'}`
                : 'Cart, empty'
            }
          >
            <Badge badgeContent={cart.itemCount} color="primary" overlap="circular">
              <ShoppingBagOutlinedIcon />
            </Badge>
          </IconButton>

          <IconButton
            onClick={() => setOpen(true)}
            sx={{ display: { xs: 'inline-flex', md: 'none' } }}
            aria-label="Open menu"
          >
            <MenuIcon />
          </IconButton>
        </Toolbar>
      </Container>

      <Drawer
        anchor="right"
        open={open}
        onClose={() => setOpen(false)}
        slotProps={{ paper: { sx: { width: 268, bgcolor: 'background.default' } } }}
      >
        <Stack direction="row" sx={{ justifyContent: 'flex-end', p: 1 }}>
          <IconButton onClick={() => setOpen(false)} aria-label="Close menu">
            <CloseIcon />
          </IconButton>
        </Stack>
        <List>
          {NAV.map((item) => (
            <ListItemButton
              key={item.to}
              component={RouterLink}
              to={item.to}
              onClick={() => setOpen(false)}
            >
              <ListItemText
                primary={item.label}
                slotProps={{ primary: { sx: { fontWeight: 600 } } }}
              />
            </ListItemButton>
          ))}
        </List>
      </Drawer>
    </AppBar>
  );
}

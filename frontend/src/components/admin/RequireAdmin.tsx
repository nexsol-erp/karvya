import { Navigate, Outlet, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';

import { useAuth } from '../../hooks/useAuth';

/**
 * Gate for the back office.
 *
 * <p>Three states, in order. Still checking: show nothing but a spinner rather
 * than flashing a login form at somebody who is already signed in. Not an
 * administrator: send them to the admin sign-in, remembering where they were
 * headed. Owing a password change: send them there and nowhere else.
 *
 * <p>None of this is a security control - every admin endpoint is authorised
 * on the server, and the same password-change rule is enforced by a filter
 * there. This only decides what the browser shows.
 */
export function RequireAdmin() {
  const { user, isLoading, isSignedIn } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '60vh' }}>
        <CircularProgress aria-label="Checking your session" />
      </Box>
    );
  }

  if (!isSignedIn || !user?.roles.includes('ROLE_ADMIN')) {
    return (
      <Navigate
        to="/admin/login"
        replace
        state={{ from: location.pathname + location.search }}
      />
    );
  }

  if (user.mustChangePassword && location.pathname !== '/admin/change-password') {
    return <Navigate to="/admin/change-password" replace />;
  }

  return <Outlet />;
}

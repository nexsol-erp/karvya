import { Navigate, Outlet, useLocation } from 'react-router-dom';
import Box from '@mui/material/Box';
import CircularProgress from '@mui/material/CircularProgress';

import { useAuth } from '../../hooks/useAuth';

/**
 * Gate for the account area.
 *
 * <p>This is a convenience, not a control. Every account endpoint is
 * authorised on the server and scoped to the owner there; removing this
 * component would change what a visitor sees, not what they can reach.
 *
 * <p>The visited path is remembered so signing in returns the visitor where
 * they were going rather than dumping them on a dashboard.
 */
export function RequireAuth() {
  const { isSignedIn, isLoading } = useAuth();
  const location = useLocation();

  if (isLoading) {
    return (
      <Box sx={{ display: 'grid', placeItems: 'center', minHeight: '40vh' }}>
        <CircularProgress aria-label="Checking your session" />
      </Box>
    );
  }

  if (!isSignedIn) {
    return <Navigate to="/login" replace state={{ from: location.pathname + location.search }} />;
  }

  return <Outlet />;
}

import { createContext, useContext, useMemo, type ReactNode } from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import { authKeys, getCurrentUser, login as loginRequest, logout as logoutRequest, type CurrentUser } from '../api/auth';
import { ApiError } from '../api/client';

interface AuthState {
  user: CurrentUser | null;
  isLoading: boolean;
  isSignedIn: boolean;
  signIn: (email: string, password: string) => Promise<CurrentUser>;
  signOut: () => Promise<void>;
  refresh: () => Promise<void>;
}

const AuthContext = createContext<AuthState | null>(null);

/**
 * Session state for the storefront.
 *
 * <p>There is no token to hold: the session lives in an HttpOnly cookie the
 * browser sends on its own, and this only mirrors who that cookie belongs to.
 * A 401 from /me is the normal signed-out answer rather than an error, so it
 * is not retried and does not surface as a failure.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const queryClient = useQueryClient();

  const { data, isLoading } = useQuery({
    queryKey: authKeys.me,
    queryFn: getCurrentUser,
    retry: false,
    staleTime: 5 * 60_000,
    // an unauthenticated visitor is a normal state, not a failed request
    throwOnError: false,
  });

  const value = useMemo<AuthState>(() => ({
    user: data ?? null,
    isLoading,
    isSignedIn: Boolean(data),

    async signIn(email, password) {
      const user = await loginRequest({ email, password });
      queryClient.setQueryData(authKeys.me, user);
      // a signed-in shopper may have a server cart and their own orders
      await queryClient.invalidateQueries({ queryKey: ['account'] });
      return user;
    },

    async signOut() {
      try {
        await logoutRequest();
      } catch (error) {
        // an already-expired session is not worth surfacing; the local state
        // is cleared either way
        if (!(error instanceof ApiError) || error.status !== 401) throw error;
      }
      queryClient.setQueryData(authKeys.me, null);
      queryClient.removeQueries({ queryKey: ['account'] });
    },

    async refresh() {
      await queryClient.invalidateQueries({ queryKey: authKeys.me });
    },
  }), [data, isLoading, queryClient]);

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

export function useAuth(): AuthState {
  const context = useContext(AuthContext);
  if (!context) {
    throw new Error('useAuth must be used inside an AuthProvider');
  }
  return context;
}

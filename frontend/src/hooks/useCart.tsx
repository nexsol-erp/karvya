import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useRef,
  useState,
  type ReactNode,
} from 'react';
import { useQuery, useQueryClient } from '@tanstack/react-query';

import {
  EMPTY_CART,
  cartKeys,
  clearServerCart,
  getServerCart,
  mergeGuestCart,
  removeServerCartItem,
  setServerCartItem,
  validateCart,
  type CartLineInput,
  type CartView,
} from '../api/cart';
import { useAuth } from './useAuth';

const STORAGE_KEY = 'karvya.cart.v1';

interface CartState {
  cart: CartView;
  isLoading: boolean;
  /** Quantity of one product, for the button on a product page. */
  quantityOf: (productId: number) => number;
  setQuantity: (productId: number, quantity: number) => Promise<void>;
  addToCart: (productId: number, quantity?: number) => Promise<void>;
  removeItem: (productId: number) => Promise<void>;
  clear: () => Promise<void>;
  /** Corrections the server made, once acknowledged they can be dismissed. */
  dismissAdjustments: () => void;
}

const CartContext = createContext<CartState | null>(null);

function readStoredLines(): CartLineInput[] {
  try {
    const raw = window.localStorage.getItem(STORAGE_KEY);
    if (!raw) return [];
    const parsed: unknown = JSON.parse(raw);
    if (!Array.isArray(parsed)) return [];
    // storage is user-writable, so treat anything malformed as an empty cart
    return parsed
      .filter(
        (line): line is CartLineInput =>
          typeof line === 'object' &&
          line !== null &&
          Number.isFinite((line as CartLineInput).productId) &&
          Number.isFinite((line as CartLineInput).quantity),
      )
      .filter((line) => line.quantity > 0);
  } catch {
    return [];
  }
}

function writeStoredLines(lines: CartLineInput[]) {
  try {
    window.localStorage.setItem(STORAGE_KEY, JSON.stringify(lines));
  } catch {
    // a full or disabled storage must not break shopping; the cart simply
    // lasts only as long as the tab
  }
}

/**
 * The cart, for visitors and signed-in customers alike.
 *
 * <p>A visitor's cart lives in localStorage as identifiers and quantities and
 * is priced by the server on every change - the browser never computes a
 * total. A signed-in customer's cart lives in the database instead, and the
 * two are folded together at sign-in.
 *
 * <p>Both paths surface the same {@link CartView}, so nothing downstream needs
 * to know which one is in play.
 */
export function CartProvider({ children }: { children: ReactNode }) {
  const { isSignedIn, isLoading: authLoading } = useAuth();
  const queryClient = useQueryClient();

  const [guestLines, setGuestLines] = useState<CartLineInput[]>(() => readStoredLines());
  const [dismissed, setDismissed] = useState(false);
  const mergedForSession = useRef(false);

  useEffect(() => {
    writeStoredLines(guestLines);
  }, [guestLines]);

  const guestCart = useQuery({
    queryKey: cartKeys.guest(guestLines),
    queryFn: () => validateCart(guestLines),
    enabled: !authLoading && !isSignedIn,
    staleTime: 30_000,
  });

  const serverCart = useQuery({
    queryKey: cartKeys.server,
    queryFn: getServerCart,
    enabled: !authLoading && isSignedIn,
  });

  /**
   * Folds the browser cart in once per sign-in, then empties it. The ref
   * guards against a re-render repeating the merge, which would double every
   * quantity.
   */
  useEffect(() => {
    if (!isSignedIn) {
      mergedForSession.current = false;
      return;
    }
    if (mergedForSession.current) return;

    mergedForSession.current = true;
    const pending = readStoredLines();
    if (pending.length === 0) return;

    mergeGuestCart(pending)
      .then((merged) => {
        queryClient.setQueryData(cartKeys.server, merged);
        setGuestLines([]);
      })
      .catch(() => {
        // leave the browser cart alone so nothing is lost; the next sign-in
        // will try again
        mergedForSession.current = false;
      });
  }, [isSignedIn, queryClient]);

  const cart = (isSignedIn ? serverCart.data : guestCart.data) ?? EMPTY_CART;

  const applyServerCart = useCallback(
    (updated: CartView) => {
      queryClient.setQueryData(cartKeys.server, updated);
    },
    [queryClient],
  );

  const setQuantity = useCallback(
    async (productId: number, quantity: number) => {
      setDismissed(false);
      if (isSignedIn) {
        applyServerCart(await setServerCartItem(productId, Math.max(0, quantity)));
        return;
      }
      setGuestLines((lines) => {
        const without = lines.filter((line) => line.productId !== productId);
        return quantity > 0 ? [...without, { productId, quantity }] : without;
      });
    },
    [isSignedIn, applyServerCart],
  );

  const value = useMemo<CartState>(() => {
    const visible: CartView = dismissed ? { ...cart, adjustments: [] } : cart;

    return {
      cart: visible,
      isLoading: authLoading || (isSignedIn ? serverCart.isPending : guestCart.isPending),

      quantityOf: (productId) =>
        cart.lines.find((line) => line.productId === productId)?.quantity ?? 0,

      setQuantity,

      async addToCart(productId, quantity = 1) {
        const current = cart.lines.find((line) => line.productId === productId)?.quantity ?? 0;
        await setQuantity(productId, current + quantity);
      },

      async removeItem(productId) {
        setDismissed(false);
        if (isSignedIn) {
          applyServerCart(await removeServerCartItem(productId));
          return;
        }
        setGuestLines((lines) => lines.filter((line) => line.productId !== productId));
      },

      async clear() {
        setDismissed(false);
        if (isSignedIn) {
          applyServerCart(await clearServerCart());
          return;
        }
        setGuestLines([]);
      },

      dismissAdjustments: () => setDismissed(true),
    };
  }, [
    cart,
    dismissed,
    authLoading,
    isSignedIn,
    serverCart.isPending,
    guestCart.isPending,
    setQuantity,
    applyServerCart,
  ]);

  return <CartContext.Provider value={value}>{children}</CartContext.Provider>;
}

export function useCart(): CartState {
  const context = useContext(CartContext);
  if (!context) {
    throw new Error('useCart must be used inside a CartProvider');
  }
  return context;
}

import { lazy, Suspense, useEffect } from 'react';
import { BrowserRouter, Routes, Route, useLocation } from 'react-router-dom';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { ThemeProvider } from '@mui/material/styles';
import CssBaseline from '@mui/material/CssBaseline';
import LinearProgress from '@mui/material/LinearProgress';

import { theme } from './theme';
import { PublicLayout } from './components/layout/PublicLayout';
import { RequireAuth } from './components/common/RequireAuth';
import { RequireAdmin } from './components/admin/RequireAdmin';
import { AdminLayout } from './components/admin/AdminLayout';
import { AuthProvider } from './hooks/useAuth';
import { CartProvider } from './hooks/useCart';
import { Landing } from './pages/Landing';

// Split by route. The landing page ships in the entry chunk because it is
// almost always the first thing requested; everything else loads on demand.
const Shop = lazy(() => import('./pages/Shop').then((m) => ({ default: m.Shop })));
const ProductDetail = lazy(() =>
  import('./pages/ProductDetail').then((m) => ({ default: m.ProductDetail })),
);
const OurStory = lazy(() => import('./pages/OurStory').then((m) => ({ default: m.OurStory })));
const Contact = lazy(() => import('./pages/Contact').then((m) => ({ default: m.Contact })));
const NotFound = lazy(() => import('./pages/NotFound').then((m) => ({ default: m.NotFound })));
const Checkout = lazy(() => import('./pages/Checkout').then((m) => ({ default: m.Checkout })));
const OrderConfirmation = lazy(() =>
  import('./pages/OrderConfirmation').then((m) => ({ default: m.OrderConfirmation })),
);
// The back office is a separate route tree with its own layout, and every
// screen is lazily loaded so none of it ships to a shopper.
const AdminLogin = lazy(() => import('./pages/admin/AdminLogin').then((m) => ({ default: m.AdminLogin })));
const AdminChangePassword = lazy(() =>
  import('./pages/admin/AdminChangePassword').then((m) => ({ default: m.AdminChangePassword })),
);
const AdminDashboard = lazy(() =>
  import('./pages/admin/AdminDashboard').then((m) => ({ default: m.AdminDashboard })),
);
const AdminOrders = lazy(() => import('./pages/admin/AdminOrders').then((m) => ({ default: m.AdminOrders })));
const AdminOrderDetail = lazy(() =>
  import('./pages/admin/AdminOrderDetail').then((m) => ({ default: m.AdminOrderDetail })),
);
const AdminProducts = lazy(() =>
  import('./pages/admin/AdminProducts').then((m) => ({ default: m.AdminProducts })),
);
const AdminEnquiries = lazy(() =>
  import('./pages/admin/AdminEnquiries').then((m) => ({ default: m.AdminEnquiries })),
);
const AdminCustomers = lazy(() =>
  import('./pages/admin/AdminCustomers').then((m) => ({ default: m.AdminCustomers })),
);
const AdminSettings = lazy(() =>
  import('./pages/admin/AdminSettings').then((m) => ({ default: m.AdminSettings })),
);
const Cart = lazy(() => import('./pages/Cart').then((m) => ({ default: m.Cart })));
const Account = lazy(() => import('./pages/Account').then((m) => ({ default: m.Account })));
const Login = lazy(() => import('./pages/Login').then((m) => ({ default: m.Login })));
const Register = lazy(() => import('./pages/Register').then((m) => ({ default: m.Register })));
const ForgotPassword = lazy(() =>
  import('./pages/ForgotPassword').then((m) => ({ default: m.ForgotPassword })),
);
const ResetPassword = lazy(() =>
  import('./pages/ResetPassword').then((m) => ({ default: m.ResetPassword })),
);

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      staleTime: 60_000,
      refetchOnWindowFocus: false,
      retry: 1,
    },
  },
});

/**
 * Returns to the top on navigation, but leaves the browser to restore position
 * on back and forward, where the reader expects to land where they left.
 */
function ScrollToTop() {
  const { pathname } = useLocation();
  const navigationType = typeof window !== 'undefined' ? window.performance : null;

  useEffect(() => {
    if (navigationType) {
      window.scrollTo({ top: 0, behavior: 'instant' as ScrollBehavior });
    }
  }, [pathname, navigationType]);

  return null;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <ThemeProvider theme={theme}>
        <CssBaseline />
        <AuthProvider>
        <CartProvider>
        <BrowserRouter>
          <ScrollToTop />
          <Suspense fallback={<LinearProgress color="primary" />}>
            <Routes>
              <Route element={<PublicLayout />}>
                <Route index element={<Landing />} />
                <Route path="shop" element={<Shop />} />
                <Route path="shop/:categorySlug" element={<Shop />} />
                <Route path="product/:slug" element={<ProductDetail />} />
                <Route path="our-story" element={<OurStory />} />
                <Route path="contact" element={<Contact />} />
                <Route path="cart" element={<Cart />} />
                <Route path="checkout" element={<Checkout />} />
                <Route path="order/:orderNumber" element={<OrderConfirmation />} />

                <Route path="login" element={<Login />} />
                <Route path="register" element={<Register />} />
                <Route path="forgot-password" element={<ForgotPassword />} />
                <Route path="reset-password" element={<ResetPassword />} />

                {/* everything below is also authorised server-side */}
                <Route element={<RequireAuth />}>
                  <Route path="account" element={<Account />} />
                </Route>

                <Route path="*" element={<NotFound />} />
              </Route>

              {/* ---- back office ---- */}
              <Route path="/admin/login" element={<AdminLogin />} />
              <Route element={<RequireAdmin />}>
                <Route path="/admin/change-password" element={<AdminChangePassword />} />
                <Route path="/admin" element={<AdminLayout />}>
                  <Route index element={<AdminDashboard />} />
                  <Route path="orders" element={<AdminOrders />} />
                  <Route path="orders/:orderNumber" element={<AdminOrderDetail />} />
                  <Route path="products" element={<AdminProducts />} />
                  <Route path="enquiries" element={<AdminEnquiries />} />
                  <Route path="customers" element={<AdminCustomers />} />
                  <Route path="settings" element={<AdminSettings />} />
                </Route>
              </Route>
            </Routes>
          </Suspense>
        </BrowserRouter>
        </CartProvider>
        </AuthProvider>
      </ThemeProvider>
    </QueryClientProvider>
  );
}

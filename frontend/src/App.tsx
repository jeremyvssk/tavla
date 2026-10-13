// Routes and session bootstrap. SPA paths avoid the API prefixes nginx proxies (/products, /auth, ...).
import { lazy, Suspense, useEffect } from 'react';
import { Navigate, Route, Routes } from 'react-router-dom';
import { setSessionExpiredHandler } from './api/client';
import { restoreSession } from './auth/session';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import AboutPage from './pages/AboutPage';
import AccountPage from './pages/AccountPage';
import AddressesPage from './pages/AddressesPage';
import CartPage from './pages/CartPage';
import CatalogPage from './pages/CatalogPage';
import CheckoutPage from './pages/CheckoutPage';
import ContactPage from './pages/ContactPage';
import FaqPage from './pages/FaqPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import HomePage from './pages/HomePage';
import LegalPage from './pages/LegalPage';
import LoginPage from './pages/LoginPage';
import NotFoundPage from './pages/NotFoundPage';
import OrderPage from './pages/OrderPage';
import OrdersPage from './pages/OrdersPage';
import OrderStatusPage from './pages/OrderStatusPage';
import ProductPage from './pages/ProductPage';
import RegisterPage from './pages/RegisterPage';
import SecurityPage from './pages/SecurityPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import { useAppDispatch } from './store';
import { signedOut } from './store/authSlice';

// staff only: shoppers never download the admin's code
const AdminApp = lazy(() => import('./admin/AdminApp'));

export default function App() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    setSessionExpiredHandler(() => dispatch(signedOut()));
    restoreSession(dispatch);
  }, [dispatch]);

  return (
    <Routes>
      {/* Checkout has its own slim header: no menu or search to wander off to. Not /orders or /cart,
          which nginx sends to the backend. */}
      <Route path="checkout" element={<CheckoutPage />} />
      <Route path="admin/*" element={<Suspense fallback={<p className="status-line adm-wait">Loading…</p>}><AdminApp /></Suspense>} />
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        {/* Not /products: nginx sends that path to the backend, so a reload there would show JSON. */}
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="catalog/:id/:slug?" element={<ProductPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="forgot" element={<ForgotPasswordPage />} />
        {/* The password-reset email links here (FRONTEND_RESET_URL). */}
        <Route path="reset" element={<ResetPasswordPage />} />
        <Route path="account" element={<RequireAuth><AccountPage /></RequireAuth>} />
        <Route path="account/orders" element={<RequireAuth><OrdersPage /></RequireAuth>} />
        <Route path="account/addresses" element={<RequireAuth><AddressesPage /></RequireAuth>} />
        <Route path="account/security" element={<RequireAuth><SecurityPage /></RequireAuth>} />
        <Route path="account/orders/:orderId" element={<RequireAuth><OrderPage /></RequireAuth>} />
        {/* where Pay lands, and Stripe's return_url: waiting, then confirmed or failed; guests too */}
        <Route path="checkout/:orderId" element={<OrderStatusPage />} />
        {/* not /cart: nginx sends that to the backend */}
        <Route path="bag" element={<CartPage />} />
        <Route path="contact" element={<ContactPage />} />
        <Route path="faq" element={<FaqPage />} />
        <Route path="about" element={<AboutPage />} />
        <Route path="legal" element={<Navigate to="/legal/terms" replace />} />
        <Route path="legal/:doc" element={<LegalPage />} />
        <Route path="*" element={<NotFoundPage />} />
      </Route>
    </Routes>
  );
}

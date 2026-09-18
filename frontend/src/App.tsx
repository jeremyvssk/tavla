// Routes and session bootstrap. SPA paths avoid the API prefixes nginx proxies (/products, /auth, ...).
import { useEffect } from 'react';
import { Link, Route, Routes } from 'react-router-dom';
import { setSessionExpiredHandler } from './api/client';
import { restoreSession } from './auth/session';
import Layout from './components/Layout';
import RequireAuth from './components/RequireAuth';
import AccountPage from './pages/AccountPage';
import CatalogPage from './pages/CatalogPage';
import ForgotPasswordPage from './pages/ForgotPasswordPage';
import HomePage from './pages/HomePage';
import LoginPage from './pages/LoginPage';
import ProductPage from './pages/ProductPage';
import RegisterPage from './pages/RegisterPage';
import ResetPasswordPage from './pages/ResetPasswordPage';
import { useAppDispatch } from './store';
import { signedOut } from './store/authSlice';

export default function App() {
  const dispatch = useAppDispatch();

  useEffect(() => {
    setSessionExpiredHandler(() => dispatch(signedOut()));
    restoreSession(dispatch);
  }, [dispatch]);

  return (
    <Routes>
      <Route element={<Layout />}>
        <Route index element={<HomePage />} />
        {/* Not /products: nginx sends that path to the backend, so a reload there would show JSON. */}
        <Route path="catalog" element={<CatalogPage />} />
        <Route path="catalog/:id" element={<ProductPage />} />
        <Route path="login" element={<LoginPage />} />
        <Route path="register" element={<RegisterPage />} />
        <Route path="forgot" element={<ForgotPasswordPage />} />
        {/* The password-reset email links here (FRONTEND_RESET_URL). */}
        <Route path="reset" element={<ResetPasswordPage />} />
        <Route path="account" element={<RequireAuth><AccountPage /></RequireAuth>} />
        <Route
          path="*"
          element={
            <div className="empty">
              <p className="label">404</p>
              <h1 className="title">Nothing here</h1>
              <Link to="/">Go home</Link>
            </div>
          }
        />
      </Route>
    </Routes>
  );
}

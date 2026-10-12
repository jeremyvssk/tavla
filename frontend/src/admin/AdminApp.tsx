// The admin area, a lazy-loaded chunk under /admin: shoppers never download it. Staff only, and only with two-factor on.
import { Navigate, Route, Routes, useLocation } from 'react-router-dom';
import { useAppSelector } from '../store';
import { isStaff } from './access';
import AdminLayout from './AdminLayout';
import AuditPage from './pages/AuditPage';
import CategoriesPage from './pages/CategoriesPage';
import DeliveryPage from './pages/DeliveryPage';
import ImportPage from './pages/ImportPage';
import OrderPage from './pages/OrderPage';
import OrdersPage from './pages/OrdersPage';
import OverviewPage from './pages/OverviewPage';
import PackingSlipsPage from './pages/PackingSlipsPage';
import ProductEditPage from './pages/ProductEditPage';
import ProductsPage from './pages/ProductsPage';
import ReviewsPage from './pages/ReviewsPage';
import UsersPage from './pages/UsersPage';
import TwoFactorGate from './TwoFactorGate';
import { Guard } from './parts';

export default function AdminApp() {
  const { status, user } = useAppSelector((s) => s.auth);
  const location = useLocation();
  if (status === 'restoring') return <p className="status-line adm-wait">Loading…</p>;
  if (!user) return <Navigate to="/login" replace state={{ from: location.pathname }} />;
  // a shopper gets the shop's 404, so the admin isn't advertised
  if (!isStaff(user.role)) return <Navigate to="/not-found" replace />;
  if (!user.twoFactorEnabled) return <TwoFactorGate />;
  return (
    <Routes>
      <Route element={<AdminLayout />}>
        <Route index element={<OverviewPage />} />
        <Route path="orders" element={<Guard section="orders"><OrdersPage /></Guard>} />
        <Route path="orders/slips" element={<Guard section="orders"><PackingSlipsPage /></Guard>} />
        <Route path="orders/:id" element={<Guard section="orders"><OrderPage /></Guard>} />
        <Route path="delivery" element={<Guard section="delivery"><DeliveryPage /></Guard>} />
        <Route path="products" element={<Guard section="products"><ProductsPage /></Guard>} />
        <Route path="products/new" element={<Guard section="products"><ProductEditPage /></Guard>} />
        <Route path="products/:id" element={<Guard section="products"><ProductEditPage /></Guard>} />
        <Route path="categories" element={<Guard section="categories"><CategoriesPage /></Guard>} />
        <Route path="import" element={<Guard section="import"><ImportPage /></Guard>} />
        <Route path="reviews" element={<Guard section="reviews"><ReviewsPage /></Guard>} />
        <Route path="users" element={<Guard section="users"><UsersPage /></Guard>} />
        <Route path="audit" element={<Guard section="audit"><AuditPage /></Guard>} />
        <Route path="*" element={<Navigate to="/admin" replace />} />
      </Route>
    </Routes>
  );
}

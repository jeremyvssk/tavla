// Which staff role may open which admin section. Least privilege: Support runs orders and reviews, Sales the catalog.
// The UI only hides what a role can't use; the API checks the role on every call anyway.
import type { Role } from '../api/admin';

export type Section = 'overview' | 'orders' | 'delivery' | 'products' | 'categories' | 'import' | 'reviews' | 'users' | 'audit';

const CAN: Record<Section, Role[]> = {
  overview: ['ADMIN', 'SUPPORT', 'SALES'],
  orders: ['ADMIN', 'SUPPORT'],
  delivery: ['ADMIN', 'SALES'],
  products: ['ADMIN', 'SALES'],
  categories: ['ADMIN', 'SALES'],
  import: ['ADMIN', 'SALES'],
  reviews: ['ADMIN', 'SUPPORT'],
  users: ['ADMIN'],
  audit: ['ADMIN'],
};

export const can = (role: Role, section: Section) => CAN[section].includes(role);
export const isStaff = (role: Role) => role !== 'CUSTOMER';
export const ROLE_NAMES: Record<Role, string> = { CUSTOMER: 'Customer', ADMIN: 'Admin', SUPPORT: 'Support', SALES: 'Sales' };

// Typed calls for the admin area. Product, image, brand and category writes use endpoints that exist today;
// everything under /staff is PLANNED: the shape the admin pages expect, for the backend to implement.
// /staff and not /admin, because /admin is the admin's own SPA path and nginx can only route one of them.
import { isAxiosError } from 'axios';
import type { ImageFraming } from '../lib/framing';
import type { CategoryNode, CategoryRef, Page, Review } from './catalog';
import { searchProducts } from './catalog';
import { api } from './client';
import type { OrderDetail, OrderStatus, OrderSummary } from './orders';

export type StaffRole = 'ADMIN' | 'SUPPORT' | 'SALES';
export type Role = 'CUSTOMER' | StaffRole;

// ---- products (exist: POST/PUT/DELETE /products, POST/DELETE /products/{id}/images) ----

/** Mirrors the backend's ProductRequest: every field a client may write, metric only. */
export interface ProductInput {
  name: string;
  description: string | null;
  price: number;
  stockQuantity: number;
  categoryId: number;
  brandId: number | null;
  attributes: Record<string, unknown> | null;
  active: boolean;
  weightKg: number | null;
  widthCm: number | null;
  heightCm: number | null;
  depthCm: number | null;
}

export async function createProduct(input: ProductInput): Promise<{ id: string }> {
  return (await api.post('/products', input)).data;
}

export async function saveProduct(id: string, input: ProductInput) {
  return (await api.put(`/products/${encodeURIComponent(id)}`, input)).data;
}

/** A product in past orders is archived (leaves the shop and search); a never-ordered one is deleted. */
export async function deleteProduct(id: string) {
  await api.delete(`/products/${encodeURIComponent(id)}`);
}

export async function uploadImage(productId: string, file: File, altText: string) {
  const body = new FormData();
  body.append('file', file);
  body.append('altText', altText);
  return (await api.post(`/products/${encodeURIComponent(productId)}/images`, body)).data;
}

export async function deleteImage(productId: string, imageId: number) {
  await api.delete(`/products/${encodeURIComponent(productId)}/images/${imageId}`);
}

export type ProductState = 'all' | 'active' | 'hidden' | 'out' | 'low' | 'archived';

/** A row of the staff product list: hidden and archived ones too, with SKU and exact stock. */
export interface StaffProductRow {
  id: string;
  name: string;
  sku: string | null;
  brandName: string | null;
  categorySlug: string;
  price: number;
  /** exact; null where only "in stock or not" is known (the shop's own search) */
  stockQuantity: number | null;
  inStock: boolean;
  averageRating: number | null;
  reviewCount: number;
  state: 'ACTIVE' | 'HIDDEN' | 'ARCHIVED';
  primaryImageUrl: string | null;
  primaryImageFraming: ImageFraming | null;
}

export interface StaffProductParams {
  /** name or SKU */
  q?: string;
  state?: ProductState;
  category?: string;
  brand?: string;
  sort?: 'price_desc' | 'price_asc' | 'stock_asc' | 'stock_desc' | 'rating' | 'newest';
  page?: number;
  size?: number;
}

/**
 * PLANNED: GET /staff/products. Until it exists the shop's own search stands in: active products only,
 * 48 a page, no SKU or exact stock, no counts per state, and `planned` says so.
 */
export async function fetchStaffProducts(params: StaffProductParams): Promise<Page<StaffProductRow> & { counts: Record<ProductState, number> | null; planned: boolean }> {
  try {
    return { ...(await api.get('/staff/products', { params })).data, planned: false };
  } catch (err) {
    if (!isAxiosError(err) || err.response?.status !== 404) throw err;
    const size = Math.min(48, params.size ?? 48);
    const shop = await searchProducts({
      q: params.q, category: params.category, brand: params.brand ? [params.brand] : undefined,
      sort: params.sort?.startsWith('stock') ? 'newest' : params.sort, page: String(params.page ?? 0), size: String(size),
    });
    return {
      items: shop.items.map((p) => ({ ...p, sku: null, stockQuantity: null, state: 'ACTIVE' as const })),
      page: shop.page, size, totalItems: shop.totalItems, totalPages: shop.totalPages, counts: null, planned: true,
    };
  }
}

export type BulkProductChange =
  | { action: 'show' | 'hide' }
  | { action: 'move'; categoryId: number }
  | { action: 'price'; mode: 'set' | 'raise' | 'lower'; value: number };

/** PLANNED: one change to many products; Archive uses DELETE /products/{id}, which exists. */
export async function bulkProducts(ids: string[], change: BulkProductChange): Promise<{ changed: number }> {
  return (await api.post('/staff/products/bulk', { ids, ...change })).data;
}

/** PLANNED: the filtered list as a CSV file. */
export async function exportProducts(params: StaffProductParams): Promise<Blob> {
  return (await api.get('/staff/products/export', { params, responseType: 'blob' })).data;
}

export async function fetchBrands(): Promise<{ id: number; name: string; slug: string }[]> {
  return (await api.get('/brands')).data;
}

// ---- categories (exists: POST /categories; PLANNED: the staff tree, PUT, DELETE and moving) ----

/** A shelf as staff see it: hidden ones too, with how many products sit in it and the shelves below. */
export interface StaffCategory extends CategoryRef {
  parentId: number | null;
  active: boolean;
  productCount: number;
  children: StaffCategory[];
}

/** PLANNED: until it exists, the shop's own tree stands in (active shelves only, no counts). */
export async function fetchStaffCategories(): Promise<{ tree: StaffCategory[]; planned: boolean }> {
  try {
    return { tree: (await api.get('/staff/categories')).data, planned: false };
  } catch (err) {
    if (!isAxiosError(err) || err.response?.status !== 404) throw err;
    const shop: CategoryNode[] = (await api.get('/categories')).data;
    const map = (n: CategoryNode, parentId: number | null): StaffCategory =>
      ({ id: n.id, name: n.name, slug: n.slug, parentId, active: true, productCount: -1, children: n.children.map((c) => map(c, n.id)) });
    return { tree: shop.map((n) => map(n, null)), planned: true };
  }
}

export async function createCategory(input: { name: string; slug: string; parentId: number | null }): Promise<CategoryRef> {
  return (await api.post('/categories', input)).data;
}

/** PLANNED */
export async function updateCategory(id: number, input: { name: string; slug: string; parentId: number | null; active: boolean }) {
  return (await api.put(`/categories/${id}`, input)).data;
}

/** PLANNED: puts the shelf under `parentId`, just before `beforeId` (null: last). */
export async function moveCategory(id: number, parentId: number | null, beforeId: number | null) {
  return (await api.put(`/categories/${id}/position`, { parentId, beforeId })).data;
}

/** PLANNED: refused (409 category_not_empty) while products or sub-categories are in it. */
export async function deleteCategory(id: number) {
  await api.delete(`/categories/${id}`);
}

// ---- everything below is PLANNED ----

export interface Overview {
  salesToday: number;
  ordersToday: number;
  sales7d: number;
  orders7d: number;
  failedPaymentsToday: number;
  paymentsTriedToday: number;
  toPack: number;
  oldestToPack: string | null;
  refundsUnconfirmed: number;
  /** new and reported reviews nobody has looked at yet */
  reviewsToCheck: number;
  reviewsReported: number;
  lowStock: number;
  staffWithout2fa: number;
  latestOrders: AdminOrderRow[];
  recent: AuditEntry[];
}

export async function fetchOverview(): Promise<Overview> {
  return (await api.get('/staff/overview')).data;
}

export interface AdminOrderRow extends Omit<OrderSummary, 'preview'> {
  customerName: string;
  guest: boolean;
  shippingMethod: string;
  /** how long it has waited in its current status */
  statusChangedAt: string;
}

export interface AdminOrderParams {
  /** an OrderStatus, or REFUND_PENDING: cancelled, but Stripe hasn't confirmed the refund for over an hour */
  status?: string;
  /** order number only: names and emails are encrypted, and encrypted columns are never searched */
  number?: string;
  sort?: 'oldest' | 'newest' | 'total_desc';
  /** placed on or after this day, yyyy-mm-dd */
  from?: string;
  page?: number;
  size?: number;
}

export async function fetchAdminOrders(params: AdminOrderParams): Promise<Page<AdminOrderRow> & { counts: Record<string, number> }> {
  return (await api.get('/staff/orders', { params })).data;
}

/** The list as a CSV file: the filtered list, or only the ticked orders when ids are given. */
export async function exportOrders(params: AdminOrderParams & { ids?: string[] }): Promise<Blob> {
  return (await api.get('/staff/orders/export', { params, responseType: 'blob', paramsSerializer: { indexes: null } })).data;
}

export interface AdminOrderDetail extends OrderDetail {
  customerName: string;
  guest: boolean;
  /** newest first: each change with who made it (SHOPPER, STRIPE, TIMER or a staff member's name); `to` is null on a team note */
  timeline: { at: string; from: OrderStatus | null; to: OrderStatus | null; actor: string; note: string | null }[];
  /** money already refunded, and per product how many were */
  refunded: number;
  refundedLines: Record<string, number>;
  shippingRefunded: boolean;
  /** the moves a person may make from here; the server's state machine decides */
  next: ('PROCESSING' | 'SHIPPED' | 'DELIVERED' | 'CANCELLED')[];
}

export async function fetchAdminOrder(id: string): Promise<AdminOrderDetail> {
  return (await api.get(`/staff/orders/${encodeURIComponent(id)}`)).data;
}

export async function moveOrder(id: string, to: string, extra: { carrier?: string; trackingNumber?: string; note?: string } = {}) {
  return (await api.post(`/staff/orders/${encodeURIComponent(id)}/status`, { to, ...extra })).data as AdminOrderDetail;
}

/** A note for the team on the order's history; the customer never sees it. */
export async function addOrderNote(id: string, note: string): Promise<AdminOrderDetail> {
  return (await api.post(`/staff/orders/${encodeURIComponent(id)}/notes`, { note })).data;
}

/** Bulk "Start packing" for the ticked rows. */
export async function startPacking(ids: string[]): Promise<{ moved: number; skipped: number }> {
  return (await api.post('/staff/orders/start-packing', { ids })).data;
}

/** A partial refund by line (stock goes back per line), plus shipping when asked. */
export async function refundOrder(id: string, lines: { productId: string; quantity: number }[], shipping: boolean, reason: string) {
  return (await api.post(`/staff/orders/${encodeURIComponent(id)}/refunds`, { lines, shipping, reason })).data as AdminOrderDetail;
}

export interface DeliveryOption {
  id: string;
  name: string;
  price: number;
  /** "15:00": orders placed before it on a working day leave the same day */
  orderBy: string;
  daysMin: number;
  daysMax: number;
  /** ISO codes, e.g. EE */
  countries: string[];
  /** shown at checkout; off hides it without deleting it */
  active: boolean;
  /** free once the order reaches the free-shipping limit */
  freeOverLimit: boolean;
}

export interface DeliverySettings {
  /** the one limit every page quotes ("Free delivery from €100") */
  freeShipping: { from: number; on: boolean };
  /** in the order shoppers see them */
  options: DeliveryOption[];
}

export async function fetchDelivery(): Promise<DeliverySettings> {
  return (await api.get('/staff/delivery')).data;
}

export async function saveFreeShipping(free: DeliverySettings['freeShipping']): Promise<DeliverySettings> {
  return (await api.put('/staff/delivery/free-shipping', free)).data;
}

export async function saveDeliveryOption(option: Omit<DeliveryOption, 'id'> & { id?: string }): Promise<DeliverySettings> {
  return option.id
    ? (await api.put(`/staff/delivery/options/${encodeURIComponent(option.id)}`, option)).data
    : (await api.post('/staff/delivery/options', option)).data;
}

export async function reorderDeliveryOptions(ids: string[]): Promise<DeliverySettings> {
  return (await api.put('/staff/delivery/order', { ids })).data;
}

export interface StaffUser {
  id: string;
  fullName: string;
  email: string;
  role: Role;
  twoFactorEnabled: boolean;
  createdAt: string;
  lastSignInAt: string | null;
  orderCount: number;
}

export interface UserParams {
  /** an email, or the start of one */
  q?: string;
  group?: 'all' | 'customers' | 'staff';
  twoFactor?: 'off';
  page?: number;
  size?: number;
}

/** counts: all, customers, staff, and admins (so the page knows when one is the last) */
export async function fetchUsers(params: UserParams): Promise<Page<StaffUser> & { counts: Record<'all' | 'customers' | 'staff' | 'admins', number> }> {
  return (await api.get('/staff/users', { params })).data;
}

/** Needs the changer's own 2FA code. Nobody changes their own role; the last admin can't be demoted. */
export async function changeRole(userId: string, role: Role, code: string): Promise<StaffUser> {
  return (await api.put(`/staff/users/${encodeURIComponent(userId)}/role`, { role, code })).data;
}

export type ModerationState = 'todo' | 'reported' | 'live' | 'hidden';

export interface ModerationReview extends Review {
  productId: string;
  productName: string;
  productImageUrl: string | null;
  productImageFraming: ImageFraming | null;
  /** the order that makes it a verified purchase */
  orderNumber: string | null;
  /** what the automatic check noticed (a link, a phone number); `serious` ones are shown in red */
  flags: { text: string; serious: boolean }[];
  /** shoppers' reports, newest first */
  reports: { reason: string; at: string }[];
  state: 'NEW' | 'REPORTED' | 'LIVE' | 'HIDDEN';
  hiddenReason: string | null;
}

export interface ModerationParams {
  /** todo: new and reported ones nobody has looked at */
  state: ModerationState;
  rating?: 'low' | 'mid' | 'high';
  sort?: 'oldest' | 'newest';
  page?: number;
  size?: number;
}

export async function fetchModeration(params: ModerationParams): Promise<Page<ModerationReview> & { counts: Record<ModerationState, number> }> {
  return (await api.get('/staff/reviews', { params })).data;
}

/** Keep clears it from the queue (and its reports); Hide takes it off the site with a reason, and can be undone. */
export async function moderateReviews(ids: string[], action: 'keep' | 'hide' | 'unhide', reason?: string): Promise<{ done: number }> {
  return (await api.post(`/staff/reviews/${action}`, { ids, reason })).data;
}

export interface AuditEntry {
  id: string;
  at: string;
  /** null when nobody was signed in, like a failed sign-in */
  actor: string | null;
  actorRole: StaffRole | null;
  /** "Marked {target} shipped": the target, linked when it has a page, goes where {target} is */
  action: string;
  target: string | null;
  targetLink: string | null;
  /** before and after, when the change has them */
  change: { from: string | null; to: string } | null;
  /** anything else worth a line: "Firefox on Linux", "€41.90 to Visa ending 1881" */
  detail: string | null;
  /** a refusal or a failure; red when serious */
  flag: { text: string; serious: boolean } | null;
  /** masked by the server to the first two parts, like 84.50.•.• */
  ip: string | null;
}

export interface AuditParams {
  actor?: string;
  kind?: 'orders' | 'products' | 'reviews' | 'access';
  since?: 'today' | '7d' | '30d';
  page?: number;
  size?: number;
}

/** actors: the staff who appear in the log, for the Person filter */
export async function fetchAudit(params: AuditParams): Promise<Page<AuditEntry> & { actors: { id: string; name: string }[] }> {
  return (await api.get('/staff/audit', { params })).data;
}

export async function exportAudit(params: AuditParams): Promise<Blob> {
  return (await api.get('/staff/audit/export', { params, responseType: 'blob' })).data;
}

/** A row the check refused, with the column and value at fault. */
export interface ImportIssue {
  line: number;
  column: string;
  problem: string;
  value: string | null;
}

/** One value an update will change. */
export interface ImportChange {
  sku: string;
  name: string;
  column: string;
  from: string | null;
  to: string;
}

export interface ImportJob {
  id: string;
  fileName: string;
  format: 'json' | 'csv';
  sizeBytes: number;
  uploadedAt: string;
  rows: number;
  status: 'CHECKING' | 'CHECKED' | 'IMPORTING' | 'DONE' | 'FAILED';
  /** a row whose SKU is already in the shop updates that product, or is skipped */
  onMatch: 'update' | 'skip';
  counts: { new: number; update: number; error: number; photos: number; done: number };
  errors: ImportIssue[];
  changes: ImportChange[];
  /** why a FAILED job stopped */
  failure: string | null;
}

/** Uploads the file and starts the check (a dry run) as a queued job; nothing changes until confirmImport. Matches on SKU. */
export async function startImport(file: File): Promise<ImportJob> {
  const body = new FormData();
  body.append('file', file);
  return (await api.post('/staff/imports', body)).data;
}

export async function fetchImport(id: string): Promise<ImportJob> {
  return (await api.get(`/staff/imports/${encodeURIComponent(id)}`)).data;
}

export async function confirmImport(id: string, onMatch: ImportJob['onMatch']): Promise<ImportJob> {
  return (await api.post(`/staff/imports/${encodeURIComponent(id)}/confirm`, { onMatch })).data;
}

/** The refused rows as they were in the file, to fix and upload again. */
export async function fetchImportErrors(id: string): Promise<Blob> {
  return (await api.get(`/staff/imports/${encodeURIComponent(id)}/errors`, { responseType: 'blob' })).data;
}

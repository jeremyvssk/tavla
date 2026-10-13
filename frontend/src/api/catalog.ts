// Typed calls to the public catalog endpoints, plus the search-parameter shape shared with the URL.
import type { ImageFraming } from '../lib/framing';
import { api } from './client';

export interface ProductSummary {
  id: string;
  name: string;
  price: number;
  averageRating: number | null;
  reviewCount: number;
  inStock: boolean;
  brandName: string | null;
  categorySlug: string;
  primaryImageUrl: string | null;
  primaryImageFraming: ImageFraming | null;
  /** PLANNED: the shelf's key specs for the list view, e.g. ["King 110 mm", "Hornbeam pieces"]; not sent yet */
  specs?: string[];
}

export interface FacetValue {
  slug: string;
  name: string;
  count: number;
}

export interface PriceBucket {
  min: number;
  max: number | null;
  count: number;
}

export interface RatingBucket {
  minRating: number;
  count: number;
}

export interface ProductSearchResponse {
  items: ProductSummary[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
  sort: string;
  approximate: boolean;
  facets: {
    categories: FacetValue[];
    brands: FacetValue[];
    prices: PriceBucket[];
    ratings: RatingBucket[];
  };
}

export interface CategoryRef {
  id: number;
  name: string;
  slug: string;
}

export interface CategoryNode extends CategoryRef {
  children: CategoryNode[];
}

export interface ProductDetail {
  id: string;
  name: string;
  /** the shop's own article code; bulk uploads match on it */
  sku: string | null;
  description: string | null;
  price: number;
  stockQuantity: number;
  inStock: boolean;
  /** false: hidden from the shop (only staff ever load a hidden product) */
  active: boolean;
  /** taken out of the shop for good because past orders point at it */
  archived: boolean;
  category: CategoryRef;
  breadcrumb: CategoryRef[];
  brand: { id: number; name: string; slug: string; logoUrl: string | null } | null;
  images: { id: number; url: string; altText: string | null; primary: boolean; framing: ImageFraming | null }[];
  attributes: Record<string, unknown> | null;
  measurements: {
    weightKg: number | null;
    widthCm: number | null;
    heightCm: number | null;
    depthCm: number | null;
    weightLbs: number | null;
    widthIn: number | null;
    heightIn: number | null;
    depthIn: number | null;
  };
  averageRating: number | null;
  reviewCount: number;
  variants: ProductVariant[];
}

/** One colour, wood or size of the product; `options` is e.g. { Colour: 'Blue' } or { Size: '19"', Colour: 'Red' }. */
export interface ProductVariant {
  id: string;
  options: Record<string, string>;
  price: number;
  inStock: boolean;
  imageUrl: string | null;
}

export interface Review {
  id: string;
  rating: number;
  title: string;
  body: string;
  verifiedPurchase: boolean;
  createdAt: string;
  /** "Toomas R.": first name and initial, never the email; null for a deleted account */
  authorName: string | null;
  helpfulCount: number;
  /** the signed-in shopper's own vote; there is no "not helpful" */
  myVote: 'helpful' | null;
  /** false only on the shopper's own hidden review, which only its author is sent */
  published: boolean;
}

export type ReviewSort = 'helpful' | 'newest' | 'highest' | 'lowest';

/** GET /products/{id}/reviews/summary. The spread behind the average, and whether this shopper may review. */
export interface ReviewSummary {
  average: number | null;
  count: number;
  /** reviews per star, index 0 = one star */
  distribution: [number, number, number, number, number];
  /**
   * null for guests. purchasedAt: when their delivered order with it was placed (null: they can't review it yet).
   * review and hiddenReason: their own review, hidden ones too, and why staff hid it.
   */
  me: { purchasedAt: string | null; reviewed: boolean; review: Review | null; hiddenReason: string | null } | null;
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

/** In the order the sort menu lists them. Best match is only offered with a search. */
export const SORTS = [
  { value: 'relevance', label: 'Best match' },
  { value: 'rating', label: 'Top rated' },
  { value: 'price_asc', label: 'Price, low to high' },
  { value: 'price_desc', label: 'Price, high to low' },
  { value: 'newest', label: 'Newest' },
] as const;

/** Mirrors GET /products query parameters; the catalog page keeps these in the URL. */
export interface SearchParams {
  q?: string;
  category?: string;
  brand?: string[];
  minPrice?: string;
  maxPrice?: string;
  minRating?: string;
  sort?: string;
  page?: string;
  size?: string;
}

export async function searchProducts(params: SearchParams): Promise<ProductSearchResponse> {
  // brand=a&brand=b, which is how Spring binds a List, rather than axios's default brand[]=a.
  return (await api.get('/products', { params, paramsSerializer: { indexes: null } })).data;
}

export async function fetchProduct(id: string): Promise<ProductDetail> {
  return (await api.get(`/products/${encodeURIComponent(id)}`)).data;
}

export async function fetchCategories(): Promise<CategoryNode[]> {
  return (await api.get('/categories')).data;
}

export async function fetchSuggestions(q: string): Promise<{ id: string; name: string }[]> {
  return (await api.get('/search/suggestions', { params: { q } })).data;
}

export async function fetchReviews(productId: string, page: number, sort: ReviewSort = 'helpful', rating?: number): Promise<Page<Review>> {
  return (await api.get(`/products/${encodeURIComponent(productId)}/reviews`, { params: { page, size: 5, sort, rating } })).data;
}

export async function fetchReviewSummary(productId: string): Promise<ReviewSummary> {
  return (await api.get(`/products/${encodeURIComponent(productId)}/reviews/summary`)).data;
}

/** PUT marks it helpful; DELETE takes the vote back. One vote per signed-in shopper, never on their own review. */
export async function voteReview(productId: string, reviewId: string, vote: 'helpful' | null): Promise<Review> {
  const url = `/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}/vote`;
  return (vote === null ? await api.delete(url) : await api.put(url, { vote })).data;
}

/** Puts the review in the staff's moderation queue; it stays shown until staff decide. */
export async function reportReview(productId: string, reviewId: string, reason: string) {
  await api.post(`/products/${encodeURIComponent(productId)}/reviews/${encodeURIComponent(reviewId)}/report`, { reason });
}

export async function postReview(productId: string, review: { rating: number; title: string; body: string }) {
  return (await api.post<Review>(`/products/${encodeURIComponent(productId)}/reviews`, review)).data;
}

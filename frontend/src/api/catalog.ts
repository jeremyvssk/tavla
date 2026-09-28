// Typed calls to the public catalog endpoints, plus the search-parameter shape shared with the URL.
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
  description: string | null;
  price: number;
  stockQuantity: number;
  inStock: boolean;
  category: CategoryRef;
  breadcrumb: CategoryRef[];
  brand: { id: number; name: string; slug: string; logoUrl: string | null } | null;
  images: { id: number; url: string; altText: string | null; primary: boolean }[];
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
}

export interface Page<T> {
  items: T[];
  page: number;
  size: number;
  totalItems: number;
  totalPages: number;
}

export const SORTS = [
  { value: 'featured', label: 'Featured' },
  { value: 'relevance', label: 'Relevance' },
  { value: 'newest', label: 'Newest' },
  { value: 'price_asc', label: 'Price, low to high' },
  { value: 'price_desc', label: 'Price, high to low' },
  { value: 'rating', label: 'Rating' },
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

export async function fetchReviews(productId: string, page: number): Promise<Page<Review>> {
  return (await api.get(`/products/${encodeURIComponent(productId)}/reviews`, { params: { page, size: 5 } })).data;
}

export async function postReview(productId: string, review: { rating: number; title: string; body: string }) {
  return (await api.post<Review>(`/products/${encodeURIComponent(productId)}/reviews`, review)).data;
}

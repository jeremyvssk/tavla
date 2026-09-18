// One product tile in a listing: image or typographic placeholder, name, brand, rating and price.
import { Link } from 'react-router-dom';
import type { ProductSummary } from '../api/catalog';
import { formatPrice } from '../lib/format';
import Stars from './Stars';

export default function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <article className="card">
      <Link to={`/catalog/${product.id}`} className="card__link">
        <div className="card__media">
          {product.primaryImageUrl ? (
            <img src={product.primaryImageUrl} alt="" loading="lazy" />
          ) : (
            // Seeded products have no photos yet; the category stands in for one.
            <span className="card__placeholder" aria-hidden="true">
              {product.categorySlug.replace(/-/g, ' ')}
            </span>
          )}
        </div>
        <h3 className="card__name">{product.name}</h3>
      </Link>
      <p className="card__meta">
        {product.brandName ?? 'Unbranded'}
        {!product.inStock && <span className="tag">Out of stock</span>}
      </p>
      <div className="card__foot">
        <Stars rating={product.averageRating} count={product.reviewCount} />
        <span className="card__price">{formatPrice(product.price)}</span>
      </div>
    </article>
  );
}

// One product card in a listing or a row: photo on the white card, name, brand, price and rating.
import { Link } from 'react-router-dom';
import type { ProductSummary } from '../api/catalog';
import { formatPrice } from '../lib/format';
import { Star } from './Icon';

export default function ProductCard({ product }: { product: ProductSummary }) {
  return (
    <article className="pc">
      <div className="ph">
        {!product.inStock && <span className="tag tag--out">Out of stock</span>}
        {product.primaryImageUrl ? (
          <img src={product.primaryImageUrl} alt="" width={600} height={600} loading="lazy" decoding="async" draggable={false} />
        ) : (
          <span className="ph-none" aria-hidden="true">{product.categorySlug.replace(/-/g, ' ')}</span>
        )}
      </div>
      {/* the link's ::after covers the whole card, so the photo is a click target too */}
      {/* long names stop at two lines, so prices line up across a row; the full name is the tooltip */}
      <h3 title={product.name}><Link to={`/catalog/${product.id}`} draggable={false}>{product.name}</Link></h3>
      <p className="br">{product.brandName ?? 'Tavla'}</p>
      <div className="ft">
        <span className="pz">{formatPrice(product.price)}</span>
        {product.averageRating !== null && product.reviewCount > 0 && (
          <span className="rt" aria-label={`Rated ${product.averageRating.toFixed(1)} out of 5 from ${product.reviewCount} reviews`}>
            <Star />
            <span aria-hidden="true">{product.averageRating.toFixed(1)} ({product.reviewCount})</span>
          </span>
        )}
      </div>
    </article>
  );
}

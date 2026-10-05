// One product card in a listing or a row: the photo on its own tile, then price and rating, then the name.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ProductSummary } from '../api/catalog';
import { formatPrice } from '../lib/format';
import { Star } from './Icon';
import Photo from './Photo';

export default function ProductCard({ product }: { product: ProductSummary }) {
  // the photo is hotlinked from the supplier; if it is gone, the card shows the no-photo label instead
  const [broken, setBroken] = useState(false);
  return (
    <article className="pc">
      <div className="ph">
        {!product.inStock && <span className="tag tag--out">Out of stock</span>}
        {product.primaryImageUrl && !broken ? (
          <Photo src={product.primaryImageUrl} framing={product.primaryImageFraming} pad={0.08}
            loading="lazy" decoding="async" draggable={false} onError={() => setBroken(true)} />
        ) : (
          <span className="ph-none" aria-hidden="true">{product.categorySlug.replace(/-/g, ' ')}</span>
        )}
      </div>
      <div className="ft">
        <span className="pz">{formatPrice(product.price)}</span>
        {product.averageRating !== null && product.reviewCount > 0 && (
          <span className="rt" aria-label={`Rated ${product.averageRating.toFixed(1)} out of 5 from ${product.reviewCount} reviews`}>
            <Star />
            <span aria-hidden="true">{product.averageRating.toFixed(1)} ({product.reviewCount})</span>
          </span>
        )}
      </div>
      {/* the link's ::after covers the whole card, so the photo is a click target too */}
      {/* long names stop at two lines, so the rows line up; the full name is the tooltip */}
      <h3 title={product.name}><Link to={`/catalog/${product.id}`} draggable={false}>{product.name}</Link></h3>
    </article>
  );
}

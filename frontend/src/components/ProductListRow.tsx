// One product in the list view: photo, then name, maker and shelf, rating and key specs, then price, stock and Add to cart.
import { useState } from 'react';
import { Link } from 'react-router-dom';
import type { ProductSummary } from '../api/catalog';
import { stockConflictOf, stockMessage } from '../api/cart';
import { toApiError } from '../api/errors';
import { useCartActions } from '../hooks/useCart';
import { formatPrice } from '../lib/format';
import { useAppDispatch } from '../store';
import { opened } from '../store/cartSlice';
import Photo from './Photo';
import Stars from './Stars';

export default function ProductListRow({ product: p, shelf }: { product: ProductSummary; shelf: string | null }) {
  const { add } = useCartActions();
  const dispatch = useAppDispatch();
  const [problem, setProblem] = useState<string | null>(null);
  const [broken, setBroken] = useState(false);
  const to = `/catalog/${p.id}`;

  function addOne() {
    setProblem(null);
    add.mutate({ productId: p.id }, {
      onSuccess: () => dispatch(opened(p.id)),
      onError: (err) => {
        const conflict = stockConflictOf(err);
        setProblem(conflict ? stockMessage(conflict) : toApiError(err).message);
      },
    });
  }

  return (
    <li>
      <Link className="ph" to={to} tabIndex={-1} aria-hidden="true">
        {p.primaryImageUrl && !broken
          ? <Photo src={p.primaryImageUrl} framing={p.primaryImageFraming} pad={0.08} loading="lazy" onError={() => setBroken(true)} />
          : <span className="ph-none">{p.categorySlug.replace(/-/g, ' ')}</span>}
      </Link>
      <div>
        <h3><Link to={to}>{p.name}</Link></h3>
        <p className="by">{[p.brandName, shelf].filter(Boolean).join(' · ')}</p>
        {p.reviewCount > 0 && <Stars rating={p.averageRating} count={p.reviewCount} />}
        {/* PLANNED: each shelf's key specs (king height, woods, board size); shown once the search API sends them */}
        {p.specs && p.specs.length > 0 && <ul className="spec">{p.specs.map((s) => <li key={s}>{s}</li>)}</ul>}
      </div>
      <div className="buy2">
        <span className="pz">{formatPrice(p.price)}</span>
        {p.inStock ? <span className="stock">In stock</span> : <span className="stock out">Out of stock</span>}
        <button className="btn" type="button" disabled={!p.inStock || add.isPending} onClick={addOne}>
          {p.inStock ? <>Add to cart<span className="vh">: {p.name}</span></> : 'Sold out'}
        </button>
        {problem && <p className="err" role="alert">{problem}</p>}
      </div>
    </li>
  );
}

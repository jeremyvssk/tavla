// Landing page: hero, the category tree as tiles, and a strip of top-rated products.
import { useQuery } from '@tanstack/react-query';
import { Link } from 'react-router-dom';
import { fetchCategories, searchProducts } from '../api/catalog';
import ProductCard from '../components/ProductCard';

export default function HomePage() {
  const { data: categories = [] } = useQuery({ queryKey: ['categories'], queryFn: fetchCategories });
  const { data: topRated } = useQuery({
    queryKey: ['products', 'sort=rating&size=4'],
    queryFn: () => searchProducts({ sort: 'rating', size: '4' }),
  });
  const tiles = categories.flatMap((root) => (root.children.length ? root.children : [root]));

  return (
    <div className="home">
      <section className="hero">
        {/* Spec-sheet annotations; the dark theme hides them. */}
        <div className="hero__notes" aria-hidden="true">
          <p>NOTE: DEMO CATALOG<br />SEEDED FROM R__SEED_CATALOG</p>
          <p>VERSION: P1<br />SEARCH: POSTGRES FTS + TRIGRAM</p>
        </div>
        <p className="label">Chess &amp; strategy games</p>
        <h1 className="hero__title">
          Heavy pieces.
          <br />
          Honest boards.
        </h1>
        <p className="lede">
          Weighted Staunton sets, solid wood boards, clocks, Go and backgammon, searchable by wood, style and size.
        </p>
        <Link to="/catalog" className="cta">
          <span className="cta__ring" aria-hidden="true">↗</span>
          Browse the catalog
        </Link>
      </section>

      {tiles.length > 0 && (
        <section aria-labelledby="categories-title">
          <h2 id="categories-title" className="section-title">Categories</h2>
          <ul className="tiles">
            {tiles.map((c) => (
              <li key={c.id}>
                <Link to={`/catalog?category=${c.slug}`} className="tile">
                  <span className="tile__name">{c.name}</span>
                </Link>
              </li>
            ))}
          </ul>
        </section>
      )}

      {topRated && topRated.items.length > 0 && (
        <section aria-labelledby="top-title">
          <div className="section-head">
            <h2 id="top-title" className="section-title">Top rated</h2>
            <Link to="/catalog?sort=rating">View all +</Link>
          </div>
          <div className="grid grid--row">
            {topRated.items.map((p) => <ProductCard key={p.id} product={p} />)}
          </div>
        </section>
      )}
    </div>
  );
}
